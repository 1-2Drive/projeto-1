% Base de conhecimento de condução, executada por engine.pl.
%
% Replica as regras Drools de src/main/resources/rules. O identificador de cada regra é o
% nome da regra Drools que replica. O número a seguir ao identificador é a precedência da
% regra, o mesmo valor que a regra Drools tem, para que a agenda as dispare pela mesma
% ordem: um número maior dispara primeiro. Quando uma regra Drools escolhe entre várias
% ações no seu RHS, aqui há uma regra por ação e o identificador acrescenta a variante entre
% parênteses, por exemplo 'Hold at red traffic light (emergency)'.
%
% As regras nunca atuam. Concluem propostas,
%   proposal(Action, SpeedKmh, Risk, PriorityName, Reason)
% e o Java arbitra-as com o mesmo DecisionContext do Drools (vence a prioridade mais alta;
% num empate, a velocidade mais baixa). PriorityName é uma constante de DecisionPriority em
% minúsculas; o Java envia os valores numéricos no reinício (priority_value/2). Uma
% proposta sem velocidade alvo (mudanças de faixa, NaN no Drools) leva no_target.
%
% Factos da perceção, carregados em cada ciclo por driving_state.pl:
%   time_ms(Ms)  ego_speed(Kmh)  ego_lane(Lane)  target_lane(Lane)  lane_change_state(State)
%   speed_limit(Kmh)  signed_speed_limit(Kmh) | no_speed_sign  cruise_speed(Kmh)
%   traffic_light(Id, red|yellow|green, DistanceM)  light_commitment(Id, stop|proceed)
%   pedestrian(Id, DistanceM)  crossing_state(Id, waiting|crossing|crossed)  pedestrian_in_path(Id)
%   ttc_sample(Target, TtcS, AgeMs)
%   stop_sign(Id, DistanceM)  stop_hold_started(Id, Ms)  stop_completed(Id)
%   previous_decision(Action, Rule)  previous_priority(Value)   a última decisão arbitrada
%   move_off_state(Active, Ms)   o MoveOffState do Drools
%   right_lane_blocked_ahead   um perigo na faixa da direita a 150 m ou menos (Java)
%   presentation_demo          o cenário de demonstração guiada (Java)
%   lane_change_request(left|right)   um pedido do condutor ainda ativo (1,5 s)
%   acceleration_request(TargetKmh, AgeMs)   o último pedido de aceleração do condutor
%   front_vehicle(Target, DistanceM, RelSpeedMps)   RelSpeed > 0: a aproximar-se
%   front_distance_sample(Target, DistanceM, AgeMs)  front_read_now(Target)
%   left_lane_vehicle(Target, DistanceM, RelSpeedMps)  left_read_now(Target)
%   lane_occupancy(left|right, Occupied, FrontDistanceM)   o lado do ego, não uma faixa
%   left_marking(dashed|continuous)  right_marking(dashed|continuous)
%   adjacent_lane_safe(left|right, true|false)   calculado pelo Java (WorldState.isLaneSafe)
%   overtaking(Active, OriginLane, TargetPassed, StartMs, Target)   estado do ciclo anterior
%   obstacle(Id, DistanceM, Severity, Lane)  obstacle_sample(Id, DistanceM, Severity, Lane, AgeMs)
%   pothole(Id, DistanceM, Severity, Lane)  barrier(Id, DistanceM, Open, Lane)
%   speed_radar(Id, DistanceM, LimitKmh)   detetado e ainda não ultrapassado
%   weather(clear|rain|..., Visibility, Grip)
%   low_visibility_sample(Visibility, AgeMs)  low_grip_sample(Grip, AgeMs)

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Emergência

rule('Emergency brake when persistent TTC is critical',
     900,
     [ time_ms(_),
       test(persistent_critical_ttc(Count, Minimum)),
       test(Count >= 3) ],
     [ assert(proposal(emergency_brake, 0.0, critical, emergency_safety,
                       'Persistent TTC critical across recent samples')),
       assert(persistent_ttc(Count, Minimum)) ]).

rule('Emergency brake when TTC is immediately critical',
     890,
     [ ttc_sample(_, Ttc, Age),
       test((Ttc > 0.0, Ttc < 1.3, Age < 200)) ],
     [ assert(proposal(emergency_brake, 0.0, critical, emergency_safety,
                       'Immediate TTC critical condition')) ]).

rule('Emergency stop for pedestrian extremely close',
     880,
     [ pedestrian_conflict(P),
       pedestrian(P, D),
       test(D =< 6.0) ],
     [ assert(proposal(emergency_brake, 0.0, critical, emergency_safety,
                       'Pedestrian critical distance in current path')) ]).

