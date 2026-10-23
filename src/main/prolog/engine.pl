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
%                 prove(Goal)  um objetivo provado por encadeamento inverso (prove_goal/2):
%                              desce pelas cláusulas dos predicados auxiliares até aos factos
%                              lidos com known/1, que ficam na evidência da regra como os de
%                              um padrão; os auxiliares só podem usar conjunções, known/1,
%                              outros auxiliares e predicados do sistema (sem cortes nem ;)
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
%
% Meta-conhecimento: conhecimento sobre as próprias regras, não sobre a condução, usado
% para controlar e acelerar a inferência. Está assinalado com "Meta-conhecimento" e é de
% dois tipos:
%   - índice de dependências (rule_reads/2): que tipos de facto cada regra lê, derivado
%     automaticamente das regras; limita a nova avaliação às regras afetadas (rapidez);
%   - conhecimento de controlo (rule_precedence/2, rule_rank/2, fired/2): que ativação
%     dispara primeiro e quais já dispararam (resolução de conflitos e refração).

:- dynamic wm_fact/2, wm_support/3, wm_next_id/1,                     % memória de trabalho
           activation/5,                                                  % agenda
           fired/2,                                                       % Meta-conhecimento: refração
           rule_reads/2, rule_rank/2, rule_precedence/2, index_built/0.   % Meta-conhecimento: índice e controlo

% Proteção contra um conjunto de regras que não para de produzir novas instanciações.
max_firings_per_cycle(5000).

% Meta-conhecimento (índice de dependências) ligado por omissão. Desligado, cada disparo
% avalia de novo todas as regras, como um ciclo reconhecer-atuar ingénuo: só serve para medir
% o que o índice poupa (DependencyIndexBenchmarkTest); as decisões são as mesmas.
:- dynamic use_dependency_index/1, cycle_stats/3, rematch_count/1.
use_dependency_index(true).
rematch_count(0).
% cycle_stats(Rematches, Firings, Millis): o que custou o último run_rules/0.
cycle_stats(0, 0, 0.0).

set_dependency_index(Flag) :-
    must_be(boolean, Flag),
    retractall(use_dependency_index(_)),
    assertz(use_dependency_index(Flag)).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Memória de trabalho.
%
% Nota: WM retém tanto percepção (sensor readings, etc) como
% conclusões intermédias para aumentar a performance.

reset_working_memory :-
    retractall(wm_fact(_, _)),
    retractall(wm_support(_, _, _)),
    retractall(activation(_, _, _, _, _)),
    retractall(fired(_, _)),
    retractall(wm_next_id(_)),
    % Reinicia contador
    assertz(wm_next_id(1)).

% A consulta usada pelas condições das regras e pelos predicados auxiliares.
known(Fact) :-
    wm_fact(_, Fact).

% Um facto percebido: nenhuma regra o suporta. Os factos são um conjunto, pelo que um
% duplicado é ignorado.
add_fact(Fact) :-
    store_fact(Fact, perceived, _).

% store_fact(+Fact, +Support, -New): New é true quando o facto ainda não era conhecido.

% store_fact(...) para percepção
store_fact(Fact, _, false) :-
    wm_fact(_, Known),
    Known == Fact, !.

% store_fact(...) para factos derivados (para explicações)
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
% Meta-conhecimento dinâmico: índice de dependências (construído uma vez, a partir das regras e
% auxiliares carregados)
%
% As regras são lidas como dados e analisadas estaticamente. Para cada regra regista-se:
%   rule_reads(Nome/Aridade, Regra)   os tipos de facto que a regra lê (padrões, padrões
%                                     negados e chamadas a known/1 nos auxiliares dos testes)
%   rule_precedence(Regra, P)         a sua precedência
%   rule_rank(Regra, Ordem)           a sua posição na base de conhecimento
% O primeiro é usado em fire/2 para avaliar de novo só as regras afetadas por um facto
% novo; os outros dois, em best_activation/2, para resolver conflitos.

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
condition_reads(prove(Goal), Keys0, Keys) :- !,
    goal_reads(Goal, [], _, Keys0, Keys).
