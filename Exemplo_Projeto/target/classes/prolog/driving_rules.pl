:- use_module(library(http/json)).

main :-
    json_read_dict(current_input, Payload, [value_string_as(atom)]),
    Sensors = Payload.sensors,
    Rules = Payload.knowledgeBase.rules,
    maplist(evaluate_rule(Sensors), Rules, Results),
    json_write_dict(current_output, Results, [width(0)]),
    nl.

evaluate_rule(Sensors, Rule, Result) :-
    include(failed_condition(Sensors), Rule.conditions, FailedConditions),
    maplist(failure_text(Sensors), FailedConditions, FailedTexts),
    ( FailedConditions = [] -> Active = true ; Active = false ),
    Result = _{
        ruleId: Rule.id,
        ruleName: Rule.name,
        priority: Rule.priority,
        decision: Rule.decision,
        reason: Rule.reason,
        active: Active,
        failedConditions: FailedTexts
    }.

failed_condition(Sensors, Condition) :-
    \+ condition_holds(Sensors, Condition).

condition_holds(Sensors, Condition) :-
    get_dict(Condition.sensor, Sensors, Actual),
    expected_value(Sensors, Condition, Expected),
    compare_value(Condition.operator, Actual, Expected).

expected_value(Sensors, Condition, Expected) :-
    ( memberchk(Condition.operator, ['LT_FIELD', 'GT_FIELD', 'LTE_FIELD'])
    -> get_dict(Condition.value, Sensors, Expected)
    ;  Expected = Condition.value
    ).

compare_value('EQ', Actual, Expected) :- Actual == Expected.
compare_value('LT', Actual, Expected) :- Actual < Expected.
compare_value('LTE', Actual, Expected) :- Actual =< Expected.
compare_value('GT', Actual, Expected) :- Actual > Expected.
compare_value('GTE', Actual, Expected) :- Actual >= Expected.
compare_value('LT_FIELD', Actual, Expected) :- Actual < Expected.
compare_value('GT_FIELD', Actual, Expected) :- Actual > Expected.
compare_value('LTE_FIELD', Actual, Expected) :- Actual =< Expected.

failure_text(Sensors, Condition, Text) :-
    ( get_dict(Condition.sensor, Sensors, Actual) -> true ; Actual = desconhecido ),
    ( expected_value(Sensors, Condition, Expected) -> true ; Expected = desconhecido ),
    format(atom(Text), '~w=~w nao satisfaz ~w ~w',
           [Condition.sensor, Actual, Condition.operator, Expected]).