% Only a reading from the last 300 ms counts, and only in the ego's lane.
rule('Emergency braking for high severity obstacle at critical distance',
     870,
     [ obstacle_sample(_, D, high, Lane, Age),
       ego_lane(Lane),
       test((D > 0, D =< 8.0, Age < 300)) ],
     [ assert(proposal(emergency_brake, 0.0, critical, emergency_safety,
                       'High severity obstacle critically close')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Peões

% Intermediate knowledge: Drools writes "inPath == true || crossingState == CROSSING" in each
% rule; here the disjunction is a conclusion of its own, with one rule per branch. Precedence
% above every reader, so rules that require no conflict never fire before it is known.
rule('Pedestrian conflicts with trajectory (in path)',
     1000,
     [ pedestrian_in_path(P) ],
     [ assert(pedestrian_conflict(P)) ]).

rule('Pedestrian conflicts with trajectory (crossing)',
     1000,
     [ crossing_state(P, crossing) ],
     [ assert(pedestrian_conflict(P)) ]).

rule('Stop for pedestrian in crossing zone',
     600,
     [ pedestrian_conflict(P),
       pedestrian(P, D),
       test((D > 6.0, D =< 32.0)) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Pedestrian detected in trajectory')) ]).

% Above the 2.0 m/s2 braking curve that ends 18 m before the pedestrian: brake.
rule('Brake for pedestrian proximity (brake)',
     605,
     [ pedestrian_conflict(P),
       pedestrian(P, D),
       ego_speed(V),
       test(D > 32.0),
       test(above_pedestrian_curve(V, D, 2.0)),
       test(pedestrian_reason(P, Reason)) ],
     [ assert(proposal(brake, 20.0, high, legal_mandatory_stop, Reason)) ]).

% Between the 1.4 and 2.0 m/s2 curves: hold the speed, never speed up towards the pedestrian.
rule('Brake for pedestrian proximity (hold speed)',
     605,
     [ pedestrian_conflict(P),
       pedestrian(P, D),
       ego_speed(V),
       test(D > 32.0),
       test(above_pedestrian_curve(V, D, 1.4)),
       not(test(above_pedestrian_curve(V, D, 2.0))),
       test(pedestrian_reason(P, Reason)) ],
     [ assert(proposal(maintain_speed, V, moderate, legal_mandatory_stop, Reason)) ]).

rule('Hold for pedestrian still crossing',
     602,
     [ crossing_state(P, crossing),
       pedestrian(P, D),
       ego_speed(V),
       test(V < 1.0),
       test((D > 32.0, D =< 45.0)) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Stopped for a pedestrian that is still crossing; waiting for the crossing to be free')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Semáforos

% The commitment to stop or to proceed is taken once per light and phase, and kept in the
% state until the light turns green or another light replaces it.
rule('Commit to stop or to proceed at traffic light (red)',
     700,
     [ traffic_light(L, red, _),
       not(light_commitment(L, _)) ],
     [ assert(light_commitment(L, stop)) ]).

rule('Commit to stop or to proceed at traffic light (yellow, can stop)',
     700,
     [ traffic_light(L, yellow, D),
       not(light_commitment(L, _)),
       ego_speed(V),
       test(stopping_distance(V, comfort, Needed)),
       test(D >= Needed) ],
     [ assert(light_commitment(L, stop)) ]).

rule('Commit to stop or to proceed at traffic light (yellow, too close)',
     700,
     [ traffic_light(L, yellow, D),
       not(light_commitment(L, _)),
       ego_speed(V),
       test(stopping_distance(V, comfort, Needed)),
       test(D < Needed) ],
     [ assert(light_commitment(L, proceed)) ]).

% Approaching red, decided by the distance needed from the current speed.
rule('Red traffic light progressive approach (emergency)',
     620,
     [ light_commitment(L, stop),
       traffic_light(L, red, D),
       ego_speed(V),
       test(red_light_approach(D, V)),
       test(braking_shortfall(V, D, Shortfall)),
       test(Shortfall > 2.0) ],
     [ assert(proposal(emergency_brake, 0.0, high, legal_mandatory_stop,
                       'Red traffic light ahead; decelerating to stop before the line')) ]).

rule('Red traffic light progressive approach (brake)',
     620,
     [ light_commitment(L, stop),
       traffic_light(L, red, D),
       ego_speed(V),
       speed_limit(Limit),
       test(red_light_approach(D, V)),
       test(braking_shortfall(V, D, Shortfall)),
       test(Shortfall =< 2.0),
       test(stopping_distance(V, comfort, Needed)),
       test(D =< Needed),
       test(Target is min(V, Limit)) ],
     [ assert(proposal(reduce_speed, Target, moderate, legal_mandatory_stop,
                       'Red traffic light ahead; decelerating to stop before the line')) ]).

rule('Red traffic light progressive approach (hold speed)',
     620,
     [ light_commitment(L, stop),
       traffic_light(L, red, D),
       ego_speed(V),
       speed_limit(Limit),
       test(red_light_approach(D, V)),
       test(braking_shortfall(V, D, Shortfall)),
       test(Shortfall =< 2.0),
       test(stopping_distance(V, comfort, Needed)),
       test(D > Needed),
       test(Target is min(V, Limit)) ],
     [ assert(proposal(maintain_speed, Target, moderate, legal_mandatory_stop,
                       'Red traffic light ahead; decelerating to stop before the line')) ]).

rule('Hold at red traffic light (emergency)',
     640,
     [ light_commitment(L, stop),
       traffic_light(L, red, D),
       ego_speed(V),
       test(at_stop_line(D)),
       test(critical_stop_at_light(V, D)) ],
     [ assert(proposal(emergency_brake, 0.0, high, legal_mandatory_stop,
                       'Red traffic light ahead and no room left for a comfortable stop; braking hard')) ]).

rule('Hold at red traffic light (stop)',
     640,
     [ light_commitment(L, stop),
       traffic_light(L, red, D),
       ego_speed(V),
       test(at_stop_line(D)),
       not(test(critical_stop_at_light(V, D))) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Stopped before red traffic light; waiting for green')) ]).

rule('Red traffic light requires full stop and hold',
     635,
     [ light_commitment(L, stop),
       traffic_light(L, red, D),
       test((D >= 0, D =< 4.0)) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Red traffic light; vehicle must remain stopped before the line')) ]).

% Yellow with a commitment to stop: like red, but never an emergency brake, because the
% commitment was only taken with room for a comfortable stop.
rule('Yellow traffic light: stop before the line (stop)',
     625,
     [ light_commitment(L, stop),
       traffic_light(L, yellow, D),
       test(at_stop_line(D)) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Yellow traffic light with room to stop; stopping before the line')) ]).

rule('Yellow traffic light: stop before the line (brake)',
     625,
     [ light_commitment(L, stop),
       traffic_light(L, yellow, D),
       ego_speed(V),
       speed_limit(Limit),
       not(test(at_stop_line(D))),
       test(stopping_distance(V, comfort, Needed)),
       test(D =< Needed),
       test(Target is min(V, Limit)) ],
     [ assert(proposal(reduce_speed, Target, moderate, legal_mandatory_stop,
                       'Yellow traffic light with room to stop; stopping before the line')) ]).

rule('Yellow traffic light: stop before the line (hold speed)',
     625,
     [ light_commitment(L, stop),
       traffic_light(L, yellow, D),
       ego_speed(V),
       speed_limit(Limit),
       not(test(at_stop_line(D))),
       test(stopping_distance(V, comfort, ComfortNeeded)),
       test(D > ComfortNeeded),
       test(stopping_distance(V, anticipation, AnticipationNeeded)),
       test(D =< AnticipationNeeded),
       test(Target is min(V, Limit)) ],
     [ assert(proposal(maintain_speed, Target, moderate, legal_mandatory_stop,
                       'Yellow traffic light with room to stop; stopping before the line')) ]).

rule('Green traffic light allows progression when no higher hazard',
     180,
     [ traffic_light(_, green, D),
       test(D =< 40.0) ],
     [ assert(proposal(continue, 50.0, low, comfort_progress,
                       'Green light and no higher-priority hazard')) ]).

rule('Green traffic light releases red-light hold',
     610,
     [ traffic_light(_, green, D),
       test((D >= 0, D =< 40.0)),
       ego_speed(V),
       test(V < 1.0) ],
     [ assert(proposal(resume, 30.0, low, maneuver_safety,
                       'Traffic light is green; releasing the red-light hold')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Veículo da frente
%
% As regras que abrandam atrás do veículo da frente afastam-se no ciclo em que começa uma
% ultrapassagem: a ultrapassagem propõe com a prioridade de manobra e perderia sempre para
% elas, pelo que a mudança de faixa nunca começaria. A sua precedência é inferior à das
% regras de ultrapassagem, por isso a ultrapassagem já é conhecida quando são avaliadas. A
% partir do ciclo seguinte voltam a aplicar-se, também enquanto o veículo ainda está a sair
% da faixa. A travagem a curta distância nunca se afasta.

rule('Brake when front vehicle distance low',
     520,
     [ front_vehicle(_, D, Rel),
       test((D > 0, D < 14.0, Rel > 0)) ],
     [ assert(proposal(brake, 20.0, high, collision_avoidance,
                       'Front vehicle too close')) ]).

% Art. 18 and 24 CE: the speed from which 2.0 m/s2 still matches the front vehicle's speed
% 12 m behind it. Only proposes above that speed, because a recommendation above the current
% speed would make the actuator accelerate. Behind a stopped vehicle, keeps the stop until
% 2 m past the stopping point, so radar noise does not release it.
rule('Keep safe following distance behind front vehicle (stop)',
     510,
     [ not(overtake_started(_, _, _)),
       front_vehicle(_, D, Rel),
       ego_speed(V),
       test(D > 0),
       test(unsafe_following(V, D, Rel)),
       test(front_vehicle_stopped(V, Rel)),
       test(D - 12.0 =< 2.0) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Stopped behind a stationary front vehicle (Art. 18 CE)')) ]).

rule('Keep safe following distance behind front vehicle (reduce)',
     510,
     [ not(overtake_started(_, _, _)),
       front_vehicle(_, D, Rel),
       ego_speed(V),
       test(D > 0),
       test(unsafe_following(V, D, Rel)),
       not(test(( front_vehicle_stopped(V, Rel), D - 12.0 =< 2.0 ))),
       test(following_speed(V, D, Rel, Target)) ],
     [ assert(proposal(reduce_speed, Target, moderate, collision_avoidance,
                       'Keeping a safe distance to the front vehicle (Art. 18/24 CE)')) ]).

rule('Reduce speed when closing front vehicle',
     500,
     [ not(overtake_started(_, _, _)),
       front_vehicle(_, D, Rel),
       test((D >= 14.0, D < 35.0, Rel > 0.8)),
       test(Target is max(20.0, D * 1.2)) ],
     [ assert(proposal(reduce_speed, Target, moderate, collision_avoidance,
                       'Closing to front vehicle')) ]).

% Trend over the 2 s window, only with the distances of the vehicle followed now: when the
% radar switches to another vehicle, the jump in distance between the two is no approach.
rule('Approaching trend from sliding distance window',
     505,
     [ not(overtake_started(_, _, _)),
       front_vehicle(Target, D, _),
       test((D > 0, D < 50.0)),
       lane_change_state(idle),
       test(front_distance_window(Target, Count, Minimum, Maximum)),
       test(Count >= 3),
       test(Minimum < Maximum - 4.0) ],
     [ assert(proposal(reduce_speed, 35.0, moderate, collision_avoidance,
                       'Front distance trend decreasing quickly')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Ultrapassagem
%
% O estado da ultrapassagem vem do ciclo anterior (overtaking/5). As regras nunca o alteram:
% concluem o que mudou (overtake_started/3, overtake_cancelled, overtake_target_passed,
% overtake_finished), current_overtaking/5 deriva o estado resultante e driving_state.pl
% guarda-o no fim do ciclo.

% A proposed overtake that never left its lane (another rule won the cycle, or the actuator
% refused) would stay active for ever. One second is enough for a manoeuvre to begin.
rule('Cancel overtaking that never left its original lane',
     540,
     [ overtaking(true, Origin, false, Start, _),
       ego_lane(Origin),
       lane_change_state(idle),
       time_ms(Now),
       test(Now - Start > 1000) ],
     [ assert(overtake_cancelled) ]).

% Passed once the ego has left the origin lane, the right lane is clear 80 m ahead (the
% passed vehicle is no longer there) and the radar no longer has it ahead.
rule('Mark front vehicle passed once it is behind or cleared',
     480,
     [ overtaking(true, Origin, false, _, Target),
       ego_lane(Lane),
       test((Lane \== right, Lane \== Origin)),
       lane_change_state(idle),
       lane_occupancy(right, false, RightFront),
       test(RightFront > 80.0),
       not(front_vehicle(Target, _, _)) ],
     [ assert(overtake_target_passed) ]).

rule('Deactivate overtaking state once return right completes',
     455,
     [ overtaking(true, _, true, _, _),
       ego_lane(right),
       lane_change_state(idle) ],
     [ assert(overtake_finished) ]).

% Art. 35/36 CE: overtake on the left, with the left lane clear. The window to start grows
% with the closing speed (5.5 s of TTC plus 10 m). A stopped vehicle is only overtaken on
% the move with that whole margin. Towards the LEFT lane (the last one) it is only worth it
% if the next vehicle there is clearly further away. Art. 41 CE: never with a pedestrian in
% the way or a red or yellow light ahead.
rule('Overtake slow front vehicle via left lane',
     525,
     [ ego_lane(Lane),
       test(memberchk(Lane, [right, center])),
       lane_change_state(idle),
       left_marking(LeftMarking),
       test(LeftMarking \== continuous),
       ego_speed(V),
       time_ms(Now),
       front_vehicle(Target, D, Rel),
       front_read_now(Target),
       test((D > 15.0, Rel > 0.5)),
       lane_occupancy(left, false, LeftFront),
       not(pedestrian_conflict(_)),
       not(test(light_forbids_overtaking)),
       test(may_start_overtaking(Target, Lane)),
       test((Lane \== center ; LeftFront > D + 20.0)),
       test(D =< max(50.0, Rel * 5.5 + 10.0)),
       test((V / 3.6 - Rel >= 1.0 ; D >= Rel * 5.5)) ],
     [ assert(overtake_started(Target, Lane, Now)),
       assert(proposal(change_lane_left, no_target, low, maneuver_safety,
                       'Overtaking slower vehicle via clear left lane (Art. 35/36 CE)')) ]).

% Art. 36 CE: a slower vehicle in the left lane is not passed on its right. From the right
% lane, the ego first moves in behind it (RIGHT -> CENTER) and that vehicle becomes the
% target; once behind it, the previous rule continues CENTER -> LEFT.
rule('Move left behind slower left lane vehicle to overtake it on the left',
     525,
     [ ego_lane(right),
       lane_change_state(idle),
       right_marking(RightMarking),
       test(RightMarking \== continuous),
       ego_speed(V),
       time_ms(Now),
       left_lane_vehicle(Target, D, Rel),
       left_read_now(Target),
       test((D > 15.0, Rel > -0.5)),
       not(test(front_vehicle_within(D))),
       lane_occupancy(left, false, _),
       not(pedestrian_conflict(_)),
       not(test(light_forbids_overtaking)),
       test(may_start_overtaking(Target)),
       test(V / 3.6 - Rel >= 1.4),
       test(desired_speed(Desired)),
       test(Desired / 3.6 > V / 3.6 - Rel + 1.4),
       test(D =< max(50.0, Rel * 5.5 + 10.0)) ],
     [ assert(overtake_started(Target, right, Now)),
       assert(proposal(change_lane_left, no_target, low, maneuver_safety,
                       'Moving left behind a slower vehicle to overtake it on the left (Art. 36 CE)')) ]).

% Stopped behind a stopped vehicle (left occupied, or too close to go around it on the move):
% go around it once the left lane clears, within the same legal limits.
rule('Go around stationary front vehicle from standstill',
     525,
     [ ego_lane(Lane),
       test(memberchk(Lane, [right, center])),
       lane_change_state(idle),
       left_marking(LeftMarking),
       test(LeftMarking \== continuous),
       ego_speed(V),
       test(V < 3.0),
       time_ms(Now),
       front_vehicle(Target, D, Rel),
       test((D > 6.0, D =< 20.0, Rel > -0.3)),
       lane_occupancy(left, false, _),
       not(pedestrian_conflict(_)),
       not(test(light_forbids_overtaking)),
       test(may_start_overtaking(Target)),
       test(front_vehicle_stopped(V, Rel)) ],
     [ assert(overtake_started(Target, Lane, Now)),
       assert(proposal(change_lane_left, no_target, low, maneuver_safety,
                       'Going around a stationary vehicle via clear left lane (Art. 35/38 CE)')) ]).

% Art. 38 CE: after overtaking, go back right one lane at a time, with that lane clear
% alongside and 80 m ahead, and no lane change requested by the driver.
rule('Return to right lane after overtaking slow vehicle',
     480,
     [ test(current_overtaking(true, _, true, _, _)),
       ego_lane(Lane),
       test(Lane \== right),
       lane_change_state(idle),
       right_marking(RightMarking),
       test(RightMarking \== continuous),
       lane_occupancy(right, false, RightFront),
       test(RightFront > 80.0),
       not(lane_change_request(_)) ],
     [ assert(proposal(return_right, no_target, low, maneuver_safety,
                       'Portuguese Highway Code Art. 38: return to rightmost lane after overtaking')) ]).

% Art. 13 CE (keep right): drive in the rightmost available safe lane. CENTER and LEFT are
% manoeuvre lanes: once an overtake or an avoidance is over, with the right lane clear
% alongside and 80 m ahead, go back right one lane at a time. Nothing may need attention:
% moving off, obstacle, pothole, closed barrier, pedestrian in conflict, a vehicle within
% 80 m, an overtake, a lane change requested by the driver. It sees overtakes that start in
% this cycle (lower precedence). Moving off is as at the start of the cycle, as in Drools,
% where this rule fires before the moving-off rules.
rule('Enforce keep right road law when right lane is safe',
     460,
     [ ego_lane(Lane),
       test(Lane \== right),
       not(presentation_demo),
       lane_change_state(idle),
       right_marking(RightMarking),
       test(RightMarking \== continuous),
       not(right_lane_blocked_ahead),
       time_ms(Now),
       test(Now >= 4000),
       lane_occupancy(right, false, RightFront),
       test(RightFront > 80.0),
       not(move_off_state(true, _)),
       not(obstacle(_, _, _, _)),
       not(pothole(_, _, _, _)),
       not(barrier(_, _, false, _)),
       not(pedestrian_conflict(_)),
       not(test(front_vehicle_within(80.0))),
       not(lane_change_request(_)),
       not(test(current_overtaking(true, _, _, _, _))) ],
     [ assert(proposal(return_right, no_target, low, maneuver_safety,
                       'Portuguese Highway Code Art. 13: Drive on the rightmost available safe lane')) ]).

% Art. 36 CE: while not yet behind a slower vehicle in the left lane, the ego must not draw
% level with it. It follows the curve that matches that vehicle's speed 35 m behind it (just
% beyond the blind-spot range, so the lane reads clear and the ego can move in).
rule('Do not pass a slower left lane vehicle on its right (closing in)',
     508,
     [ not(overtake_started(_, _, _)),
       ego_speed(V),
       left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V > Curve) ],
     [ assert(proposal(reduce_speed, Curve, moderate, speed_compliance,
                       'Slower vehicle in the left lane must not be passed on its right (Art. 36 CE)')) ]).

rule('Do not pass a slower left lane vehicle on its right (fall back)',
     508,
     [ not(overtake_started(_, _, _)),
       ego_speed(V),
       left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V =< Curve),
       test((D < 33.0, Rel > -0.3)),
       test(Target is max(0.0, V / 3.6 - Rel - 1.5) * 3.6) ],
     [ assert(proposal(reduce_speed, Target, moderate, speed_compliance,
                       'Slower vehicle in the left lane must not be passed on its right (Art. 36 CE)')) ]).

rule('Do not pass a slower left lane vehicle on its right (hold)',
     508,
     [ not(overtake_started(_, _, _)),
       ego_speed(V),
       left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V =< Curve),
       not(test((D < 33.0, Rel > -0.3))),
       test(Target is min(V, (V / 3.6 - Rel) * 3.6)) ],
     [ assert(proposal(maintain_speed, Target, moderate, speed_compliance,
                       'Slower vehicle in the left lane must not be passed on its right (Art. 36 CE)')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Desvio de perigos na faixa do ego
%
% O veículo contorna um perigo na sua faixa quando os sensores de ângulo morto confirmam
% que uma faixa adjacente está livre. As regras só disparam com o perigo na faixa do ego,
% por isso, depois de mudar de faixa, deixam de corresponder e a manobra não oscila.

% While the lane change is executing, keep it as the decision. They also hold for an
% overtake or a driver request, so they propose at manoeuvre priority.
rule('Maintain ongoing lane change left avoidance',
     475,
     [ lane_change_state(State),
       test(memberchk(State, [requested, changing])),
       target_lane(Target),
       ego_lane(Lane),
       test((Target == left ; Lane == right, Target == center)) ],
     [ assert(proposal(change_lane_left, no_target, low, maneuver_safety,
                       'Executing ongoing lane change left')) ]).

rule('Maintain ongoing lane change right avoidance',
     475,
     [ lane_change_state(State),
       test(memberchk(State, [requested, changing])),
       target_lane(Target),
       ego_lane(Lane),
       test((Target == right ; Lane == left, Target == center)) ],
     [ assert(proposal(change_lane_right, no_target, low, maneuver_safety,
                       'Executing ongoing lane change right')) ]).

% Pothole: from the centre, prefer the right (the rightmost clear lane); left only with the
% right occupied.
rule('Avoid pothole in centre lane by changing right when right is clear',
     475,
     [ ego_lane(center),
       lane_change_state(idle),
       right_marking(RightMarking),
       test(RightMarking \== continuous),
       pothole(_, D, _, center),
       test((D > 0, D =< 50.0)),
       lane_occupancy(right, false, _) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear right lane (rightmost safe lane policy)')) ]).

rule('Avoid pothole in centre lane by changing left when right is occupied',
     470,
     [ ego_lane(center),
       lane_change_state(idle),
       left_marking(LeftMarking),
       test(LeftMarking \== continuous),
       pothole(_, D, _, center),
       test((D > 0, D =< 50.0)),
       lane_occupancy(right, true, _),
       lane_occupancy(left, false, _) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear left lane')) ]).

rule('Avoid pothole in left lane by changing right',
     470,
     [ ego_lane(left),
       lane_change_state(idle),
       pothole(_, D, _, left),
       test((D > 0, D =< 50.0)),
       lane_occupancy(right, false, _) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear right lane')) ]).

rule('Avoid pothole in right lane by changing left',
     470,
     [ ego_lane(right),
       lane_change_state(idle),
       pothole(_, D, _, right),
       test((D > 0, D =< 50.0)),
       lane_occupancy(left, false, _) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear left lane')) ]).

% Obstacle: from the centre, prefer the left; right only with the left occupied.
rule('Avoid obstacle in centre lane by changing left',
     470,
     [ ego_lane(center),
       lane_change_state(idle),
       obstacle(_, D, _, center),
       test((D > 0, D =< 50.0)),
       lane_occupancy(left, false, _) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding obstacle by moving into the clear left lane')) ]).

rule('Avoid obstacle in centre lane by changing right when left is occupied',
     470,
     [ ego_lane(center),
       lane_change_state(idle),
       obstacle(_, D, _, center),
       test((D > 0, D =< 50.0)),
       lane_occupancy(left, true, _),
       lane_occupancy(right, false, _) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding obstacle by moving into the clear right lane')) ]).

rule('Avoid obstacle in left lane by changing right',
     470,
     [ ego_lane(left),
       lane_change_state(idle),
       obstacle(_, D, _, left),
       test((D > 0, D =< 50.0)),
       lane_occupancy(right, false, _) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding obstacle by moving into the clear right lane')) ]).

rule('Avoid obstacle in right lane by changing left',
     470,
     [ ego_lane(right),
       lane_change_state(idle),
       obstacle(_, D, _, right),
       test((D > 0, D =< 50.0)),
       lane_occupancy(left, false, _) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding obstacle by moving into the clear left lane')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Pedidos de mudança de faixa do condutor
%
% Um pedido é apenas um comando: as regras decidem se a faixa pedida está livre antes de
% qualquer manobra. Se estiver ocupada, o veículo mantém a sua faixa.

rule('Block lane change when target lane occupied',
     430,
     [ lane_change_request(left),
       lane_occupancy(left, true, _) ],
     [ assert(proposal(keep_lane, no_target, moderate, maneuver_safety,
                       'Cannot change lane left; lane occupied')) ]).

rule('Allow lane change left when lane safe',
     420,
     [ lane_change_request(left),
       lane_occupancy(left, false, _) ],
     [ assert(proposal(change_lane_left, no_target, low, maneuver_safety,
                       'Lane change left is safe')) ]).

rule('Block lane change right when target lane occupied',
     430,
     [ lane_change_request(right),
       lane_occupancy(right, true, _) ],
     [ assert(proposal(keep_lane, no_target, moderate, maneuver_safety,
                       'Cannot change lane right; lane occupied')) ]).

rule('Allow lane change right when lane safe',
     420,
     [ lane_change_request(right),
       lane_occupancy(right, false, _) ],
     [ assert(proposal(change_lane_right, no_target, low, maneuver_safety,
                       'Lane change right is safe')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Perigos que não se podem contornar

% Pothole within 40 m (any lane) with no free lane to move into: slow down.
rule('Pothole ahead reduce speed',
     460,
     [ lane_change_state(idle),
       pothole(_, D, _, _),
       test((D > 0, D =< 40.0)),
       ego_lane(Lane),
       test(no_free_adjacent_lane(Lane)),
       test((D < 15 -> Target = 20.0 ; Target = 30.0)) ],
     [ assert(proposal(reduce_speed, Target, moderate, road_hazard,
                       'Pothole ahead requires speed reduction')) ]).

% Obstacle in the ego's lane and both adjacent lanes occupied: stop 10 m before it (outside
% the 8 m emergency zone, so it can still go around if a lane opens). It follows the
% comfortable braking curve; if the deceleration needed exceeds the comfortable one by 10%,
% it brakes hard.
rule('Stop before obstacle when no lane is free (stop)',
     465,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_occupancy(left, true, _),
       lane_occupancy(right, true, _),
       test(obstacle_margin(D, Margin)),
       test(Margin =< 0.5) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Obstacle ahead and no free lane to avoid it')) ]).

rule('Stop before obstacle when no lane is free (emergency)',
     465,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_occupancy(left, true, _),
       lane_occupancy(right, true, _),
       ego_speed(V),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_braking_excessive(V, Margin)) ],
     [ assert(proposal(emergency_brake, 0.0, high, collision_avoidance,
                       'Obstacle ahead and no free lane to avoid it')) ]).

rule('Stop before obstacle when no lane is free (brake)',
     465,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_occupancy(left, true, _),
       lane_occupancy(right, true, _),
       ego_speed(V),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       not(test(obstacle_braking_excessive(V, Margin))),
       test(obstacle_curve(Margin, Target)) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Obstacle ahead and no free lane to avoid it')) ]).

% Continuous line on the left and the right lane occupied (or the ego already in the right
% lane): there is nowhere to go legally.
rule('Stop before obstacle when lane change prohibited by continuous line (stop)',
     466,
     [ left_marking(continuous),
       ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       test(right_unavailable(Lane)),
       test(obstacle_margin(D, Margin)),
       test(Margin =< 0.5) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Continuous line prohibits lane change left; stopping before obstacle')) ]).

rule('Stop before obstacle when lane change prohibited by continuous line (brake)',
     466,
     [ left_marking(continuous),
       ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       test(right_unavailable(Lane)),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_curve(Margin, Target)) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Continuous line prohibits lane change left; stopping before obstacle')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Barreiras

% Closed barrier in the ego's lane: move to a lane Java verified safe.
rule('Avoid closed barrier by changing left when safe',
     650,
     [ ego_lane(right),
       lane_change_state(idle),
       adjacent_lane_safe(left, true),
       barrier(_, D, false, right),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_left, no_target, high, maneuver_safety,
                       'Closed barrier ahead; changing left to a verified clear lane')) ]).

rule('Avoid closed barrier by changing left from centre when safe',
     650,
     [ ego_lane(center),
       lane_change_state(idle),
       adjacent_lane_safe(left, true),
       barrier(_, D, false, center),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_left, no_target, high, maneuver_safety,
                       'Closed barrier ahead; changing left to a verified clear lane')) ]).

rule('Avoid closed barrier by changing right from centre when safe',
     650,
     [ ego_lane(center),
       lane_change_state(idle),
       adjacent_lane_safe(left, false),
       adjacent_lane_safe(right, true),
       barrier(_, D, false, center),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_right, no_target, high, maneuver_safety,
                       'Closed barrier ahead; changing right to a verified clear lane')) ]).

rule('Avoid closed barrier by changing right when safe',
     650,
     [ ego_lane(left),
       lane_change_state(idle),
       adjacent_lane_safe(right, true),
       barrier(_, D, false, left),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_right, no_target, high, maneuver_safety,
                       'Closed barrier ahead; changing right to a verified clear lane')) ]).

rule('Closed barrier requires stop',
     640,
     [ ego_lane(Lane),
       adjacent_lane_safe(left, false),
       adjacent_lane_safe(right, false),
       barrier(_, D, false, Lane),
       test((D >= 0, D =< 55.0)) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Closed barrier ahead and no adjacent lane is safe')) ]).

% Only when nothing at collision-avoidance priority or above has been proposed.
rule('Open barrier allows continue if no higher priority',
     170,
     [ barrier(_, D, true, _),
       test((D >= 0, D =< 35.0)),
       not(test(proposal_at_least(collision_avoidance))) ],
     [ assert(proposal(continue, 35.0, low, comfort_progress,
                       'Barrier open and crossing allowed')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Condições meteorológicas (weather.drl, temporal.drl)

% Rain: prudent speed of 65 km/h, or 55 km/h with low visibility or grip.
rule('Rain lowers recommended safe speed',
     340,
     [ weather(rain, Visibility, Grip),
       test(( ( Visibility < 0.75 ; Grip < 0.7 ) -> Target = 55.0 ; Target = 65.0 )) ],
     [ assert(proposal(reduce_speed, Target, moderate, speed_compliance,
                       'Rain and reduced grip require conservative speed')) ]).

% One proposal per reading in the last 500 ms; the slowest wins the arbitration.
rule('Low visibility requires stronger speed reduction',
     350,
     [ low_visibility_sample(Visibility, Age),
       test(Age < 500),
       test(Target is max(30.0, 75.0 * Visibility)) ],
     [ assert(proposal(reduce_speed, Target, high, road_hazard,
                       'Reduced visibility requires conservative speed')) ]).

rule('Low road grip requires stronger speed reduction',
     350,
     [ low_grip_sample(Grip, Age),
       test(Age < 500),
       test(Target is max(25.0, 80.0 * Grip)) ],
     [ assert(proposal(reduce_speed, Target, high, road_hazard,
                       'Reduced grip requires cautious speed')) ]).

% In rain, two TTC readings below 3 s within 2 s are enough to brake.
rule('Persistent low TTC in rain increases conservatism',
     560,
     [ weather(rain, _, _),
       test(persistent_low_ttc(3.0, Count)),
       test(Count >= 2) ],
     [ assert(proposal(brake, 15.0, high, collision_avoidance,
                       'Rain + persistent low TTC => conservative braking')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Sinal STOP

% The approach only starts above the 2.0 m/s2 curve that ends 2 m before the sign (the
% actuator's weakest deceleration), and holds until the line: so the rule does not switch
% off every time braking takes the vehicle below the curve. The action follows the
% comfortable stopping distance (2.8 m/s2): when it no longer stops before the line, brake
% hard, until there is 1 m to spare again. The recommended speed is the current one (within
% the limit), not the curve, which the actuator could not follow.
rule('Stop sign requires progressive deceleration (emergency)',
     610,
     [ stop_sign(S, D),
       not(stop_completed(S)),
       test((D > 4.0, D =< 125.0)),
       ego_speed(V),
       test(above_stop_curve(V, D)),
       test(hard_braking_for_stop(V, D)) ],
     [ assert(proposal(emergency_brake, 0.0, high, legal_mandatory_stop,
                       'Stop sign ahead; comfortable braking no longer stops in time')) ]).

rule('Stop sign requires progressive deceleration (brake)',
     610,
     [ stop_sign(S, D),
       not(stop_completed(S)),
       test((D > 4.0, D =< 125.0)),
       ego_speed(V),
       speed_limit(Limit),
       test(above_stop_curve(V, D)),
       not(test(hard_braking_for_stop(V, D))),
       test(stop_comfort_curve(D, Curve)),
       test(V > Curve),
       test(Target is min(V, Limit)) ],
     [ assert(proposal(reduce_speed, Target, moderate, legal_mandatory_stop,
                       'Approaching stop sign; decelerate to stop at the line')) ]).

rule('Stop sign requires progressive deceleration (hold speed)',
     610,
     [ stop_sign(S, D),
       not(stop_completed(S)),
       test((D > 4.0, D =< 125.0)),
       ego_speed(V),
       speed_limit(Limit),
       test(above_stop_curve(V, D)),
       not(test(hard_braking_for_stop(V, D))),
       test(stop_comfort_curve(D, Curve)),
       test(V =< Curve),
       test(Target is min(V, Limit)) ],
     [ assert(proposal(maintain_speed, Target, low, legal_mandatory_stop,
                       'Approaching stop sign; hold speed until the stop line')) ]).

% At the line the vehicle stops and stays stopped for 1.2 s. The count starts when the speed
% drops below 0.6 km/h; the start time is kept in the state between cycles.
rule('Start stop timer once vehicle is stopped',
     630,
     [ stop_sign(S, D),
       test(D =< 4.0),
       not(stop_completed(S)),
       not(stop_hold_started(S, _)),
       ego_speed(V),
       test(V < 0.6),
       time_ms(Now) ],
     [ assert(stop_hold_started(S, Now)) ]).

rule('Stop sign condition satisfied after full stop hold',
     635,
     [ stop_hold_started(S, Start),
       not(stop_completed(S)),
       time_ms(Now),
       test(Now - Start >= 1200) ],
     [ assert(stop_completed(S)) ]).

% While the stop has not lasted 1.2 s. The condition reads the time, not only
% stop_completed, so it does not depend on which of the two rules fires first.
rule('Stop sign requires full stop near line',
     620,
     [ stop_sign(S, D),
       test(D =< 4.0),
       not(stop_completed(S)),
       time_ms(Now),
       not(test(stop_hold_complete(S, Now))) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Stop sign reached; full stop required')) ]).

rule('Proceed after stop sign condition satisfied',
     200,
     [ stop_completed(S),
       stop_sign(S, D),
       test(D =< 4.0) ],
     [ assert(proposal(continue, 25.0, low, maneuver_safety,
                       'Stop completed; proceed when safe')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Velocidade

% Speed radar within 250 m: the limit configured on the radar applies to every lane. Above
% it, slow down; within it, stay at the lower of the two limits.
rule('Decelerate approaching speed radar to comply with legal limit',
     320,
     [ speed_radar(_, D, RadarLimit),
       test((D > 0, D =< 250.0)),
       ego_speed(V),
       test(V > RadarLimit + 0.1),
       test(radar_reason(RadarLimit, Reason)) ],
     [ assert(proposal(reduce_speed, RadarLimit, moderate, speed_compliance, Reason)) ]).

rule('Maintain speed at or below the configured radar limit while approaching',
     215,
     [ speed_radar(_, D, RadarLimit),
       test((D >= 0, D =< 250.0)),
       speed_limit(LegalLimit),
       ego_speed(V),
       test(V =< RadarLimit + 0.1),
       test(Target is min(RadarLimit, LegalLimit)) ],
     [ assert(proposal(maintain_speed, Target, low, speed_compliance,
                       'Radar speed-control zone ahead; maintaining speed at or below the effective limit')) ]).

% 0.1 km/h deadband, the same as the actuator's: "exceeds" and "complies" are exact
% complements.
rule('Reduce speed when exceeding detected limit',
     300,
     [ ego_speed(V),
       speed_limit(Limit),
       test(V > Limit + 0.1) ],
     [ assert(proposal(reduce_speed, Limit, moderate, speed_compliance,
                       'Vehicle exceeds current legal speed limit')) ]).

% A signed limit above the current speed is a target the vehicle may rise to. The default
% limit is not a sign and is never a target.
rule('Maintain speed when compliant with detected limit',
     210,
     [ signed_speed_limit(Limit),
       ego_speed(V),
       test(V =< Limit + 0.1) ],
     [ assert(proposal(maintain_speed, Limit, low, comfort_progress,
                       'Vehicle is compliant with legal limit')) ]).

rule('Build back up to the scenario cruise speed when no sign governs the road',
     208,
     [ no_speed_sign,
       cruise_speed(Cruise),
       speed_limit(Limit),
       ego_speed(V),
       test(Cruise > 0),
       test(Target is min(Cruise, Limit)),
       test(V < Target - 0.5),
       not(test(proposal_above(comfort_progress))) ],
     [ assert(proposal(maintain_speed, Target, low, speed_compliance,
                       'Road is clear; building back up to the scenario cruise speed')) ]).

% Driver acceleration request: accepted for 1.5 s, only when nothing above comfort has been
% proposed.
rule('Allow acceleration request when safe context',
     205,
     [ acceleration_request(Target, Age),
       test((Target > 0, Age < 1500)),
       not(test(proposal_above(comfort_progress))) ],
     [ assert(proposal(accelerate, Target, low, comfort_progress,
                       'Driver acceleration request accepted')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Arranque e decisão base
%
% Arranque: um veículo parado que nada retém (abriu-se uma faixa ao lado de um obstáculo, o
% semáforo ficou verde, o peão saiu) arranca suavemente até 30 km/h, ou até ao limite se
% este for menor. A intenção fica guardada no estado (move_off/2), para sobreviver a uma
% mudança de faixa a meio, e um carro que apenas abrandou nunca é acelerado.
%
% Começa parado quando a decisão anterior tinha, no máximo, a prioridade de manobra: isso é
% uma autorização para andar (o "avançar" de um STOP, uma mudança de faixa), não uma
% retenção. Termina quando um perigo ou uma retenção ganha um ciclo, ou a 1 km/h do alvo.
rule('Start moving off after a standstill',
     60,
     [ move_off_state(false, _),
       ego_speed(V),
       test(V < 3.0),
       lane_change_state(idle),
       time_ms(Now),
       test(previous_priority_at_most(maneuver_safety)) ],
     [ assert(move_off_started(Now)) ]).

rule('Stop moving off once held or at speed',
     60,
     [ move_off_state(true, _),
       ego_speed(V),
       speed_limit(Limit),
       test(( V >= min(30.0, Limit) - 1.0 ; \+ previous_priority_at_most(maneuver_safety) )) ],
     [ assert(move_off_ended) ]).

% At manoeuvre priority: any rule that still requires the stop wins; the slower "proceed"
% of a STOP wins the tie. It steps aside during a driver lane change request and while an
% overtake has not yet left its lane, so its speed does not win the tie against the lane
% change.
rule('Move off when stopped and nothing holds the vehicle',
     -90,
     [ test(moving_off),
       ego_lane(Lane),
       speed_limit(Limit),
       not(lane_change_request(_)),
       not(test(current_overtaking(true, Lane, false, _, _))),
       test(Target is min(30.0, Limit)) ],
     [ assert(proposal(continue, Target, low, maneuver_safety,
                       'Vehicle was stopped and no rule holds it any more')) ]).

rule('Default maintain speed baseline',
     -100,
     [ ego_speed(V),
       not(proposal(_, _, _, _, _)) ],
     [ assert(proposal(maintain_speed, V, low, comfort_progress,
                       'No restrictive condition detected')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Predicados auxiliares (chamados a partir de test/1)

% Distance needed to stop from V km/h (StoppingPolicy.requiredDistanceMeters): reaction time
% + braking + stop line offset.
stopping_distance(V, Kind, D) :-
    deceleration(Kind, A),
    param(reactionTimeSeconds, Reaction),
    param(redLightStopLineOffsetMeters, Offset),
    Vms is max(0.0, V) / 3.6,
    D is Vms * Reaction + Vms * Vms / (2.0 * A) + Offset.

braking_distance(V, Kind, D) :-
    deceleration(Kind, A),
    Vms is max(0.0, V) / 3.6,
    D is Vms * Vms / (2.0 * A).

deceleration(comfort, A) :- param(comfortDecelerationMps2, A).
deceleration(anticipation, A) :- param(anticipationDecelerationMps2, A).

% Still before the line (with tolerance), within 125 m, and already inside the distance the
% anticipation deceleration needs.
red_light_approach(D, V) :-
    D =< 125.0,
    \+ at_stop_line(D),
    stopping_distance(V, anticipation, Needed),
    D =< Needed.

at_stop_line(D) :-
    param(redLightStopLineOffsetMeters, Offset),
    param(stopLineToleranceMeters, Tolerance),
    D =< Offset + Tolerance.

% Metres a comfortable stop lacks to stop at the line.
braking_shortfall(V, D, Shortfall) :-
    param(redLightStopLineOffsetMeters, Offset),
    braking_distance(V, comfort, Braking),
    Shortfall is Braking - (D - Offset).

% Before the line: a comfortable stop lacks more than 2 m. Past the line: a comfortable stop
% does not fit before the light (less the tolerance).
critical_stop_at_light(V, D) :-
    param(redLightStopLineOffsetMeters, Offset),
    param(stopLineToleranceMeters, Tolerance),
    braking_distance(V, comfort, Braking),
    ToLine is D - Offset,
    (   ToLine > 0.0
    ->  Braking - ToLine > 2.0
    ;   Braking > max(0.0, D - Tolerance)
    ).

% The braking curve at Deceleration m/s2 that ends 18 m before the pedestrian.
above_pedestrian_curve(V, D, Deceleration) :-
    V / 3.6 > sqrt(2.0 * Deceleration * max(0.0, D - 18.0)).

% The front vehicle's speed (m/s), from the ego's and the relative one.
front_vehicle_speed(V, Rel, Front) :-
    Front is max(0.0, V / 3.6 - Rel).

front_vehicle_stopped(V, Rel) :-
    front_vehicle_speed(V, Rel, Front),
    Front < 0.5.

% Speed (km/h) from which 2.0 m/s2 matches the front vehicle's speed 12 m behind it.
following_speed(V, D, Rel, Target) :-
    deceleration(anticipation, A),
    front_vehicle_speed(V, Rel, Front),
    Target is sqrt(Front * Front + 2.0 * A * max(0.0, D - 12.0)) * 3.6.

% Closing in above the following speed, or stopped (or nearly) behind a stopped vehicle
% within 14 m.
unsafe_following(V, D, Rel) :-
    (   Rel > 0.3,
        following_speed(V, D, Rel, Target),
        V > Target
    ->  true
    ;   D =< 14.0,
        Rel > -0.3,
        front_vehicle_stopped(V, Rel)
    ).

front_distance_window(Target, Count, Minimum, Maximum) :-
    front_distance_window_ms(Window),
    findall(D, ( known(front_distance_sample(Target, D, Age)), D > 0, Age < Window ), Ds),
    length(Ds, Count),
    Count > 0,
    min_list(Ds, Minimum),
    max_list(Ds, Maximum).

% The overtaking state after the changes concluded in this cycle. An overtake that starts
% now prevails; otherwise cancelled or finished deactivates; passed marks the target.
current_overtaking(Active, Origin, Passed, Start, Target) :-
    effective_overtaking(Active0, Origin0, Passed0, Start0, Target0),
    Active = Active0, Origin = Origin0, Passed = Passed0, Start = Start0, Target = Target0.

effective_overtaking(true, Origin, false, Start, Target) :-
    known(overtake_started(Target, Origin, Start)), !.
effective_overtaking(false, Origin, false, Start, none) :-
    (   known(overtake_cancelled) ; known(overtake_finished) ), !,
    known(overtaking(_, Origin, _, Start, _)).
effective_overtaking(Active, Origin, true, Start, Target) :-
    known(overtake_target_passed), !,
    known(overtaking(Active, Origin, _, Start, Target)).
effective_overtaking(Active, Origin, Passed, Start, Target) :-
    known(overtaking(Active, Origin, Passed, Start, Target)).

% May start overtaking Target from Lane: no overtake active, or another target, or (only for
% the front vehicle) another origin lane.
may_start_overtaking(Target, Lane) :-
    current_overtaking(Active, Origin, _, _, Followed),
    (   Active == false -> true ; Target \== Followed -> true ; Lane \== Origin ).

may_start_overtaking(Target) :-
    current_overtaking(Active, _, _, _, Followed),
    (   Active == false -> true ; Target \== Followed ).

light_forbids_overtaking :-
    known(traffic_light(_, State, D)),
    State \== green,
    D =< 125.0, !.

front_vehicle_within(Distance) :-
    known(front_vehicle(_, D, _)),
    D =< Distance, !.

% The speed the ego wants: the signed limit, or with no sign the cruise speed.
desired_speed(Desired) :-
    known(speed_limit(Limit)),
    (   known(no_speed_sign),
        known(cruise_speed(Cruise)),
        Cruise > 0
    ->  Desired is min(Cruise, Limit)
    ;   Desired = Limit
    ).

% The left-lane vehicle is moving (1.4 m/s or more) and the ego is close to it or above
% the curve.
must_not_pass_on_right(V, D, Rel) :-
    Left is V / 3.6 - Rel,
    Left >= 1.4,
    (   D =< 37.0
    ->  true
    ;   left_vehicle_curve(V, D, Rel, Curve),
        V > Curve
    ).

% Speed (km/h) that matches the left-lane vehicle's speed 35 m behind it, at 2.0 m/s2.
left_vehicle_curve(V, D, Rel, Curve) :-
    deceleration(anticipation, A),
    Left is V / 3.6 - Rel,
    Curve is sqrt(Left * Left + 2.0 * A * max(0.0, D - 35.0)) * 3.6.

% No free lane to move into, seen from the ego's lane.
no_free_adjacent_lane(center) :-
    known(lane_occupancy(left, true, _)),
    known(lane_occupancy(right, true, _)).
no_free_adjacent_lane(left) :-
    known(lane_occupancy(right, true, _)).
no_free_adjacent_lane(right) :-
    known(lane_occupancy(left, true, _)).

right_unavailable(right) :- !.
right_unavailable(_) :-
    known(lane_occupancy(right, true, _)).

% Metres to the stopping point, 10 m before the obstacle.
obstacle_margin(D, Margin) :-
    Margin is max(0.0, D - 10.0).

% The deceleration needed to stop within the margin exceeds comfortable braking by 10%.
obstacle_braking_excessive(V, Margin) :-
    deceleration(comfort, A),
    Vms is V / 3.6,
    Vms * Vms / (2.0 * Margin) > A * 1.1.

% Speed (km/h) on the comfortable braking curve that stops within the margin.
obstacle_curve(Margin, Target) :-
    deceleration(comfort, A),
    Target is sqrt(2.0 * A * Margin) * 3.6.

radar_reason(Limit, Reason) :-
    Rounded is round(Limit),
    format(atom(Reason), 'Radar detects speed-control zone ahead; configured limit ~w km/h', [Rounded]).

% STOP: braking curves that end 2 m before the sign.
above_stop_curve(V, D) :-
    deceleration(anticipation, A),
    V / 3.6 > sqrt(2.0 * A * max(0.0, D - 2.0)).

% Speed (km/h) on the comfortable braking curve D metres from the sign.
stop_comfort_curve(D, Curve) :-
    deceleration(comfort, A),
    Curve is sqrt(2.0 * A * max(0.0, D - 2.0)) * 3.6.

% Metres a comfortable stop would still leave to do past the 2 m margin.
stop_shortfall(V, D, Shortfall) :-
    braking_distance(V, comfort, Braking),
    Shortfall is Braking - max(0.0, D - 2.0).

% Brake hard: a comfortable stop ends more than 1 m past the line, or hard braking for a
% STOP was already under way and there is not yet 1 m to spare.
hard_braking_for_stop(V, D) :-
    stop_shortfall(V, D, Shortfall),
    (   Shortfall > 3.0
    ->  true
    ;   Shortfall > 1.0,
        known(previous_decision(emergency_brake, Rule)),
        sub_atom(Rule, 0, _, _, 'Stop sign')
    ).

stop_hold_complete(S, Now) :-
    known(stop_hold_started(S, Start)),
    Now - Start >= 1200.

pedestrian_reason(P, 'Pedestrian ahead in path; prepare stop') :-
    known(pedestrian_in_path(P)), !.
pedestrian_reason(_, 'Pedestrian still crossing the road ahead; giving way').

% Aggregation over the 2 s window (Drools accumulate).
persistent_critical_ttc(Count, Minimum) :-
    ttc_window_ms(Window),
    findall(T, ( known(ttc_sample(_, T, Age)), T > 0.0, T < 2.5, Age < Window ), Ts),
    length(Ts, Count),
    Count > 0,
    min_list(Ts, Minimum).

% TTC readings between 0 and Threshold s in the 2 s window.
persistent_low_ttc(Threshold, Count) :-
    ttc_window_ms(Window),
    aggregate_all(count,
                  ( known(ttc_sample(_, T, Age)), T > 0.0, T < Threshold, Age < Window ),
                  Count).

% The previous cycle's decision had at most the priority Name.
previous_priority_at_most(Name) :-
    priority_value(Name, Threshold),
    known(previous_priority(P)),
    P =< Threshold.

% Moving off is active after this cycle's state rules.
moving_off :-
    known(move_off_started(_)), !.
moving_off :-
    \+ known(move_off_ended),
    known(move_off_state(true, _)).

proposal_at_least(Name) :-
    priority_value(Name, Threshold),
    known(proposal(_, _, _, P, _)),
    priority_value(P, Value),
    Value >= Threshold, !.

proposal_above(Name) :-
    priority_value(Name, Threshold),
    known(proposal(_, _, _, P, _)),
    priority_value(P, Value),
    Value > Threshold, !.
