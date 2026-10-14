% Estado da perceção mantido entre ciclos (o lado Prolog de 01-state-updates.drl).
%
% A memória de trabalho do motor é refeita em cada ciclo de 100 ms. O que tem de sobreviver
% de um ciclo para o seguinte fica aqui: a última leitura de cada sensor, o limite de
% velocidade em vigor, as amostras recentes de TTC e o compromisso assumido perante um
% semáforo. Cada evento dos sensores atualiza este estado; depois, load_cycle_facts/0
% transforma-o nos factos iniciais do ciclo, e store_conclusions/0 guarda o que as regras
% concluíram e tem de persistir.

:- dynamic ego_state/3, lane_change/1, markings/2, target_lane_state/1, safe_lanes/2,
           road_flags/2, lane_request/3, accel_request/2, radar/6,
           weather_state/4, visibility_reading/2, grip_reading/2,
           obstacle_track/5, obstacle_reading/5, pothole_track/5, barrier_track/5,
           limit_state/3, light_track/5, pedestrian_track/5, ttc_reading/3, stop_track/5,
           front_track/4, front_distance_reading/3, occupancy/3, left_vehicle_track/4,
           overtaking_state/5, last_decision/2, last_priority/1, move_off/2, cruise/1,
           param/2, priority_value/2.

% Window of the persistent-TTC rule (window:time(2s) in Drools).
ttc_window_ms(2000).
% Missed readings after which an object is no longer considered (Drools: 1000/400).
pedestrian_validity_ms(1000).
light_validity_ms(400).
stop_validity_ms(1500).
% With no radar reading for more than 250 ms, the front vehicle is no longer ahead.
front_validity_ms(250).
% Window of the distances to the front vehicle (window:time(2s) in Drools).
front_distance_window_ms(2000).
left_vehicle_validity_ms(250).
% Obstacles, potholes and barriers: forgotten 1 s after the last reading.
hazard_validity_ms(1000).
% Window of the emergency brake for an obstacle (window:time(300ms) in Drools).
obstacle_window_ms(300).
radar_validity_ms(1000).
% A driver request lasts 1.5 s.
request_validity_ms(1500).
% Window of the reduced visibility and grip readings (window:time(500ms) in Drools).
weather_window_ms(500).

