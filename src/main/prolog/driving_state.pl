% Estado da perceção mantido entre ciclos (o lado Prolog de 01-state-updates.drl).
%
% A memória de trabalho do motor é refeita em cada ciclo de 100 ms. O que tem de sobreviver
% de um ciclo para o seguinte fica aqui: a última leitura de cada sensor, o limite de
% velocidade em vigor e o sinal de limite lido à frente, as amostras recentes de TTC e o compromisso assumido perante um
% semáforo. Cada evento dos sensores atualiza este estado; depois, load_cycle_facts/0
% transforma-o nos factos iniciais do ciclo, e store_conclusions/0 guarda o que as regras
% concluíram e tem de persistir.

:- dynamic ego_state/3, lane_change/1, markings/2, target_lane_state/1, safe_lanes/2,
           road_flags/2, lane_request/3, accel_request/2, ego_position/1,
           weather_state/4,
           obstacle_track/5, obstacle_reading/5, pothole_track/5, barrier_track/5,
           limit_state/3, pending_limit/4, light_track/5, pedestrian_track/5, ttc_reading/3, rain_reading/1, stop_track/5,
           front_track/4, front_distance_reading/3, occupancy/4, occupancy_read/2, left_vehicle_track/4,
           right_vehicle_track/4, far_left_vehicle_track/4,
           overtaking_state/5, last_decision/2, last_priority/1, move_off/2, cruise/1,
           param/2, priority_value/2, lane_settled/1, physical_lane/1, pedestrian_braking/2.

% Janela da regra de TTC persistente (2s).
ttc_window_ms(2000).
% Um TTC baixo conta quando há uma leitura de chuva a menos disto.
rain_coincidence_ms(400).
% Leituras em falta após as quais um objeto deixa de ser considerado.
pedestrian_validity_ms(1000).
light_validity_ms(400).
stop_validity_ms(1500).
% Sem leitura do radar há mais de 250 ms, o veículo da frente deixa de estar à frente.
front_validity_ms(250).
% Janela das distâncias ao veículo da frente.
front_distance_window_ms(2000).
left_vehicle_validity_ms(250).
% Obstáculos, buracos e barreiras: esquecidos 1 s após a última leitura.
hazard_validity_ms(1000).
% Janela da travagem de emergência para um obstáculo.
obstacle_window_ms(300).
% Um sinal de limite de velocidade pendente que a câmara deixa de reportar ainda à frente é descartado.
pending_limit_validity_ms(1000).
% Um pedido do condutor dura 1,5s.
request_validity_ms(1500).