condition_reads(Pattern, Keys, [Name/Arity|Keys]) :-
    functor(Pattern, Name, Arity).

% goal_reads(+Term, +Seen0, -Seen, +Keys0, -Keys): os tipos de facto que um objetivo pode
% ler. Percorre todos os subtermos (pelo que findall/3, aggregate_all/3, \+ e if-then-else
% não precisam de tratamento especial) e as cláusulas de cada predicado do utilizador que
% encontra, uma vez cada. Sobrestimar só custa uma nova avaliação a mais.
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

% Um predicado deste programa, por oposição a um predicado do sistema ou de uma biblioteca.
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

% Uma instanciação: as condições tal como corresponderam, a evidência (ids dos factos e os
% padrões negados que se verificaram) e as ações a executar.
match(Id, inst(Conditions, Evidence, Actions)) :-
    rule(Id, _, Conditions, Actions),
    satisfy(Conditions, Evidence).

satisfy(Conditions, Evidence) :-
    satisfy(Conditions, [], Evidence).

% Seen: a evidência das condições anteriores a esta, para que um facto que uma prova volta a
% ler (a faixa do ego, por exemplo) fique listado uma só vez.
satisfy([], _, []).
satisfy([Condition|Conditions], Seen, Evidence) :-
    holds(Condition, Proof),
    proof_evidence(Proof, Seen, Items),
    append(Items, Seen, Seen1),
    satisfy(Conditions, Seen1, Evidence0),
    append(Items, Evidence0, Evidence).

proof_evidence(none, _, []) :- !.
proof_evidence(proof(Ids), Seen, Items) :- !,
    new_ids(Ids, Seen, Items).
proof_evidence(Proof, _, [Proof]).

new_ids([], _, []).
new_ids([Id|Ids], Seen, Items) :-
    (   memberchk(Id, Seen)
    ->  new_ids(Ids, Seen, Items)
    ;   Items = [Id|Items0],
        new_ids(Ids, [Id|Seen], Items0)
    ).

holds(not(test(Goal)), none) :- !,
    \+ call(Goal).
holds(not(Pattern), not(Pattern)) :- !,
    \+ wm_fact(_, Pattern).
holds(test(Goal), none) :- !,
    call(Goal).
holds(prove(Goal), proof(Ids)) :- !,
    once(prove_goal(Goal, Ids)).
holds(Pattern, Id) :-
    wm_fact(Id, Pattern).

% Encadeamento inverso: prove_goal(+Goal, -Ids) prova Goal a partir das cláusulas dos
% predicados auxiliares até aos factos, e devolve os ids dos factos que leu.
prove_goal(true, []) :- !.
prove_goal((A, B), Ids) :- !,
    prove_goal(A, IdsA),
    prove_goal(B, IdsB),
    append(IdsA, IdsB, Ids).
prove_goal(known(Fact), [Id]) :- !,
    wm_fact(Id, Fact).
prove_goal(Goal, Ids) :-
    functor(Goal, Name, Arity),
    helper_predicate(Name/Arity), !,
    clause(Goal, Body),
    prove_goal(Body, Ids).
prove_goal(Goal, []) :-
    call(Goal).

% Substitui as ativações da regra pelas suas correspondências atuais, deixando de fora o que
% já disparou.
% Meta-conhecimento: precedência e ordem da regra (do índice) e refração (already_fired/2).
rematch(Id) :-
    retract(rematch_count(N0)),
    N is N0 + 1,
    assertz(rematch_count(N)),
    retractall(activation(Id, _, _, _, _)),
    rule_precedence(Id, Precedence),
    rule_rank(Id, Rank),
    findall(Inst, match(Id, Inst), Insts),
    forall(( nth1(Order, Insts, Inst),
             \+ already_fired(Id, Inst) ),
           assertz(activation(Id, Precedence, Rank, Order, Inst))).

% Meta-conhecimento (refração): uma instanciação que já disparou neste ciclo.
already_fired(Id, inst(Conditions, _, _)) :-
    fired(Id, Done),
    Done =@= Conditions, !.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Agenda e disparo