state_predicates([ego_state/3, lane_change/1, markings/2, target_lane_state/1, safe_lanes/2,
                  road_flags/2, lane_request/3, accel_request/2, radar/6,
                  weather_state/4, visibility_reading/2, grip_reading/2,
                  obstacle_track/5, obstacle_reading/5, pothole_track/5, barrier_track/5,
                  limit_state/3, light_track/5, pedestrian_track/5, ttc_reading/3, stop_track/5,
                  front_track/4, front_distance_reading/3, occupancy/3, left_vehicle_track/4,
                  overtaking_state/5, last_decision/2, last_priority/1, move_off/2, cruise/1,
                  param/2, priority_value/2]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Reinício da sessão

% The same initial state as the seeds of KnowledgeSession.create: a default limit of
% 120 km/h (not a sign), no traffic light, no pedestrian.
reset_state(Cruise, Params, Priorities) :-
    state_predicates(Predicates),
    forall(member(Name/Arity, Predicates),
           ( functor(Head, Name, Arity), retractall(Head) )),
    assertz(ego_state(0.0, center, 0)),
    assertz(lane_change(idle)),
    assertz(markings(dashed, dashed)),
    assertz(target_lane_state(center)),
    assertz(safe_lanes(false, false)),
    % road_flags(RightLaneBlockedAhead, PresentationDemo), computed by Java.
    assertz(road_flags(false, false)),
    % Driver requests: lane_request(Active, Direction, Ms), accel_request(TargetKmh, Ms).
    assertz(lane_request(false, left, -1)),
    assertz(accel_request(0.0, 0)),
    % radar(Id, DistanceM, LimitKmh, Ms, Detected, Passed), like the SpeedRadarFact.
    assertz(radar(none, inf, 50.0, 0, false, false)),
    % weather_state(Condition, Visibility, Grip, Ms): clear-weather seed, as in Drools.
    assertz(weather_state(clear, 1.0, 1.0, 0)),
    % The adjacent lanes start occupied: the vehicle never moves into a lane only because it
    % has not sensed it yet (a pessimistic seed, as in Drools).
    assertz(occupancy(left, true, inf)),
    assertz(occupancy(right, true, inf)),
    % overtaking_state(Active, OriginLane, TargetPassed, StartMs, Target)
    assertz(overtaking_state(false, right, false, 0, none)),
    assertz(limit_state(120, default, 0)),
    assertz(last_decision(continue, 'Default continue decision')),
    assertz(last_priority(0)),
    % move_off(Active, Ms): Drools' MoveOffState.
    assertz(move_off(false, 0)),
    assertz(cruise(Cruise)),
    forall(get_dict(Name, Params, Value), assertz(param(Name, Value))),
    forall(get_dict(Name, Priorities, Value), assertz(priority_value(Name, Value))).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Ciclo: estado do veículo, eventos e esquecimento do que deixou de ser visto

% vehicle_input(Now, SpeedKmh, Lane, LaneChange, Left-Right markings, TargetLane,
%               SafeLeft-SafeRight, RightBlocked-Demo)
% The previous decision, already arbitrated by Java: Drools also sees it, as the
% DrivingDecision fact, and the STOP rule uses it to keep hard braking already under way.
update_state(vehicle_input(Now, Speed, Lane, LaneChange, Markings, Target, Safe, Flags),
             Radar, Events, previous(Action, Rule, Priority)) :-
    Flags = RightBlocked-Demo,
    replace(road_flags(_, _), road_flags(RightBlocked, Demo)),
    replace(last_priority(_), last_priority(Priority)),
    update_radar(Now, Radar),
    downcase_atom(Target, TargetLower),
    replace(target_lane_state(_), target_lane_state(TargetLower)),
    % The adjacent lanes Java finds the ego may enter (WorldState.isLaneSafe), like
    % leftAdjacentLaneSafe and rightAdjacentLaneSafe in Drools' VehicleStateFact.
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
    % Only a distance adopts a new front vehicle; relative speed and TTC only update the one
    % already followed. Distances go first so the order of events in the cycle does not
    % matter, as in Drools, which re-evaluates after every update.
    partition(distance_event, Events, Distances, Others),
    maplist(record_event, Distances),
    maplist(record_event, Others),
    expire_old_readings(Now).

replace(Old, New) :-
    retractall(Old),
    assertz(New).

record_event(Event) :-
    (   get_dict(type, Event, Type), on_event(Type, Event)
    ->  true
    ;   true    % an event type no rule reads yet: ignored
    ).

% An infinite distance arrives from Java as "Infinity".
distance(X, X) :- number(X), !.
distance(_, inf).

valid_hazard(Id, D) :-
    Id \== none, number(D), D > 0.

distance_event(Event) :-
    get_dict(type, Event, 'FrontDistanceEvent').

% "Update current speed limit from latest sign": a lower limit applies at once, a higher one
% only when the sign is less than 12 m away.
on_event('SpeedLimitDetectedEvent', E) :-
    limit_state(Current, _, Since),
    (   number(E.distanceMeters),
        E.timestamp >= Since,
        ( E.limitKmh =< Current ; E.distanceMeters < 12 )
    ->  replace(limit_state(_, _, _), limit_state(E.limitKmh, E.signId, E.timestamp))
    ;   true
    ).

% "Update traffic light state": the commitment is about one light and one yellow/red phase;
% another light or green cancels it.
on_event('TrafficLightEvent', E) :-
    number(E.distanceMeters),    % an infinite distance arrives as "Infinity": no reading
    downcase_atom(E.state, State),
    (   light_track(Id0, _, _, Commitment0, _), Id0 == E.lightId, State \== green
    ->  Commitment = Commitment0
    ;   Commitment = none
    ),
    replace(light_track(_, _, _, _, _),
            light_track(E.lightId, State, E.distanceMeters, Commitment, E.timestamp)).

% "Update pedestrian state": a reading with no pedestrian (id none, distance not positive or
% infinite) clears the state.
on_event('PedestrianDetectedEvent', E) :-
    retractall(pedestrian_track(_, _, _, _, _)),
    (   E.pedestrianId \== none, number(E.distanceMeters), E.distanceMeters > 0
    ->  downcase_atom(E.crossingState, Crossing),
        assertz(pedestrian_track(E.pedestrianId, E.distanceMeters, E.inPath, Crossing, E.timestamp))
    ;   true
    ).

% "Update stop sign state": a reading more than 8 m away is a new approach, so the stop
% already made is forgotten; closer, the stop under way is kept.
on_event('StopSignEvent', E) :-
    number(E.distanceMeters),
    (   stop_track(_, _, Since, Start0, Done0)
    ->  E.timestamp >= Since
    ;   Start0 = -1, Done0 = false
    ),
    (   E.distanceMeters > 8
    ->  Start = -1, Done = false
    ;   Start = Start0, Done = Done0
    ),
    replace(stop_track(_, _, _, _, _),
            stop_track(E.signId, E.distanceMeters, E.timestamp, Start, Done)).

% "Update obstacle/pothole/barrier state": one reading per hazard type; a reading with no
% hazard (none, distance not positive or infinite) clears it. Each obstacle reading also
% enters the emergency-brake window.
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

% "Update speed radar perception": only while the radar has not been passed, and only when
% the reading brings something new. A reading with no radar clears it, keeping "detected".
on_event('SpeedRadarEvent', E) :-
    radar(Id0, D0, L0, Since, Detected, false),
    E.timestamp >= Since,
    (   E.timestamp > Since ; E.radarId \== Id0 ; abs(D0 - E.distanceMeters) > 0.01 ),
    !,
    (   valid_hazard(E.radarId, E.distanceMeters)
    ->  Id = E.radarId, D = E.distanceMeters, L = E.speedLimitKmh
    ;   Id = none, D = inf, L = L0
    ),
    replace(radar(_, _, _, _, _, _), radar(Id, D, L, E.timestamp, Detected, false)).
on_event('SpeedRadarEvent', _).

% "Update weather state": the environment sensor's latest reading; it does not expire.
on_event('WeatherEvent', E) :-
    weather_state(_, _, _, Since),
    (   E.timestamp >= Since
    ->  downcase_atom(E.condition, Condition),
        replace(weather_state(_, _, _, _),
                weather_state(Condition, E.visibilityFactor, E.gripFactor, E.timestamp))
    ;   true
    ).

% The sensor only emits them below 0.85; the rules read those of the last 500 ms.
on_event('ReducedVisibilityEvent', E) :-
    assertz(visibility_reading(E.timestamp, E.visibilityFactor)).

on_event('ReducedGripEvent', E) :-
    assertz(grip_reading(E.timestamp, E.gripFactor)).

% "Update lane change request": only a newer request (or another direction at the same
% instant) activates the state; reactivating an expired request would fight the expiry.
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

% "Update front vehicle from distance": a reading with no target (none, distance not
% positive or infinite) clears the front vehicle. Each valid reading also enters the window.
on_event('FrontDistanceEvent', E) :-
    (   front_track(_, _, _, Since) -> E.timestamp >= Since ; true ),
    (   E.targetId \== none, number(E.distanceMeters), E.distanceMeters > 0
    ->  (   front_track(Target0, _, Rel0, _), Target0 == E.targetId -> Rel = Rel0 ; Rel = 0.0 ),
        replace(front_track(_, _, _, _), front_track(E.targetId, E.distanceMeters, Rel, E.timestamp)),
        assertz(front_distance_reading(E.timestamp, E.distanceMeters, E.targetId))
    ;   retractall(front_track(_, _, _, _))
    ).

% "Update lane occupancy left/right": Side is the ego's side (left/right), not a lane.
on_event('LaneOccupancyEvent', E) :-
    downcase_atom(E.lane, Side),
    distance(E.frontDistanceMeters, Front),
    replace(occupancy(Side, _, _), occupancy(Side, E.occupied, Front)).

% "Update left lane vehicle state": the nearest vehicle ahead in the left lane.
on_event('LeftLaneVehicleEvent', E) :-
    (   E.targetId \== none, number(E.distanceMeters)
    ->  replace(left_vehicle_track(_, _, _, _),
                left_vehicle_track(E.targetId, E.distanceMeters, E.relativeSpeedMps, E.timestamp))
    ;   retractall(left_vehicle_track(_, _, _, _))
    ).

% "Update front vehicle from relative speed": only for the vehicle already followed.
on_event('RelativeSpeedEvent', E) :-
    (   front_track(Target, D, _, Since), Target == E.targetId, E.timestamp >= Since,
        number(E.relativeSpeedMps)
    ->  replace(front_track(_, _, _, _), front_track(Target, D, E.relativeSpeedMps, E.timestamp))
    ;   true
    ).

% Readings kept in a time window: dropped once they are Window ms old.
expire_window(Template, Stamp, Window, Now) :-
    forall(( Template, Now - Stamp >= Window ), retract(Template)).

% Tracked objects: dropped when not read for more than Validity ms.
expire_track(Template, Stamp, Validity, Now) :-
    forall(( Template, Now - Stamp > Validity ), retract(Template)).

expire_old_readings(Now) :-
    ttc_window_ms(TtcWindow),
    expire_window(ttc_reading(T1, _, _), T1, TtcWindow, Now),
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
    expire_lane_request(Now),
    expire_radar(Now),
    weather_window_ms(WeatherWindow),
    expire_window(visibility_reading(T7, _), T7, WeatherWindow, Now),
    expire_window(grip_reading(T8, _), T8, WeatherWindow, Now),
    hazard_validity_ms(HazardValidity),
    expire_track(obstacle_track(_, _, _, _, T9), T9, HazardValidity, Now),
    expire_track(pothole_track(_, _, _, _, T10), T10, HazardValidity, Now),
    expire_track(barrier_track(_, _, _, _, T11), T11, HazardValidity, Now),
    obstacle_window_ms(ObstacleWindow),
    expire_window(obstacle_reading(T12, _, _, _, _), T12, ObstacleWindow, Now),
    stop_validity_ms(StopValidity),
    expire_track(stop_track(_, _, T13, _, _), T13, StopValidity, Now).

% "Expire lane change request".
expire_lane_request(Now) :-
    request_validity_ms(Validity),
    (   lane_request(true, Direction, Since), Now - Since > Validity
    ->  replace(lane_request(_, _, _), lane_request(false, Direction, Since))
    ;   true
    ).

expire_radar(Now) :-
    radar_validity_ms(Validity),
    (   radar(Id, _, L, Since, Detected, false), Id \== none, Now - Since > Validity
    ->  replace(radar(_, _, _, _, _, _), radar(none, inf, L, Now, Detected, false))
    ;   true
    ).

% What Java does to the SpeedRadarFact for Drools (DroolsBackend), received with the cycle:
%   Passed   the ego passed radar Passed after the previous cycle's actuation, or none;
%   Ahead    the nearest radar within 250 m in the world, or none.
% A passed radar never changes again in this session, as in Drools.
update_radar(Now, Passed-Ahead) :-
    (   Passed \== none, radar(Passed, _, L, _, Detected, false)
    ->  replace(radar(_, _, _, _, _, _), radar(Passed, 0.0, L, Now, Detected, true))
    ;   true
    ),
    (   Ahead = radar_ahead(Id, D, Limit),
        radar(Id0, D0, L0, _, Detected0, false),
        (   Id \== Id0 ; abs(D - D0) > 0.01 ; abs(Limit - L0) > 0.01 ; Detected0 == false )
    ->  replace(radar(_, _, _, _, _, _), radar(Id, D, Limit, Now, true, false))
    ;   true
    ).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Conclusões que persistem

% The commitment at the traffic light, the time the vehicle stopped at the STOP, the STOP
% completed, moving off, and the overtaking state the cycle ended with.
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
    current_overtaking(Active, Origin, Passed, Start, Target),
    replace(overtaking_state(_, _, _, _, _), overtaking_state(Active, Origin, Passed, Start, Target)),
    % Shown in the working memory the user sees: the state the cycle ended with. The rules
    % have already stopped, so none reads it.
    add_fact(overtaking_result(Active, Origin, Passed, Start, Target)).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Estado -> memória de trabalho (os factos iniciais do ciclo)

load_cycle_facts :-
    reset_working_memory,
    ego_state(Speed, Lane, Now),
    add_fact(time_ms(Now)),
    add_fact(ego_speed(Speed)),
    add_fact(ego_lane(Lane)),
    load_vehicle,
    load_hazards(Now),
    load_radar,
    load_driver_requests(Now),
    load_weather(Now),
    load_road,
    load_overtaking,
    load_speed_limit,
    load_lights_and_signs,
    load_vehicles(Now),
    load_previous_decision,
    load_ttc(Now).

load_vehicle :-
    lane_change(State),
    add_fact(lane_change_state(State)),
    target_lane_state(Target),
    add_fact(target_lane(Target)),
    safe_lanes(SafeLeft, SafeRight),
    add_fact(adjacent_lane_safe(left, SafeLeft)),
    add_fact(adjacent_lane_safe(right, SafeRight)),
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

load_radar :-
    (   radar(Id, D, Limit, _, true, false), Id \== none
    ->  add_fact(speed_radar(Id, D, Limit))
    ;   true
    ).

load_driver_requests(Now) :-
    (   lane_request(true, Direction, _) -> add_fact(lane_change_request(Direction)) ; true ),
    accel_request(Target, Since),
    Age is Now - Since,
    add_fact(acceleration_request(Target, Age)).

load_weather(Now) :-
    weather_state(Condition, Visibility, Grip, _),
    add_fact(weather(Condition, Visibility, Grip)),
    forall(visibility_reading(Ms, Value),
           ( Age is Now - Ms, add_fact(low_visibility_sample(Value, Age)) )),
    forall(grip_reading(Ms, Value),
           ( Age is Now - Ms, add_fact(low_grip_sample(Value, Age)) )).

load_road :-
    markings(Left, Right),
    add_fact(left_marking(Left)),
    add_fact(right_marking(Right)),
    forall(occupancy(Side, Occupied, Front),
           add_fact(lane_occupancy(Side, Occupied, Front))).

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

% A vehicle read in this very cycle also gets a *_read_now fact.
load_vehicles(Now) :-
    forall(front_track(Id, D, Rel, Ms),
           ( add_fact(front_vehicle(Id, D, Rel)),
             (   Ms =:= Now -> add_fact(front_read_now(Id)) ; true ) )),
    forall(left_vehicle_track(Id, D, Rel, Ms),
           ( add_fact(left_lane_vehicle(Id, D, Rel)),
             (   Ms =:= Now -> add_fact(left_read_now(Id)) ; true ) )),
    forall(front_distance_reading(Ms, D, Target),
           ( Age is Now - Ms, add_fact(front_distance_sample(Target, D, Age)) )).

load_previous_decision :-
    last_decision(Action, Rule),
    add_fact(previous_decision(Action, Rule)),
    last_priority(Priority),
    add_fact(previous_priority(Priority)),
    move_off(Active, Since),
    add_fact(move_off_state(Active, Since)).

load_ttc(Now) :-
    forall(ttc_reading(Ms, Ttc, Target),
           ( Age is Now - Ms, add_fact(ttc_sample(Target, Ttc, Age)) )).