state_predicates([ego_state/3, lane_change/1, markings/2, target_lane_state/1, safe_lanes/2,
                  road_flags/2, lane_request/3, accel_request/2, ego_position/1,
                  weather_state/4,
                  obstacle_track/5, obstacle_reading/5, pothole_track/5, barrier_track/5,
                  limit_state/3, pending_limit/4, light_track/5, pedestrian_track/5, ttc_reading/3, rain_reading/1, stop_track/5,
                  front_track/4, front_distance_reading/3, occupancy/4, occupancy_read/2, left_vehicle_track/4,
                  right_vehicle_track/4, far_left_vehicle_track/4,
                  overtaking_state/5, last_decision/2, last_priority/1, move_off/2, cruise/1,
                  param/2, priority_value/2, lane_settled/1, physical_lane/1, pedestrian_braking/2]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Reinício da sessão

% O mesmo estado inicial que as sementes de KnowledgeSession.create: um limite por omissão de
% 120 km/h (não um sinal), nenhum semáforo, nenhum peão.
reset_state(Cruise, Params, Priorities) :-
    state_predicates(Predicates),
    forall(member(Name/Arity, Predicates),
           ( functor(Head, Name, Arity), retractall(Head) )),
    assertz(ego_state(0.0, center, 0)),
    % physical_lane(Lane): a faixa em que o veículo está fisicamente (VehicleStateFact.physicalLane),
    % que difere da faixa assumida durante uma mudança de faixa e no tick em que esta termina.
    assertz(physical_lane(center)),
    assertz(lane_change(idle)),
    assertz(markings(dashed, dashed)),
    assertz(target_lane_state(center)),
    assertz(safe_lanes(false, false)),
    % road_flags(RightLaneBlockedAhead, PresentationDemo), calculados pelo Java.
    assertz(road_flags(false, false)),
    % Pedidos do condutor: lane_request(Active, Direction, Ms), accel_request(TargetKmh, Ms).
    assertz(lane_request(false, left, -1)),
    assertz(accel_request(0.0, 0)),
    assertz(ego_position(0.0)),
    % weather_state(Condition, Visibility, Grip, Ms): semente de tempo limpo, como no Drools.
    assertz(weather_state(clear, 1.0, 1.0, 0)),
    % occupancy(Side, Occupied, FrontDistanceM, SafeToEnter). As faixas adjacentes começam
    % ocupadas e sem segurança para entrar: o veículo nunca entra numa faixa só porque ainda
    % não a detetou (uma semente pessimista, como no Drools).
    assertz(occupancy(left, true, inf, false)),
    assertz(occupancy(right, true, inf, false)),
    % occupancy_read(Side, Ms): quando esse lado foi lido pela última vez (LaneOccupancyState.timestamp).
    assertz(occupancy_read(left, -1)),
    assertz(occupancy_read(right, -1)),
    % lane_settled(Ms): quando terminou a última mudança de faixa (laneSettledAtMillis), -1 se nenhuma.
    assertz(lane_settled(-1)),
    % overtaking_state(Active, OriginLane, TargetPassed, StartMs, Target)
    assertz(overtaking_state(false, right, false, 0, none)),
    % boxed_in_state(Active, Lane, SinceMs): o BoxedInFact do Drools, ausente no início.
    retractall(boxed_in_state(_, _, _)),
    assertz(boxed_in_state(false, none, 0)),
    assertz(limit_state(120, default, 0)),
    % pending_limit(SignId, LimitKmh, SignPositionM, Ms): o PendingSpeedLimit do Drools.
    assertz(pending_limit(none, 0, inf, -1)),
    assertz(last_decision(continue, 'Default continue decision')),
    assertz(last_priority(0)),
    % move_off(Active, Ms): o MoveOffState do Drools.
    assertz(move_off(false, 0)),
    % pedestrian_braking(Phase, Ms): o PedestrianBrakingState do Drools (none, braking, holding).
    assertz(pedestrian_braking(none, 0)),
    assertz(cruise(Cruise)),
    forall(get_dict(Name, Params, Value), assertz(param(Name, Value))),
    forall(get_dict(Name, Priorities, Value), assertz(priority_value(Name, Value))).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Ciclo: estado do veículo, eventos e esquecimento do que deixou de ser visto

% vehicle_input(Now, SpeedKmh, PositionM, Lane, LaneChange, marcações Left-Right, TargetLane,
%               SafeLeft-SafeRight, RightBlocked-Demo, LaneSettledMs, PhysicalLane)
% A decisão anterior, já arbitrada pelo Java: o Drools também a vê, como o facto
% DrivingDecision, e a regra STOP usa-a para manter uma travagem forte já em curso.
update_state(vehicle_input(Now, Speed, Position, Lane, LaneChange, Markings, Target, Safe, Flags, Settled, Physical),
             Events, previous(Action, Rule, Priority)) :-
    Flags = RightBlocked-Demo,
    replace(lane_settled(_), lane_settled(Settled)),
    replace(physical_lane(_), physical_lane(Physical)),
    replace(road_flags(_, _), road_flags(RightBlocked, Demo)),
    replace(last_priority(_), last_priority(Priority)),
    replace(ego_position(_), ego_position(Position)),
    downcase_atom(Target, TargetLower),
    replace(target_lane_state(_), target_lane_state(TargetLower)),
    % As faixas adjacentes em que o Java considera que o ego pode entrar (WorldState.isLaneSafe), como
    % leftAdjacentLaneSafe e rightAdjacentLaneSafe no VehicleStateFact do Drools.
    Safe = SafeLeft-SafeRight,
    replace(safe_lanes(_, _), safe_lanes(SafeLeft, SafeRight)),
    replace(ego_state(_, _, _), ego_state(Speed, Lane, Now)),
    downcase_atom(LaneChange, LaneChangeLower),
    replace(lane_change(_), lane_change(LaneChangeLower)),
    Markings = Left-Right,
    downcase_atom(Left, LeftLower),
    downcase_atom(Right, RightLower),
    replace(markings(_, _), markings(LeftLower, RightLower)),
    downcase_atom(Action, ActionLower),
    replace(last_decision(_, _), last_decision(ActionLower, Rule)),
    % Só uma distância adota um novo veículo da frente; a velocidade relativa e o TTC só atualizam o
    % que já é seguido. As distâncias vão primeiro para que a ordem dos eventos no ciclo não
    % importe, como no Drools, que reavalia após cada atualização.
    % "Activate the pending speed limit at its sign" corre antes das novas leituras, para que um sinal
    % acabado de passar entre em vigor antes de a leitura da câmara do seguinte o substituir.
    activate_pending_limit(Now),
    partition(distance_event, Events, Distances, Others),
    maplist(record_event, Distances),
    maplist(record_event, Others),
    expire_old_readings(Now).

% "Activate the pending speed limit at its sign": o limite pendente passa a ser o limite em vigor
% quando o veículo chega ao sinal; o sinal pendente é esquecido, mantendo a hora da leitura
% para que uma leitura mais antiga não o possa trazer de volta.
activate_pending_limit(Now) :-
    (   pending_limit(SignId, Limit, SignPosition, Ms),
        SignId \== none,
        ego_position(Position),
        Position >= SignPosition
    ->  replace(limit_state(_, _, _), limit_state(Limit, SignId, Now)),
        replace(pending_limit(_, _, _, _), pending_limit(none, 0, inf, Ms))
    ;   true
    ).

replace(Old, New) :-
    retractall(Old),
    assertz(New).

record_event(Event) :-
    (   get_dict(type, Event, Type), on_event(Type, Event)
    ->  true
    ;   true    % um tipo de evento que nenhuma regra lê ainda: ignorado
    ).

% Uma distância infinita chega do Java como "Infinity".
distance(X, X) :- number(X), !.
distance(_, inf).

valid_hazard(Id, D) :-
    Id \== none, number(D), D > 0.

distance_event(Event) :-
    get_dict(type, Event, 'FrontDistanceEvent').

% "Track the speed limit sign ahead as pending": um limite só começa no seu sinal, por isso um sinal
% lido à frente é guardado com a sua posição na estrada até o veículo lá chegar.
on_event('SpeedLimitDetectedEvent', E) :-
    pending_limit(_, _, _, Since),
    (   number(E.distanceMeters),
        E.timestamp > Since
    ->  ego_position(Position),
        SignPosition is Position + E.distanceMeters,
        replace(pending_limit(_, _, _, _), pending_limit(E.signId, E.limitKmh, SignPosition, E.timestamp)),
        ego_state(_, _, Now),
        activate_pending_limit(Now)
    ;   true
    ).

% "Update traffic light state": o compromisso diz respeito a um semáforo e a uma fase amarela/vermelha;
% outro semáforo ou o verde cancelam-no.
on_event('TrafficLightEvent', E) :-
    number(E.distanceMeters),    % uma distância infinita chega como "Infinity": sem leitura
    downcase_atom(E.state, State),
    (   light_track(Id0, _, _, Commitment0, _), Id0 == E.lightId, State \== green
    ->  Commitment = Commitment0
    ;   Commitment = none
    ),
    replace(light_track(_, _, _, _, _),
            light_track(E.lightId, State, E.distanceMeters, Commitment, E.timestamp)).

% "Update pedestrian state": uma leitura sem peão (id none, distância não positiva ou
% infinita) limpa o estado.
on_event('PedestrianDetectedEvent', E) :-
    retractall(pedestrian_track(_, _, _, _, _)),
    (   E.pedestrianId \== none, number(E.distanceMeters), E.distanceMeters > 0
    ->  downcase_atom(E.crossingState, Crossing),
        assertz(pedestrian_track(E.pedestrianId, E.distanceMeters, E.inPath, Crossing, E.timestamp))
    ;   true
    ).

% "Update stop sign state": uma leitura a mais de 8 m é uma nova aproximação, por isso a paragem
% já feita é esquecida; mais perto, a paragem em curso é mantida.
on_event('StopSignEvent', E) :-
    number(E.distanceMeters),
    (   stop_track(_, _, Since, Start0, Done0)
    ->  E.timestamp >= Since
    ;   Start0 = -1, Done0 = false
    ),
    % A câmara mede até ao sinal; o estado guarda a distância à linha de paragem, que
    % fica StoppingPolicy.STOP_SIGN_OFFSET_METERS antes dele.
    param(stopSignOffsetMeters, Offset),
    ToStopLine is E.distanceMeters - Offset,
    (   ToStopLine > 8
    ->  Start = -1, Done = false
    ;   Start = Start0, Done = Done0
    ),
    replace(stop_track(_, _, _, _, _),
            stop_track(E.signId, ToStopLine, E.timestamp, Start, Done)).

% "Update obstacle/pothole/barrier state": uma leitura por tipo de perigo; uma leitura sem
% perigo (none, distância não positiva ou infinita) limpa-o. Cada leitura de obstáculo entra
% também na janela da travagem de emergência.
on_event('ObstacleDetectedEvent', E) :-
    retractall(obstacle_track(_, _, _, _, _)),
    (   valid_hazard(E.obstacleId, E.distanceMeters)
    ->  downcase_atom(E.severity, Severity),
        downcase_atom(E.lane, Lane),
        assertz(obstacle_track(E.obstacleId, E.distanceMeters, Severity, Lane, E.timestamp)),
        assertz(obstacle_reading(E.timestamp, E.distanceMeters, Severity, Lane, E.obstacleId))
    ;   true
    ).

on_event('PotholeDetectedEvent', E) :-
    retractall(pothole_track(_, _, _, _, _)),
    (   valid_hazard(E.potholeId, E.distanceMeters)
    ->  downcase_atom(E.severity, Severity),
        downcase_atom(E.lane, Lane),
        assertz(pothole_track(E.potholeId, E.distanceMeters, Severity, Lane, E.timestamp))
    ;   true
    ).

on_event('BarrierDetectedEvent', E) :-
    retractall(barrier_track(_, _, _, _, _)),
    (   valid_hazard(E.barrierId, E.distanceMeters)
    ->  downcase_atom(E.lane, Lane),
        assertz(barrier_track(E.barrierId, E.distanceMeters, E.open, Lane, E.timestamp))
    ;   true
    ).

% "Update weather state": a última leitura do sensor ambiental; não expira. As
% regras do tempo leem este estado (as leituras de chuva são também guardadas para a regra de TTC, como
% os WeatherEvents no Drools): os ReducedVisibilityEvent e ReducedGripEvent que o sensor
% também emite abaixo de 0,85 são ignorados, como no Drools, onde já nenhuma regra os lê.
on_event('WeatherEvent', E) :-
    (   E.condition == 'RAIN'
    ->  assertz(rain_reading(E.timestamp))
    ;   true
    ),
    weather_state(_, _, _, Since),
    (   E.timestamp >= Since
    ->  downcase_atom(E.condition, Condition),
        replace(weather_state(_, _, _, _),
                weather_state(Condition, E.visibilityFactor, E.gripFactor, E.timestamp))
    ;   true
    ).

% Mantém a leitura ambiental atual como contexto antecedente das mesmas regras difusas de
% velocidade do Drools. Os tipos de evento reduced-* antigos são ignorados de propósito; o
% sensor emite WeatherEvent no mesmo ciclo e o DRL já não tem regras nítidas para eles.
on_event('ReducedVisibilityEvent', _E).
on_event('ReducedGripEvent', _E).

% "Update lane change request": só um pedido mais recente (ou outra direção no mesmo
% instante) ativa o estado; reativar um pedido expirado contrariaria a expiração.
on_event('LaneChangeRequestEvent', E) :-
    lane_request(_, Direction0, Since),
    downcase_atom(E.direction, Direction),
    (   E.timestamp >= Since, ( E.timestamp > Since ; Direction \== Direction0 )
    ->  replace(lane_request(_, _, _), lane_request(true, Direction, E.timestamp))
    ;   true
    ).

% "Update acceleration request".
on_event('AccelerationRequestEvent', E) :-
    accel_request(Target0, Since),
    (   E.timestamp >= Since, ( E.timestamp > Since ; abs(Target0 - E.targetSpeedKmh) > 0.01 )
    ->  replace(accel_request(_, _), accel_request(E.targetSpeedKmh, E.timestamp))
    ;   true
    ).

on_event('TTCEvent', E) :-
    (   number(E.ttcSeconds)
    ->  assertz(ttc_reading(E.timestamp, E.ttcSeconds, E.targetId))
    ;   true
    ).

% "Update front vehicle from distance": uma leitura sem alvo (none, distância não
% positiva ou infinita) limpa o veículo da frente. Cada leitura válida entra também na janela.
on_event('FrontDistanceEvent', E) :-
    (   front_track(_, _, _, Since) -> E.timestamp >= Since ; true ),
    (   E.targetId \== none, number(E.distanceMeters), E.distanceMeters > 0
    ->  (   front_track(Target0, _, Rel0, _), Target0 == E.targetId -> Rel = Rel0 ; Rel = 0.0 ),
        replace(front_track(_, _, _, _), front_track(E.targetId, E.distanceMeters, Rel, E.timestamp)),
        assertz(front_distance_reading(E.timestamp, E.distanceMeters, E.targetId))
    ;   retractall(front_track(_, _, _, _))
    ).

% "Update lane occupancy left/right": Side é o lado do ego (left/right), não uma faixa.
on_event('LaneOccupancyEvent', E) :-
    downcase_atom(E.lane, Side),
    distance(E.frontDistanceMeters, Front),
    replace(occupancy(Side, _, _, _), occupancy(Side, E.occupied, Front, E.safeToEnter)),
    replace(occupancy_read(Side, _), occupancy_read(Side, E.timestamp)).

% "Update left lane vehicle state": o veículo mais próximo à frente na faixa da esquerda.
on_event('LeftLaneVehicleEvent', E) :-
    (   E.targetId \== none, number(E.distanceMeters)
    ->  replace(left_vehicle_track(_, _, _, _),
                left_vehicle_track(E.targetId, E.distanceMeters, E.relativeSpeedMps, E.timestamp))
    ;   retractall(left_vehicle_track(_, _, _, _))
    ).

% "Update right lane vehicle state": o veículo mais próximo à frente na faixa da direita.
on_event('RightLaneVehicleEvent', E) :-
    (   E.targetId \== none, number(E.distanceMeters)
    ->  replace(right_vehicle_track(_, _, _, _),
                right_vehicle_track(E.targetId, E.distanceMeters, E.relativeSpeedMps, E.timestamp))
    ;   retractall(right_vehicle_track(_, _, _, _))
    ).

% "Update far left lane vehicle state": o veículo mais próximo à frente duas faixas à esquerda.
on_event('FarLeftLaneVehicleEvent', E) :-
    (   E.targetId \== none, number(E.distanceMeters)
    ->  replace(far_left_vehicle_track(_, _, _, _),
                far_left_vehicle_track(E.targetId, E.distanceMeters, E.relativeSpeedMps, E.timestamp))
    ;   retractall(far_left_vehicle_track(_, _, _, _))
    ).

% "Update front vehicle from relative speed": só para o veículo já seguido.
on_event('RelativeSpeedEvent', E) :-
    (   front_track(Target, D, _, Since), Target == E.targetId, E.timestamp >= Since,
        number(E.relativeSpeedMps)
    ->  replace(front_track(_, _, _, _), front_track(Target, D, E.relativeSpeedMps, E.timestamp))
    ;   true
    ).

% Leituras mantidas numa janela temporal: descartadas quando atingem Window ms de idade.
expire_window(Template, Stamp, Window, Now) :-
    forall(( Template, Now - Stamp >= Window ), retract(Template)).

% Objetos seguidos: descartados quando não são lidos há mais de Validity ms.
expire_track(Template, Stamp, Validity, Now) :-
    forall(( Template, Now - Stamp > Validity ), retract(Template)).

expire_old_readings(Now) :-
    ttc_window_ms(TtcWindow),
    expire_window(ttc_reading(T1, _, _), T1, TtcWindow, Now),
    % Uma leitura de chuva mais antiga do que a janela de TTC e a coincidência não corresponde a nenhum TTC.
    rain_coincidence_ms(Coincidence),
    RainWindow is TtcWindow + Coincidence,
    expire_window(rain_reading(TR), TR, RainWindow, Now),
    pedestrian_validity_ms(PedestrianValidity),
    expire_track(pedestrian_track(_, _, _, _, T2), T2, PedestrianValidity, Now),
    light_validity_ms(LightValidity),
    expire_track(light_track(_, _, _, _, T3), T3, LightValidity, Now),
    front_validity_ms(FrontValidity),
    expire_track(front_track(_, _, _, T4), T4, FrontValidity, Now),
    front_distance_window_ms(FrontWindow),
    expire_window(front_distance_reading(T5, _, _), T5, FrontWindow, Now),
    left_vehicle_validity_ms(LeftValidity),
    expire_track(left_vehicle_track(_, _, _, T6), T6, LeftValidity, Now),
    expire_track(right_vehicle_track(_, _, _, T8), T8, LeftValidity, Now),
    expire_track(far_left_vehicle_track(_, _, _, T7), T7, LeftValidity, Now),
    expire_lane_request(Now),
    expire_pending_limit(Now),
    hazard_validity_ms(HazardValidity),
    expire_track(obstacle_track(_, _, _, _, T9), T9, HazardValidity, Now),
    expire_track(pothole_track(_, _, _, _, T10), T10, HazardValidity, Now),
    expire_track(barrier_track(_, _, _, _, T11), T11, HazardValidity, Now),
    obstacle_window_ms(ObstacleWindow),
    expire_window(obstacle_reading(T12, _, _, _, _), T12, ObstacleWindow, Now),
    stop_validity_ms(StopValidity),
    expire_stop_track(StopValidity, Now).

% "Clear stale stop sign perception": uma paragem ainda devida depois da linha (a câmara perdeu
% o sinal ao passar por ele) é mantida até ser feita.
expire_stop_track(Validity, Now) :-
    forall(( stop_track(Id, D, Ms, Start, Done), Now - Ms > Validity,
             ( Done == true ; D > 4.0 ) ),
           retract(stop_track(Id, D, Ms, Start, Done))).

% "Expire lane change request".
expire_lane_request(Now) :-
    request_validity_ms(Validity),
    (   lane_request(true, Direction, Since), Now - Since > Validity
    ->  replace(lane_request(_, _, _), lane_request(false, Direction, Since))
    ;   true
    ).

% "Drop a pending speed limit the camera no longer sees": a câmara reporta um sinal em todas as
% leituras até o veículo o passar, por isso um sinal que deixa de ser reportado ainda à frente
% desapareceu ou saiu do campo de visão (a chuva encurta o alcance da câmara).
expire_pending_limit(Now) :-
    pending_limit_validity_ms(Validity),
    (   pending_limit(SignId, _, _, Since), SignId \== none, Now - Since > Validity
    ->  replace(pending_limit(_, _, _, _), pending_limit(none, 0, inf, Since))
    ;   true
    ).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Conclusões que persistem

% O compromisso no semáforo, o instante em que o veículo parou no STOP, o STOP
% cumprido, o arranque e o estado de ultrapassagem com que o ciclo terminou.
store_conclusions :-
    forall(( known(light_commitment(Id, Commitment)),
             retract(light_track(Id, State, D, _, Ms)) ),
           assertz(light_track(Id, State, D, Commitment, Ms))),
    forall(( known(stop_hold_started(Id, Start)),
             retract(stop_track(Id, D, Ms, _, Done)) ),
           assertz(stop_track(Id, D, Ms, Start, Done))),
    forall(( known(stop_completed(Id)),
             retract(stop_track(Id, D, Ms, Start, _)) ),
           assertz(stop_track(Id, D, Ms, Start, true))),
    (   known(move_off_started(Since))
    ->  replace(move_off(_, _), move_off(true, Since))
    ;   known(move_off_ended)
    ->  move_off(_, Previous), replace(move_off(_, _), move_off(false, Previous))
    ;   true
    ),
    (   known(pedestrian_braking_started(BrakeSince))
    ->  replace(pedestrian_braking(_, _), pedestrian_braking(braking, BrakeSince))
    ;   known(pedestrian_approach_over)
    ->  pedestrian_braking(_, BrakePrevious),
        replace(pedestrian_braking(_, _), pedestrian_braking(none, BrakePrevious))
    ;   known(pedestrian_braking_ended)
    ->  pedestrian_braking(_, BrakePrevious),
        replace(pedestrian_braking(_, _), pedestrian_braking(holding, BrakePrevious))
    ;   true
    ),
    current_overtaking(Active, Origin, Passed, Start, Target),
    replace(overtaking_state(_, _, _, _, _), overtaking_state(Active, Origin, Passed, Start, Target)),
    % Mostrado na memória de trabalho que o utilizador vê: o estado com que o ciclo terminou. As
    % regras já pararam, por isso nenhuma o lê.
    add_fact(overtaking_result(Active, Origin, Passed, Start, Target)),
    store_boxed_in.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Estado -> memória de trabalho (os factos iniciais do ciclo)

% Limpa os factos meteorológicos derivados entre ciclos. O Drools completa a perceção antes da
% agenda de decisão; reutilizar graus de pertença difusos do ciclo anterior pode causar propostas
% difusas obsoletas que o estado meteorológico atual já tinha retirado no Drools.
prepare_cycle :-
    findall(Id, (wm_fact(Id, Fact), cycle_fuzzy_fact(Fact)), Ids),
    forall(member(Id, Ids),
           ( retractall(wm_support(Id, _, _)), retractall(wm_fact(Id, _)) )).

cycle_fuzzy_fact(fuzzy_membership(_, _, _)).
cycle_fuzzy_fact(fuzzy_conclusion(_, _)).
cycle_fuzzy_fact(composed_fuzzy_conclusion(_, _)).

load_cycle_facts :-
    reset_working_memory,
    ego_state(Speed, Lane, Now),
    add_fact(time_ms(Now)),
    add_fact(ego_speed(Speed)),
    add_fact(ego_lane(Lane)),
    physical_lane(Physical),
    add_fact(ego_physical_lane(Physical)),
    % ego_path_lane(Lane): a faixa ao longo da qual segue a trajetória do veículo (VehicleStateFact.pathLane):
    % a faixa de destino enquanto uma mudança de faixa está em curso, a faixa assumida caso contrário.
    lane_change(ChangeState),
    target_lane_state(PathTarget),
    (   ChangeState == idle -> PathLane = Lane ; PathLane = PathTarget ),
    add_fact(ego_path_lane(PathLane)),
    ego_position(Position),
    add_fact(ego_position(Position)),
    load_vehicle,
    load_hazards(Now),
    load_driver_requests(Now),
    load_weather,
    load_road,
    load_overtaking,
    load_speed_limit,
    load_lights_and_signs,
    load_vehicles(Now),
    load_previous_decision,
    load_ttc(Now),
    load_boxed_in.

load_vehicle :-
    lane_change(State),
    add_fact(lane_change_state(State)),
    target_lane_state(Target),
    add_fact(target_lane(Target)),
    safe_lanes(SafeLeft, SafeRight),
    add_fact(adjacent_lane_safe(left, SafeLeft)),
    add_fact(adjacent_lane_safe(right, SafeRight)),
    lane_settled(Settled),
    add_fact(lane_settled_at(Settled)),
    road_flags(RightBlocked, Demo),
    (   RightBlocked == true -> add_fact(right_lane_blocked_ahead) ; true ),
    (   Demo == true -> add_fact(presentation_demo) ; true ).

load_hazards(Now) :-
    forall(obstacle_track(Id, D, Severity, Lane, _),
           add_fact(obstacle(Id, D, Severity, Lane))),
    forall(obstacle_reading(Ms, D, Severity, Lane, Id),
           ( Age is Now - Ms, add_fact(obstacle_sample(Id, D, Severity, Lane, Age)) )),
    forall(pothole_track(Id, D, Severity, Lane, _),
           add_fact(pothole(Id, D, Severity, Lane))),
    forall(barrier_track(Id, D, Open, Lane, _),
           add_fact(barrier(Id, D, Open, Lane))).

load_driver_requests(Now) :-
    (   lane_request(true, Direction, _) -> add_fact(lane_change_request(Direction)) ; true ),
    lane_request(_, _, RequestedAt),
    add_fact(lane_request_at(RequestedAt)),
    accel_request(Target, Since),
    Age is Now - Since,
    add_fact(acceleration_request(Target, Age)).

load_weather :-
    weather_state(Condition, Visibility, Grip, _),
    add_fact(weather(Condition, Visibility, Grip)),
    fuzzy_input_degrees(Visibility, VisibilityPoor, VisibilityReduced, VisibilityFair, VisibilityGood),
    fuzzy_input_degrees(Grip, GripPoor, GripReduced, GripFair, GripGood),
    add_fact(fuzzy_membership(visibility, poor, VisibilityPoor)),
    add_fact(fuzzy_membership(visibility, reduced, VisibilityReduced)),
    add_fact(fuzzy_membership(visibility, fair, VisibilityFair)),
    add_fact(fuzzy_membership(visibility, good, VisibilityGood)),
    add_fact(fuzzy_membership(grip, poor, GripPoor)),
    add_fact(fuzzy_membership(grip, reduced, GripReduced)),
    add_fact(fuzzy_membership(grip, fair, GripFair)),
    add_fact(fuzzy_membership(grip, good, GripGood)).

load_road :-
    markings(Left, Right),
    add_fact(left_marking(Left)),
    add_fact(right_marking(Right)),
    forall(occupancy(Side, Occupied, Front, Safe),
           ( add_fact(lane_occupancy(Side, Occupied, Front)),
             add_fact(lane_safe_to_enter(Side, Safe)) )),
    forall(occupancy_read(Side, Ms),
           add_fact(lane_occupancy_read(Side, Ms))).

load_overtaking :-
    overtaking_state(Active, Origin, Passed, Start, Target),
    add_fact(overtaking(Active, Origin, Passed, Start, Target)).

load_speed_limit :-
    limit_state(Limit, Source, _),
    add_fact(speed_limit(Limit)),
    (   Source == default
    ->  add_fact(no_speed_sign)
    ;   add_fact(signed_speed_limit(Limit))
    ),
    (   pending_limit(SignId, Next, SignPosition, _), SignId \== none
    ->  add_fact(pending_speed_limit(Next, SignPosition))
    ;   true
    ),
    cruise(Cruise),
    add_fact(cruise_speed(Cruise)).

load_lights_and_signs :-
    forall(light_track(Id, State, D, Commitment, _),
           ( add_fact(traffic_light(Id, State, D)),
             (   Commitment == none -> true ; add_fact(light_commitment(Id, Commitment)) ) )),
    forall(pedestrian_track(Id, D, InPath, Crossing, _),
           ( add_fact(pedestrian(Id, D)),
             add_fact(crossing_state(Id, Crossing)),
             (   InPath == true -> add_fact(pedestrian_in_path(Id)) ; true ) )),
    forall(stop_track(Id, D, _, Start, Done),
           ( add_fact(stop_sign(Id, D)),
             (   Start >= 0 -> add_fact(stop_hold_started(Id, Start)) ; true ),
             (   Done == true -> add_fact(stop_completed(Id)) ; true ) )).

% Um veículo lido neste mesmo ciclo recebe também um facto *_read_now.
load_vehicles(Now) :-
    forall(front_track(Id, D, Rel, Ms),
           ( add_fact(front_vehicle(Id, D, Rel)),
             (   Ms =:= Now -> add_fact(front_read_now(Id)) ; true ) )),
    forall(left_vehicle_track(Id, D, Rel, Ms),
           ( add_fact(left_lane_vehicle(Id, D, Rel)),
             (   Ms =:= Now -> add_fact(left_read_now(Id)) ; true ) )),
    forall(right_vehicle_track(Id, D, Rel, _),
           add_fact(right_lane_vehicle(Id, D, Rel))),
    forall(far_left_vehicle_track(Id, D, Rel, _),
           add_fact(far_left_lane_vehicle(Id, D, Rel))),
    forall(front_distance_reading(Ms, D, Target),
           ( Age is Now - Ms, add_fact(front_distance_sample(Target, D, Age)) )).

load_previous_decision :-
    last_decision(Action, Rule),
    add_fact(previous_decision(Action, Rule)),
    last_priority(Priority),
    add_fact(previous_priority(Priority)),
    move_off(Active, Since),
    add_fact(move_off_state(Active, Since)),
    pedestrian_braking(Braking, BrakeSince),
    add_fact(pedestrian_braking_state(Braking, BrakeSince)).

load_ttc(Now) :-
    forall(ttc_reading(Ms, Ttc, Target),
           ( Age is Now - Ms, add_fact(ttc_sample(Target, Ttc, Age)) )),
    forall(rain_reading(Ms),
           ( Age is Now - Ms, add_fact(rain_sample(Age)) )).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Preso atrás de trânsito lento (BoxedInFact no Drools)

:- dynamic boxed_in_state/3.

% Iniciado ou limpo pelas regras deste ciclo; caso contrário, fica como estava.
store_boxed_in :-
    (   known(boxed_in_started(Lane, Since))
    ->  replace(boxed_in_state(_, _, _), boxed_in_state(true, Lane, Since))
    ;   known(boxed_in_cleared)
    ->  replace(boxed_in_state(_, _, _), boxed_in_state(false, none, 0))
    ;   true
    ).

load_boxed_in :-
    (   boxed_in_state(true, Lane, Since)
    ->  add_fact(boxed_in(Lane, Since))
    ;   true
    ).

