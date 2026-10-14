% Motor de regras de produção para a base de conhecimento de condução: encadeamento para a
% frente com uma agenda por precedência, nova avaliação guiada por um índice de
% dependências, refração e explicações devolvidas como dados.
%
% Formato das regras (driving_rules.pl):
%
%   rule(Id, Precedence, Conditions, Actions).
%
%   Precedence  um número; entre as regras prontas a disparar, dispara primeiro a maior.
%   Conditions  uma lista de condições que têm de se verificar todas:
%                 Padrão       um facto da memória de trabalho, unificado com o padrão
%                 not(C)       C (um padrão ou um teste) não se verifica
%                 test(Goal)   um objetivo Prolog: aritmética, ou um predicado auxiliar que lê
%                              a memória de trabalho através de known/1; as ligações das
%                              variáveis passam para o resto da regra
%   Actions     uma lista de assert(Fact).
%
% Ciclo: run_rules/0 avalia todas as regras contra a memória de trabalho e preenche a
% agenda. Depois, até a agenda ficar vazia, dispara a melhor ativação: a de maior
% precedência, depois a da regra declarada primeiro, depois a da correspondência encontrada
% primeiro. Uma instanciação que já disparou não volta a disparar no mesmo ciclo (refração).
%
% Disparar uma regra pode acrescentar factos. Cada tipo de facto (Nome/Aridade) tem a lista
% das regras que o leem, construída uma única vez por análise estática: os padrões da
% regra, os padrões negados e todas as chamadas a known/1 alcançáveis a partir dos seus
% testes através dos predicados auxiliares. Só essas regras são avaliadas de novo, pelo que
% as suas ativações aparecem, mudam ou são canceladas assim que um facto novo lhes diz
% respeito. Dentro de um ciclo, nunca se retiram factos.

:- dynamic wm_fact/2, wm_support/3, wm_next_id/1,
           activation/5, fired/2,
           rule_reads/2, rule_rank/2, rule_precedence/2, index_built/0.

% Guard against a rule set that keeps producing new instantiations.
max_firings_per_cycle(5000).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Memória de trabalho

reset_working_memory :-
    retractall(wm_fact(_, _)),
    retractall(wm_support(_, _, _)),
    retractall(activation(_, _, _, _, _)),
    retractall(fired(_, _)),
    retractall(wm_next_id(_)),
    assertz(wm_next_id(1)).

% The query used by rule conditions and helper predicates.
known(Fact) :-
    wm_fact(_, Fact).

% A perceived fact: no rule supports it. Facts are a set, so a duplicate is ignored.
add_fact(Fact) :-
    store_fact(Fact, perceived, _).

% store_fact(+Fact, +Support, -New): New is true when the fact was not yet known.
store_fact(Fact, _, false) :-
    wm_fact(_, Known),
    Known == Fact, !.
store_fact(Fact, Support, true) :-
    retract(wm_next_id(Id)),
    Next is Id + 1,
    assertz(wm_next_id(Next)),
    assertz(wm_fact(Id, Fact)),
    (   Support = derived(Rule, Evidence)
    ->  assertz(wm_support(Id, Rule, Evidence))
    ;   true
    ).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Índice de dependências (construído uma vez, a partir das regras e auxiliares carregados)

ensure_index :-
    index_built, !.
ensure_index :-
    retractall(rule_reads(_, _)),
    retractall(rule_rank(_, _)),
    retractall(rule_precedence(_, _)),
    findall(Id, rule(Id, _, _, _), Ids),
    check_unique_ids(Ids),
    forall(nth1(Rank, Ids, Id), index_rule(Id, Rank)),
    assertz(index_built).

check_unique_ids(Ids) :-
    msort(Ids, Sorted),
    (   append(_, [Id, Id|_], Sorted)
    ->  throw(error(duplicate_rule_id(Id), _))
    ;   true
    ).

index_rule(Id, Rank) :-
    rule(Id, Precedence, Conditions, _),
    assertz(rule_rank(Id, Rank)),
    assertz(rule_precedence(Id, Precedence)),
    foldl(condition_reads, Conditions, [], Keys0),
    sort(Keys0, Keys),
    forall(member(Key, Keys), assertz(rule_reads(Key, Id))).

condition_reads(not(C), Keys0, Keys) :- !,
    condition_reads(C, Keys0, Keys).
condition_reads(test(Goal), Keys0, Keys) :- !,
    goal_reads(Goal, [], _, Keys0, Keys).
condition_reads(Pattern, Keys, [Name/Arity|Keys]) :-
    functor(Pattern, Name, Arity).

% goal_reads(+Term, +Seen0, -Seen, +Keys0, -Keys): the fact types a goal may read. It walks
% every subterm (so findall/3, aggregate_all/3, \+ and if-then-else need no special case)
% and the clauses of every user predicate it meets, once each. Over-approximating only
% costs an extra re-match.
goal_reads(Term, Seen, Seen, Keys, Keys) :-
    \+ compound(Term), \+ atom(Term), !.
goal_reads(known(Fact), Seen, Seen, Keys, [Name/Arity|Keys]) :-
    nonvar(Fact), !,
    functor(Fact, Name, Arity).
goal_reads(Term, Seen0, Seen, Keys0, Keys) :-
    functor(Term, Name, Arity),
    (   memberchk(Name/Arity, Seen0)
    ->  Seen1 = Seen0, Keys1 = Keys0
    ;   helper_predicate(Name/Arity)
    ->  functor(Head, Name, Arity),
        findall(Body, clause(Head, Body), Bodies),
        foldl2(goal_reads, Bodies, [Name/Arity|Seen0], Seen1, Keys0, Keys1)
    ;   Seen1 = Seen0, Keys1 = Keys0
    ),
    (   compound(Term)
    ->  Term =.. [_|Args],
        foldl2(goal_reads, Args, Seen1, Seen, Keys1, Keys)
    ;   Seen = Seen1, Keys = Keys1
    ).

