% Servidor do backend Prolog: um processo swipl persistente, um pedido JSON por linha no
% stdin e uma resposta JSON por linha no stdout (o padrão de ligação de Docs/Exemplo_Projeto,
% mas sem arrancar um processo por pedido, porque a simulação decide a cada 100 ms).
%
%   swipl -q -g main -t halt server.pl
%
% Pedidos:
%   {"cmd":"reset", "cruiseTargetKmh":..., "params":{...}, "priorities":{...}}
%   {"cmd":"tick", "timestamp":ms, "speedKmh":..., "positionMeters":..., "lane":"RIGHT", "events":[{"type":...}, ...],
%    "laneChangeState":"IDLE", "targetLane":"CENTER", "leftMarking":"DASHED", "rightMarking":"CONTINUOUS",
%    "leftAdjacentLaneSafe":true, "rightAdjacentLaneSafe":false,
%    "rightLaneBlockedAhead":false, "presentationDemo":false, "laneSettledAtMillis":-1,
%    "previousDecision":{"action":"STOP", "rule":"...", "priority":60}}
%   (os campos a partir da segunda linha são opcionais)
%   {"cmd":"whynot", "rule":"<rule id>"}
%   {"cmd":"dependencyIndex", "enabled":false}   só para medir o índice (ligado por omissão)
% Respostas: {"ok":true, ...} ou {"ok":false, "error":"..."}. A resposta ao tick leva também
% laneChecks: para cada lado, se cada subobjetivo de safe_to_change_lane/1 se prova; e
% engineStats: quantas avaliações de regras e disparos o ciclo custou, e em quanto tempo.
% Os avisos e erros do Prolog vão para o stderr; o stdout só leva respostas.

:- use_module(library(http/json)).

:- ensure_loaded(engine).
:- ensure_loaded(driving_state).
:- ensure_loaded(driving_rules).

main :-
    set_stream(user_input, encoding(utf8)),
    set_stream(user_output, encoding(utf8)),
    repeat,
    read_line_to_string(user_input, Line),
    (   Line == end_of_file
    ->  !
    ;   handle_line(Line),
        fail
    ).

% Every request gets a reply, also when it fails: Java is waiting for it.
handle_line(Line) :-
    (   catch(( atom_json_dict(Line, Request, [value_string_as(atom)]),
                reply(Request.cmd, Request, Reply0)
              ),
              Error,
              ( term_to_atom(Error, Text),
                Reply0 = _{ok: false, error: Text} ))
    ->  Reply = Reply0
    ;   format(atom(Text), 'Request failed: ~w', [Line]),
        Reply = _{ok: false, error: Text}
    ),
    json_write_dict(user_output, Reply, [width(0)]),
    nl(user_output),
    flush_output(user_output).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Comandos

reply(reset, R, _{ok: true}) :-
    reset_state(R.cruiseTargetKmh, R.params, R.priorities).

reply(tick, R, _{ok: true, proposals: Proposals, facts: Facts, speedLimitKmh: Limit, laneChecks: Checks,
                 engineStats: _{rematches: Rematches, firings: Firings, ruleMillis: Millis}}) :-
    downcase_atom(R.lane, Lane),
    optional(R, laneChangeState, 'IDLE', LaneChange),
    optional(R, leftMarking, 'DASHED', LeftMarking),
    optional(R, rightMarking, 'DASHED', RightMarking),
    optional(R, targetLane, R.lane, TargetLane),
    optional(R, leftAdjacentLaneSafe, false, SafeLeft),
    optional(R, rightAdjacentLaneSafe, false, SafeRight),
    optional(R, rightLaneBlockedAhead, false, RightBlocked),
    optional(R, presentationDemo, false, Demo),
    optional(R, positionMeters, 0.0, Position),
    optional(R, laneSettledAtMillis, -1, Settled),
    previous_decision_input(R, Previous),
    update_state(vehicle_input(R.timestamp, R.speedKmh, Position, Lane, LaneChange, LeftMarking-RightMarking,
                               TargetLane, SafeLeft-SafeRight, RightBlocked-Demo, Settled),
                 R.events, Previous),
    load_cycle_facts,
    run_rules,
    store_conclusions,
    findall(J, ( wm_fact(Id, proposal(_, _, _, _, _)), proposal_json(Id, J) ), Proposals),
    findall(J, ( wm_fact(Id, Fact), fact_json(Id, Fact, J) ), Facts),
    findall(J, ( member(Side, [left, right]), lane_check_json(Side, J) ), Checks),
    limit_state(Limit, _, _),
    cycle_stats(Rematches, Firings, Millis).

reply(dependencyIndex, R, _{ok: true, dependencyIndex: Flag}) :-
    Flag = R.enabled,
    set_dependency_index(Flag).

reply(whynot, R, _{ok: true, rule: R.rule, failedConditions: Texts}) :-
    why_not(R.rule, Blocking),
    maplist(text, Blocking, Texts).

optional(Dict, Key, Default, Value) :-
    (   get_dict(Key, Dict, Value0) -> Value = Value0 ; Value = Default ).

previous_decision_input(R, previous(Action, Rule, Priority)) :-
    (   get_dict(previousDecision, R, P)
    ->  Action = P.action, Rule = P.rule,
        optional(P, priority, 0, Priority)
    ;   Action = 'CONTINUE', Rule = 'Default continue decision', Priority = 0
    ).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% JSON

proposal_json(Id, _{rule: Rule, action: Action, speedKmh: Speed, risk: Risk, priority: Value,
                    reason: Reason, evidence: Evidence}) :-
    wm_fact(Id, proposal(A, Speed0, R, Priority, Reason)),
    wm_support(Id, Rule, Support),
    upcase_atom(A, Action),
    upcase_atom(R, Risk),
    (   Speed0 == no_target -> Speed = null ; Speed is float(Speed0) ),   % null: NaN in Java
    priority_value(Priority, Value),
    maplist(evidence_text, Support, Evidence).

% The evidence of a proposal: the facts it rests on, each with the rule that concluded it when
% it does not come from perception, and the negated patterns that held.
evidence_text(not(Pattern), Text) :- !,
    format(atom(Text), 'not ~w', [Pattern]).
evidence_text(Id, Text) :-
    wm_fact(Id, Fact),
    (   wm_support(Id, Rule, _)
    ->  format(atom(Text), '~w (by rule \'~w\')', [Fact, Rule])
    ;   format(atom(Text), '~w', [Fact])
    ).

% "Why not change lane?": each sub-goal of safe_to_change_lane/1, proved on its own.
lane_check_json(Side, _{side: Side, subGoals: Goals}) :-
    subgoal_results(safe_to_change_lane(Side), Results),
    findall(_{goal: Name, holds: Holds}, ( member(Goal-Holds, Results), functor(Goal, Name, _) ), Goals).

fact_json(Id, Fact, _{n: Id, fact: Text, rule: Rule}) :-
    format(atom(Text), '~w', [Fact]),
    (   wm_support(Id, Rule0, _) -> Rule = Rule0 ; Rule = null ).

text(Term, Text) :-
    format(atom(Text), '~w', [Term]).