%
% Meta-conhecimento de controlo: best_activation/2 escolhe a ativação pela precedência e
% pela ordem da regra; fire/2 regista o disparo para a refração e usa o índice de
% dependências para avaliar de novo apenas as regras que leem os tipos de facto novos.

run_rules :-
    ensure_index,
    retractall(rematch_count(_)),
    assertz(rematch_count(0)),
    get_time(Start),
    forall(rule_rank(Id, _), rematch(Id)),
    max_firings_per_cycle(Max),
    fire_loop(0, Max, Firings),
    get_time(End),
    Millis is (End - Start) * 1000.0,
    rematch_count(Rematches),
    retractall(cycle_stats(_, _, _)),
    assertz(cycle_stats(Rematches, Firings, Millis)).

fire_loop(Count, Max, Firings) :-
    (   best_activation(Id, Inst)
    ->  (   Count >= Max
        ->  throw(error(rule_firing_limit(Max, Id), _))
        ;   true
        ),
        fire(Id, Inst),
        Next is Count + 1,
        fire_loop(Next, Max, Firings)
    ;   Firings = Count
    ).

% Meta-conhecimento (resolução de conflitos): maior precedência, depois a ordem da regra,
% depois a ordem da correspondência.
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
    % Meta-conhecimento (índice de dependências): só as regras que leem um tipo de facto novo.
    (   use_dependency_index(true)
    ->  findall(Reader, ( member(Key, Changed), rule_reads(Key, Reader) ), Readers0)
    ;   findall(Reader, rule_rank(Reader, _), Readers0)
    ),
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

% explain(+FactId, -Tree): como um facto passou a ser conhecido.
%   perceived(Fact)                  carregado a partir da perceção
%   derived(Fact, Rule, Because)     concluído por Rule; Because explica cada elemento da
%                                    evidência, absent(Pattern) para um padrão negado
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

% why_not(+RuleId, -Blocking): porque é que uma regra não tem agora correspondência. De
% todas as maneiras de fazer corresponder as condições a partir da esquerda, escolhe a que vai
% mais longe e indica a condição onde parou, seguida das condições posteriores que também não
% se verificam, todas com as ligações produzidas por essa correspondência parcial.
why_not(Id, []) :-
    match(Id, _), !.
why_not(Id, Blocking) :-
    rule(Id, _, Conditions, _),
    findall(Depth-Rest, partial_match(Conditions, 0, Depth, Rest), Partials),
    msort(Partials, Sorted),
    last(Sorted, _-[Stop|Later]),
    include(fails_leniently, Later, AlsoFailing),
    Blocking = [Stop|AlsoFailing].

% Cada prefixo que se verifica, com as condições que ficam depois dele.
partial_match([], Depth, Depth, []).
partial_match([Condition|Conditions], Depth0, Depth, Rest) :-
    (   holds(Condition, _),
        Depth1 is Depth0 + 1,
        partial_match(Conditions, Depth1, Depth, Rest)
    ;   Depth = Depth0,
        Rest = [Condition|Conditions]
    ).

% Uma condição com variáveis que a correspondência falhada nunca ligou pode lançar um erro:
% conta como não se verificando.
fails_leniently(Condition) :-
    \+ catch(holds(Condition, _), _, fail).

% subgoal_results(+Goal, -Results): porque é que um objetivo provado por encadeamento inverso
% se verifica ou não. Toma o corpo da cláusula do objetivo e prova cada conjunção
% isoladamente, para que um objetivo que falha indique os subobjetivos que falham. Results é
% uma lista de SubGoal-true ou SubGoal-false.
subgoal_results(Goal, Results) :-
    clause(Goal, Body), !,
    conjuncts(Body, SubGoals),
    maplist(subgoal_result, SubGoals, Results).

conjuncts((A, B), Goals) :- !,
    conjuncts(A, GoalsA),
    conjuncts(B, GoalsB),
    append(GoalsA, GoalsB, Goals).
conjuncts(Goal, [Goal]).

subgoal_result(Goal, Goal-Holds) :-
    (   once(prove_goal(Goal, _)) -> Holds = true ; Holds = false ).