% A predicate of this program, as opposed to a built-in or library one.
helper_predicate(Name/Arity) :-
    functor(Head, Name, Arity),
    predicate_property(user:Head, defined),
    \+ predicate_property(user:Head, built_in),
    \+ predicate_property(user:Head, imported_from(_)),
    \+ predicate_property(user:Head, foreign).

foldl2(_, [], S, S, K, K).
foldl2(G, [X|Xs], S0, S, K0, K) :-
    call(G, X, S0, S1, K0, K1),
    foldl2(G, Xs, S1, S, K1, K).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Correspondência das condições

% An instantiation: the conditions as they matched, the evidence (fact ids and the negated
% patterns that held) and the actions to perform.
match(Id, inst(Conditions, Evidence, Actions)) :-
    rule(Id, _, Conditions, Actions),
    satisfy(Conditions, Evidence).

satisfy([], []).
satisfy([Condition|Conditions], Evidence) :-
    holds(Condition, Proof),
    satisfy(Conditions, Evidence0),
    (   Proof == none -> Evidence = Evidence0 ; Evidence = [Proof|Evidence0] ).

holds(not(test(Goal)), none) :- !,
    \+ call(Goal).
holds(not(Pattern), not(Pattern)) :- !,
    \+ wm_fact(_, Pattern).
holds(test(Goal), none) :- !,
    call(Goal).
holds(Pattern, Id) :-
    wm_fact(Id, Pattern).

% Replaces the rule's activations with its current matches, leaving out what already fired.
rematch(Id) :-
    retractall(activation(Id, _, _, _, _)),
    rule_precedence(Id, Precedence),
    rule_rank(Id, Rank),
    findall(Inst, match(Id, Inst), Insts),
    forall(( nth1(Order, Insts, Inst),
             \+ already_fired(Id, Inst) ),
           assertz(activation(Id, Precedence, Rank, Order, Inst))).

already_fired(Id, inst(Conditions, _, _)) :-
    fired(Id, Done),
    Done =@= Conditions, !.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Agenda e disparo

run_rules :-
    ensure_index,
    forall(rule_rank(Id, _), rematch(Id)),
    max_firings_per_cycle(Max),
    fire_loop(0, Max).

fire_loop(Count, Max) :-
    (   best_activation(Id, Inst)
    ->  (   Count >= Max
        ->  throw(error(rule_firing_limit(Max, Id), _))
        ;   true
        ),
        fire(Id, Inst),
        Next is Count + 1,
        fire_loop(Next, Max)
    ;   true
    ).

best_activation(Id, Inst) :-
    findall(key(Neg, Rank, Order)-Id,
            ( activation(Id, Precedence, Rank, Order, _), Neg is -Precedence ),
            Keys),
    Keys \== [],
    msort(Keys, [key(_, _, Order)-Id|_]),
    activation(Id, _, _, Order, Inst), !.

fire(Id, Inst) :-
    Inst = inst(Conditions, Evidence, Actions),
    retract(activation(Id, _, _, _, Inst)), !,
    assertz(fired(Id, Conditions)),
    foldl(perform(Id, Evidence), Actions, [], Changed0),
    sort(Changed0, Changed),
    findall(Reader, ( member(Key, Changed), rule_reads(Key, Reader) ), Readers0),
    sort(Readers0, Readers),
    maplist(rematch, Readers).

perform(Id, Evidence, assert(Fact), Changed0, Changed) :-
    store_fact(Fact, derived(Id, Evidence), New),
    (   New == true
    ->  functor(Fact, Name, Arity),
        Changed = [Name/Arity|Changed0]
    ;   Changed = Changed0
    ).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Explicações

% explain(+FactId, -Tree): how a fact came to be known.
%   perceived(Fact)                  loaded from perception
%   derived(Fact, Rule, Because)     concluded by Rule; Because explains each piece of evidence,
%                                    absent(Pattern) for a negated pattern
explain(Id, perceived(Fact)) :-
    wm_fact(Id, Fact),
    \+ wm_support(Id, _, _), !.
explain(Id, derived(Fact, Rule, Because)) :-
    wm_fact(Id, Fact),
    wm_support(Id, Rule, Evidence),
    maplist(explain_evidence, Evidence, Because).

explain_evidence(not(Pattern), absent(Pattern)) :- !.
explain_evidence(Id, Tree) :-
    explain(Id, Tree).

% why_not(+RuleId, -Blocking): why a rule has no match now. Among all ways to match the
% conditions from the left, it takes the one that gets furthest and reports the condition
% where it stopped, followed by the later conditions that do not hold either, all with the
% bindings that partial match produced.
why_not(Id, []) :-
    match(Id, _), !.
why_not(Id, Blocking) :-
    rule(Id, _, Conditions, _),
    findall(Depth-Rest, partial_match(Conditions, 0, Depth, Rest), Partials),
    msort(Partials, Sorted),
    last(Sorted, _-[Stop|Later]),
    include(fails_leniently, Later, AlsoFailing),
    Blocking = [Stop|AlsoFailing].

% Every prefix that holds, with the conditions left after it.
partial_match([], Depth, Depth, []).
partial_match([Condition|Conditions], Depth0, Depth, Rest) :-
    (   holds(Condition, _),
        Depth1 is Depth0 + 1,
        partial_match(Conditions, Depth1, Depth, Rest)
    ;   Depth = Depth0,
        Rest = [Condition|Conditions]
    ).

% A condition with variables the failed match never bound may raise an error: it counts as
% not holding.
fails_leniently(Condition) :-
    \+ catch(holds(Condition, _), _, fail).
