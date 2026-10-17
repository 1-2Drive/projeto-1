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
%   ego_position(M)   a posição do ego na estrada, em metros percorridos
%   speed_limit(Kmh)  signed_speed_limit(Kmh) | no_speed_sign  cruise_speed(Kmh)
%   pending_speed_limit(Kmh, SignPositionM)   sinal lido à frente, ainda não alcançado
%   traffic_light(Id, red|yellow|green, DistanceM)  light_commitment(Id, stop|proceed)
%   pedestrian(Id, DistanceM)  crossing_state(Id, waiting|crossing|crossed)  pedestrian_in_path(Id)
%   ttc_sample(Target, TtcS, AgeMs)  rain_sample(AgeMs)   uma leitura de chuva do sensor de ambiente
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
%   far_left_lane_vehicle(Target, DistanceM, RelSpeedMps)   duas faixas à esquerda (LEFT, visto de RIGHT)
%   lane_occupancy(left|right, Occupied, FrontDistanceM)   o lado do ego, não uma faixa
%   lane_occupancy_read(left|right, Ms)   quando esse lado foi lido (LaneOccupancyState.timestamp)
%   lane_settled_at(Ms)   quando a última mudança de faixa assentou (laneSettledAtMillis), -1 se nenhuma
%   lane_safe_to_enter(left|right, true|false)   o safeToEnter do sensor de ângulo morto
%   left_marking(dashed|continuous)  right_marking(dashed|continuous)
%   adjacent_lane_safe(left|right, true|false)   calculado pelo Java (WorldState.isLaneSafe)
%   overtaking(Active, OriginLane, TargetPassed, StartMs, Target)   estado do ciclo anterior
%   obstacle(Id, DistanceM, Severity, Lane)  obstacle_sample(Id, DistanceM, Severity, Lane, AgeMs)
%   pothole(Id, DistanceM, Severity, Lane)  barrier(Id, DistanceM, Open, Lane)
%   weather(clear|rain|..., Visibility, Grip)   o estado persistente (último WeatherEvent)

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
% As regras que abrandam atrás do veículo da frente nunca se afastam por uma ultrapassagem:
% a mudança de faixa é arbitrada no seu próprio canal (lateral) e não compete com as
% propostas de velocidade, por isso a ultrapassagem pode começar no mesmo ciclo em que o
% veículo continua a abrandar atrás do da frente, e isso mantém-se até ter saído da faixa.

% Too close and still closing: brake to 3 km/h below the front vehicle's speed, so the gap
% reopens instead of merely holding the closing speed.
rule('Brake when front vehicle distance low',
     520,
     [ front_vehicle(_, D, Rel),
       ego_speed(V),
       test((D > 0, D < 14.0, Rel > 0)),
       test(Target is max(0.0, (V / 3.6 - Rel) * 3.6 - 3.0)) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Front vehicle too close')) ]).

% Art. 18 and 24 CE: the speed from which 2.0 m/s2 still matches the front vehicle's speed
% 12 m behind it. Only proposes above that speed, because a recommendation above the current
% speed would make the actuator accelerate. Behind a stopped vehicle, keeps the stop until
% 2 m past the stopping point, so radar noise does not release it. Inside the 12 m gap behind
% a moving vehicle, and not opening the gap fast, the curve says nothing (no room left): the
% vehicle then drives 3 km/h slower than the vehicle ahead until the gap has reopened.
rule('Keep safe following distance behind front vehicle (stop)',
     510,
     [ front_vehicle(_, D, Rel),
       ego_speed(V),
       test(D > 0),
       test(unsafe_following(V, D, Rel)),
       test(front_vehicle_stopped(V, Rel)),
       test(D - 12.0 =< 2.0) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Stopped behind a stationary front vehicle (Art. 18 CE)')) ]).

rule('Keep safe following distance behind front vehicle (reopen gap)',
     510,
     [ front_vehicle(_, D, Rel),
       ego_speed(V),
       test(D > 0),
       test(reopening_gap(V, D, Rel)),
       test(front_vehicle_speed(V, Rel, Front)),
       test(Target is max(0.0, Front * 3.6 - 3.0)) ],
     [ assert(proposal(reduce_speed, Target, moderate, collision_avoidance,
                       'Reopening the following gap behind the front vehicle (Art. 18 CE)')) ]).

rule('Keep safe following distance behind front vehicle (reduce)',
     510,
     [ front_vehicle(_, D, Rel),
       ego_speed(V),
       test(D > 0),
       test(unsafe_following(V, D, Rel)),
       not(test(( front_vehicle_stopped(V, Rel), D - 12.0 =< 2.0 ))),
       not(test(reopening_gap(V, D, Rel))),
       test(following_speed(V, D, Rel, Target)) ],
     [ assert(proposal(reduce_speed, Target, moderate, collision_avoidance,
                       'Keeping a safe distance to the front vehicle (Art. 18/24 CE)')) ]).

rule('Reduce speed when closing front vehicle',
     500,
     [ front_vehicle(_, D, Rel),
       test((D >= 14.0, D < 35.0, Rel > 0.8)),
       test(Target is max(20.0, D * 1.2)) ],
     [ assert(proposal(reduce_speed, Target, moderate, collision_avoidance,
                       'Closing to front vehicle')) ]).

% Trend over the 2 s window, only with the distances of the vehicle followed now: when the
% radar switches to another vehicle, the jump in distance between the two is no approach.
rule('Approaching trend from sliding distance window',
     505,
     [ front_vehicle(Target, D, _),
       test((D > 0, D < 50.0)),
       lane_change_state(idle),
       test(front_distance_window(Target, Count, Minimum, Maximum)),
       test(Count >= 3),
       test(Minimum < Maximum - 4.0) ],
     [ assert(proposal(reduce_speed, 35.0, moderate, collision_avoidance,
                       'Front distance trend decreasing quickly')) ]).

% Following a moving vehicle at its speed, just outside the 12 m gap, where no rule above
% holds unless the ego is closing in: the cruise speed would otherwise drive it at the
% vehicle ahead and the brake (below 14 m) would take over again, in a loop. Closer than
% 16 m, 1 km/h less, so the gap settles clear of the brake's 14 m, beyond radar noise. The
% window reaches 20 m, divided by the grip on low grip (at most twice).
rule('Follow the front vehicle speed outside the following gap',
     508,
     [ front_vehicle(_, D, Rel),
       ego_speed(V),
       weather(_, _, Grip),
       test(D > 12.0),
       test(front_vehicle_speed(V, Rel, Front)),
       test(Front >= 0.5),
       test(D =< 20.0 / max(0.5, min(1.0, Grip))),
       test(( FrontKmh is Front * 3.6,
              ( D < 16.0 -> Target is max(0.0, FrontKmh - 1.0) ; Target = FrontKmh ) )) ],
     [ assert(proposal(reduce_speed, Target, moderate, collision_avoidance,
                       'Following the front vehicle at its speed (Art. 18 CE)')) ]).

% Below the 35 km/h the trend rule asks for, that request would make the actuator
% accelerate towards the vehicle being approached: while really closing in, hold the speed.
rule('Hold speed on an approaching trend below its target',
     504,
     [ front_vehicle(Target, D, Rel),
       test((D > 0, D < 50.0, Rel > 0)),
       lane_change_state(idle),
       ego_speed(V),
       test(V < 35.0),
       test(front_distance_window(Target, Count, Minimum, Maximum)),
       test(Count >= 3),
       test(Minimum < Maximum - 4.0) ],
     [ assert(proposal(reduce_speed, V, moderate, collision_avoidance,
                       'Front distance trend decreasing; not accelerating towards it')) ]).

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
       test(fresh_side_reading(right)),
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
% the move with that whole margin. Moving left, from CENTER or from RIGHT, is only worth it
% if the next vehicle in that lane is clearly further away. Art. 41 CE: never with a pedestrian in
% the way or a red or yellow light ahead.
rule('Overtake slow front vehicle via left lane',
     525,
     [ ego_lane(Lane),
       test(memberchk(Lane, [right, center])),
       lane_change_state(idle),
       prove(no_continuous_line(left)),
       ego_speed(V),
       time_ms(Now),
       front_vehicle(Target, D, Rel),
       front_read_now(Target),
       test((D > 15.0, Rel > 0.5)),
       lane_occupancy(left, false, LeftFront),
       not(pedestrian_conflict(_)),
       not(test(light_forbids_overtaking)),
       test(may_start_overtaking(Target, Lane)),
       test(LeftFront > D + 20.0),
       test(D =< max(50.0, Rel * 5.5 + 10.0)),
       test((V / 3.6 - Rel >= 1.0 ; D >= Rel * 5.5)) ],
     [ assert(overtake_started(Target, Lane, Now)),
       assert(proposal(change_lane_left, no_target, low, maneuver_safety,
                       'Overtaking slower vehicle via clear left lane (Art. 35/36 CE)')) ]).

% The overtake above needs the ego to be closing in, which never holds once the following
% rule has matched it to the front vehicle's speed. This is the exit from following: at the
% front vehicle's speed (within 0.5 m/s either way), in the following window (6-30 m) and
% with that vehicle at least 10 km/h below the speed the ego aims for (the posted limit, or
% the scenario cruise speed), it moves left when the left lane is clear. Same lane, line,
% lane-ahead and legal (Art. 41 CE) conditions as the overtake.
rule('Overtake the vehicle being followed',
     524,
     [ ego_lane(Lane),
       test(memberchk(Lane, [right, center])),
       lane_change_state(idle),
       prove(no_continuous_line(left)),
       ego_speed(V),
       time_ms(Now),
       front_vehicle(Target, D, Rel),
       front_read_now(Target),
       test((D > 6.0, D =< 30.0, Rel >= -0.5, Rel =< 0.5)),
       lane_occupancy(left, false, LeftFront),
       not(pedestrian_conflict(_)),
       not(test(light_forbids_overtaking)),
       test(may_start_overtaking(Target, Lane)),
       test(LeftFront > D + 20.0),
       test(desired_speed(Desired)),
       test((V / 3.6 - Rel) * 3.6 =< Desired - 10.0) ],
     [ assert(overtake_started(Target, Lane, Now)),
       assert(proposal(change_lane_left, no_target, low, maneuver_safety,
                       'Overtaking the slower vehicle being followed via clear left lane (Art. 35/36 CE)')) ]).

% Art. 36 CE: a slower vehicle in the left lane is not passed on its right. From the right
% lane, the ego first moves in behind it (RIGHT -> CENTER) and that vehicle becomes the
% target; once behind it, the previous rule continues CENTER -> LEFT. Art. 38 CE: only with
% LEFT clear well beyond that vehicle (+20 m, as the overtake asks); otherwise the ego stays
% in RIGHT behind it.
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
       test(D =< max(50.0, Rel * 5.5 + 10.0)),
       test(far_left_clear_beyond(D)) ],
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
       prove(no_continuous_line(left)),
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
       prove(no_continuous_line(right)),
       lane_occupancy(right, false, RightFront),
       test(RightFront > 80.0),
       test(fresh_side_reading(right)),
       not(lane_change_request(_)) ],
     [ assert(proposal(return_right, no_target, low, maneuver_safety,
                       'Portuguese Highway Code Art. 38: return to rightmost lane after overtaking')) ]).

% Slow traffic in every lane the ego could use to overtake (front-vehicle.drl, the same
% rules). boxed_in(Lane, Since) comes from the previous cycle (driving_state.pl); the rules
% conclude boxed_in_started/2 or boxed_in_cleared and current_boxed_in/1 reads the result.
% Boxed in: behind a vehicle at least 10 km/h below the speed the ego is aiming for, and
% unable to move left (road edge, continuous line, occupied, or no clearer than its own
% lane by 20 m). After 3 s the ego falls back, returns right with the right lane clear
% alongside and ahead (2-second rule, 3 s in rain or on low grip, at least 30 m) but with
% traffic there within 80 m (into an empty right lane it would pass the slow vehicle on its
% right, Art. 36 CE), and does not start the same overtake again until the way ahead or to
% the left opens.
rule('Note the vehicle is boxed in behind slow traffic',
     536,
     [ ego_lane(Lane),
       test(Lane \== right),
       lane_change_state(idle),
       left_marking(LeftMarking),
       ego_speed(V),
       time_ms(Now),
       front_vehicle(_, D, Rel),
       test((D > 0, D =< 30.0, Rel >= -0.5)),
       lane_occupancy(left, LeftOccupied, LeftFront),
       not(boxed_in(_, _)),
       test(front_vehicle_speed(V, Rel, Front)),
       test(Front >= 0.5),
       test(desired_speed(Desired)),
       test(Front * 3.6 =< Desired - 10.0),
       test(( Lane == left ; LeftMarking == continuous ; LeftOccupied == true ; LeftFront =< D + 20.0 )) ],
     [ assert(boxed_in_started(Lane, Now)) ]).

rule('Forget boxed in once nothing is ahead',
     535,
     [ boxed_in(_, _),
       lane_change_state(idle),
       not(test(front_vehicle_within(80.0))) ],
     [ assert(boxed_in_cleared) ]).

rule('Forget boxed in once the left lane opens',
     535,
     [ boxed_in(_, _),
       ego_lane(Lane),
       test(Lane \== left),
       lane_change_state(idle),
       prove(no_continuous_line(left)),
       front_vehicle(_, D, _),
       test((D > 0, D =< 80.0)),
       lane_occupancy(left, false, LeftFront),
       test(LeftFront > D + 20.0) ],
     [ assert(boxed_in_cleared) ]).

rule('Fall back behind slow traffic to return right',
     507,
     [ test(current_boxed_in(Since)),
       ego_lane(Lane),
       test(Lane \== right),
       lane_change_state(idle),
       ego_speed(V),
       time_ms(Now),
       test(Now - Since >= 3000),
       lane_occupancy(right, true, _),
       front_vehicle(_, D, Rel),
       test((D > 0, D =< 30.0)),
       test(front_vehicle_speed(V, Rel, Front)),
       test(Front >= 0.5),
       test(Target is max(0.0, Front * 3.6 - 3.0)) ],
     [ assert(proposal(reduce_speed, Target, moderate, collision_avoidance,
                       'Overtaking impossible: falling back to return to the right lane (Art. 13 CE)')) ]).

rule('Return right when boxed in and the right lane is clear',
     470,
     [ test(current_boxed_in(Since)),
       ego_lane(Lane),
       test(Lane \== right),
       lane_change_state(idle),
       prove(no_continuous_line(right)),
       ego_speed(V),
       time_ms(Now),
       test(Now - Since >= 3000),
       lane_occupancy(right, false, RightFront),
       test(fresh_side_reading(right)),
       weather(Condition, _, Grip),
       not(lane_change_request(_)),
       test(( ( Condition == rain ; Grip < 0.85 ) -> Seconds = 3.0 ; Seconds = 2.0 )),
       test(RightFront =< 80.0),
       test(RightFront >= max(30.0, V / 3.6 * Seconds)) ],
     [ assert(proposal(return_right, no_target, low, maneuver_safety,
                       'Portuguese Highway Code Art. 13: overtaking impossible, returning to the right lane')) ]).

rule('Keep the right lane while the lanes ahead stay blocked',
     527,
     [ test(current_boxed_in(_)),
       ego_lane(right),
       lane_change_state(idle),
       not(lane_change_request(_)),
       not(obstacle(_, _, _, _)),
       not(pothole(_, _, _, _)),
       not(barrier(_, _, false, _)),
       not(pedestrian_conflict(_)) ],
     [ assert(proposal(keep_lane, no_target, low, collision_avoidance,
                       'Slow traffic in every lane ahead: no overtake (Art. 13/35 CE)')) ]).

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
       prove(no_continuous_line(right)),
       not(right_lane_blocked_ahead),
       time_ms(Now),
       test(Now >= 4000),
       lane_occupancy(right, false, RightFront),
       test(RightFront > 80.0),
       test(fresh_side_reading(right)),
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
     [ ego_speed(V),
       left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V > Curve) ],
     [ assert(proposal(reduce_speed, Curve, moderate, speed_compliance,
                       'Slower vehicle in the left lane must not be passed on its right (Art. 36 CE)')) ]).

rule('Do not pass a slower left lane vehicle on its right (fall back)',
     508,
     [ ego_speed(V),
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
     [ ego_speed(V),
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
       pothole(_, D, _, center),
       test((D > 0, D =< 50.0)),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear right lane (rightmost safe lane policy)')) ]).

rule('Avoid pothole in centre lane by changing left when right is occupied',
     470,
     [ ego_lane(center),
       pothole(_, D, _, center),
       test((D > 0, D =< 50.0)),
       lane_occupancy(right, true, _),
       prove(safe_to_change_lane(left)) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear left lane')) ]).

% Every lane change checks the line it crosses: between RIGHT and CENTER the right marking,
% between CENTER and LEFT the left marking; a continuous line is never crossed. The avoidance
% rules prove it, with the idle maneuver and the clear side, through safe_to_change_lane/1.
rule('Avoid pothole in left lane by changing right',
     470,
     [ ego_lane(left),
       pothole(_, D, _, left),
       test((D > 0, D =< 50.0)),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear right lane')) ]).

rule('Avoid pothole in right lane by changing left',
     470,
     [ ego_lane(right),
       pothole(_, D, _, right),
       test((D > 0, D =< 50.0)),
       prove(safe_to_change_lane(left)) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear left lane')) ]).

% Obstacle: from the centre, prefer the left; right only with the left occupied. An obstacle
% blocks the lane, so going around it is collision avoidance.
rule('Avoid obstacle in centre lane by changing left',
     470,
     [ ego_lane(center),
       obstacle(_, D, _, center),
       test((D > 0, D =< 50.0)),
       prove(safe_to_change_lane(left)) ],
     [ assert(proposal(change_lane_left, no_target, low, collision_avoidance,
                       'Avoiding obstacle by moving into the clear left lane')) ]).

rule('Avoid obstacle in centre lane by changing right when left is occupied',
     470,
     [ ego_lane(center),
       obstacle(_, D, _, center),
       test((D > 0, D =< 50.0)),
       lane_occupancy(left, true, _),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, collision_avoidance,
                       'Avoiding obstacle by moving into the clear right lane')) ]).

rule('Avoid obstacle in left lane by changing right',
     470,
     [ ego_lane(left),
       obstacle(_, D, _, left),
       test((D > 0, D =< 50.0)),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, collision_avoidance,
                       'Avoiding obstacle by moving into the clear right lane')) ]).

rule('Avoid obstacle in right lane by changing left',
     470,
     [ ego_lane(right),
       obstacle(_, D, _, right),
       test((D > 0, D =< 50.0)),
       prove(safe_to_change_lane(left)) ],
     [ assert(proposal(change_lane_left, no_target, low, collision_avoidance,
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
       prove(safe_to_change_lane(left)) ],
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
       prove(safe_to_change_lane(right)) ],
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

% Obstacle in the ego's lane while the vehicle has not started to move out of that lane
% (no side free, avoidance vetoed by a mandatory stop, or refused by the actuator): stop
% 10 m before it (outside the 8 m emergency zone). The curve only binds when the vehicle
% is too fast to stop there, so an avoidance that starts in time is not slowed down. It follows the
% comfortable braking curve; if the deceleration needed exceeds the comfortable one by 10%,
% it brakes hard.
rule('Stop before obstacle ahead while still in its lane (stop)',
     465,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_change_state(idle),
       test(obstacle_margin(D, Margin)),
       test(Margin =< 0.5) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Obstacle ahead in own lane; stopping unless a lane change takes the vehicle out of its way')) ]).

rule('Stop before obstacle ahead while still in its lane (emergency)',
     465,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_change_state(idle),
       ego_speed(V),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_braking_excessive(V, Margin)) ],
     [ assert(proposal(emergency_brake, 0.0, high, collision_avoidance,
                       'Obstacle ahead in own lane; stopping unless a lane change takes the vehicle out of its way')) ]).

rule('Stop before obstacle ahead while still in its lane (brake)',
     465,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_change_state(idle),
       ego_speed(V),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       not(test(obstacle_braking_excessive(V, Margin))),
       test(obstacle_curve(Margin, Target)) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Obstacle ahead in own lane; stopping unless a lane change takes the vehicle out of its way')) ]).

% Neither side is safe to enter (the blind-spot occupancy already reports a side as not safe
% when the line towards it is continuous, WorldState.sideLaneOccupancy) and the ego's lane
% is closed by a continuous line (the left marking lies between LEFT and CENTER, the right
% marking between CENTER and RIGHT): there is nowhere to go legally.
rule('Stop before obstacle when lane change prohibited by continuous line (stop)',
     466,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_safe_to_enter(left, false),
       lane_safe_to_enter(right, false),
       test(continuous_line_closes_lane(Lane)),
       test(obstacle_margin(D, Margin)),
       test(Margin =< 0.5) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Continuous line prohibits lane change; stopping before obstacle')) ]).

rule('Stop before obstacle when lane change prohibited by continuous line (brake)',
     466,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       test((D > 0, D =< 60.0)),
       lane_safe_to_enter(left, false),
       lane_safe_to_enter(right, false),
       test(continuous_line_closes_lane(Lane)),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_curve(Margin, Target)) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Continuous line prohibits lane change; stopping before obstacle')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Barreiras

% Closed barrier in the ego's lane: move to a lane the blind-spot sensor reports clear,
% proved by safe_to_change_lane/1 (Art. 35 and 38 CE). The barrier blocks the lane, so going
% around it is collision avoidance. No maneuver moving and no continuous line on the line it
% crosses (RIGHT-CENTER: the right marking; CENTER-LEFT: the left marking); otherwise the
% vehicle stops.
rule('Avoid closed barrier by changing left when safe',
     650,
     [ ego_lane(right),
       prove(safe_to_change_lane(left)),
       barrier(_, D, false, right),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_left, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing left to a verified clear lane')) ]).

rule('Avoid closed barrier by changing left from centre when safe',
     650,
     [ ego_lane(center),
       prove(safe_to_change_lane(left)),
       barrier(_, D, false, center),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_left, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing left to a verified clear lane')) ]).

rule('Avoid closed barrier by changing right from centre when safe',
     650,
     [ ego_lane(center),
       lane_occupancy(left, true, _),
       prove(safe_to_change_lane(right)),
       barrier(_, D, false, center),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_right, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing right to a verified clear lane')) ]).

rule('Avoid closed barrier by changing right when safe',
     650,
     [ ego_lane(left),
       prove(safe_to_change_lane(right)),
       barrier(_, D, false, left),
       test((D > 0, D =< 55.0)) ],
     [ assert(proposal(change_lane_right, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing right to a verified clear lane')) ]).

rule('Closed barrier requires stop',
     640,
     [ ego_lane(Lane),
       lane_occupancy(left, true, _),
       lane_occupancy(right, true, _),
       barrier(_, D, false, Lane),
       test((D >= 0, D =< 55.0)) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Closed barrier ahead and no adjacent lane is safe')) ]).

% Comfort proposal: it loses to any speed ceiling, hold or manoeuvre speed in the cycle, so
% it needs no guard on what else was proposed.
rule('Open barrier allows continue if no higher priority',
     170,
     [ barrier(_, D, true, _),
       test((D >= 0, D =< 35.0)) ],
     [ assert(proposal(continue, 35.0, low, comfort_progress,
                       'Barrier open and crossing allowed')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Condições meteorológicas: lógica difusa (fuzzy-weather.drl) e temporal.drl

% Fuzzy weather controller (Mamdani), the same as fuzzy-weather.drl. Visibility and grip are
% vague, so each reading belongs, to some degree, to the terms poor / reduced / fair / good:
%   1. fuzzification   (360): weather/3 -> fuzzy_membership(Variable, Term, Degree)
%   2. inference       (355): one rule per fuzzy rule, AND = min, OR = max
%                             -> fuzzy_conclusion(Term, Degree)
%   3. composition and 4. defuzzification (350): the conclusions are joined (max) and their
%                             centroid is the recommended speed.
% The result is one speed ceiling at road_hazard, proposed only when a restrictive term
% holds. The sets and the centroid sampling are those of FuzzySupport.java, so both engines
% compute the same speed.

rule('Fuzzify visibility',
     360,
     [ weather(_, Visibility, _),
       test(fuzzy_input_degrees(Visibility, Poor, Reduced, Fair, Good)) ],
     [ assert(fuzzy_membership(visibility, poor, Poor)),
       assert(fuzzy_membership(visibility, reduced, Reduced)),
       assert(fuzzy_membership(visibility, fair, Fair)),
       assert(fuzzy_membership(visibility, good, Good)) ]).

rule('Fuzzify grip',
     360,
     [ weather(_, _, Grip),
       test(fuzzy_input_degrees(Grip, Poor, Reduced, Fair, Good)) ],
     [ assert(fuzzy_membership(grip, poor, Poor)),
       assert(fuzzy_membership(grip, reduced, Reduced)),
       assert(fuzzy_membership(grip, fair, Fair)),
       assert(fuzzy_membership(grip, good, Good)) ]).

rule('Fuzzy: poor visibility or poor grip -> very slow',
     355,
     [ fuzzy_membership(visibility, poor, V),
       fuzzy_membership(grip, poor, G),
       test(( D is max(V, G), D > 0.0 )) ],
     [ assert(fuzzy_conclusion(very_slow, D)) ]).

rule('Fuzzy: reduced visibility or reduced grip -> slow',
     355,
     [ fuzzy_membership(visibility, reduced, V),
       fuzzy_membership(grip, reduced, G),
       test(( D is max(V, G), D > 0.0 )) ],
     [ assert(fuzzy_conclusion(slow, D)) ]).

rule('Fuzzy: fair visibility or fair grip -> moderate',
     355,
     [ fuzzy_membership(visibility, fair, V),
       fuzzy_membership(grip, fair, G),
       test(( D is max(V, G), D > 0.0 )) ],
     [ assert(fuzzy_conclusion(moderate, D)) ]).

% Rain is crisp (rain or clear): it enters the rule with degree 1 or 0.
rule('Fuzzy: rain with good visibility and good grip -> prudent',
     355,
     [ weather(rain, _, _),
       fuzzy_membership(visibility, good, V),
       fuzzy_membership(grip, good, G),
       test(( D is min(V, G), D > 0.0 )) ],
     [ assert(fuzzy_conclusion(prudent, D)) ]).

% light only refines a ceiling another term already set: alone it proposes nothing.
rule('Fuzzy: no rain with good visibility and good grip -> light',
     355,
     [ weather(Condition, _, _),
       test(Condition \== rain),
       fuzzy_membership(visibility, good, V),
       fuzzy_membership(grip, good, G),
       test(( D is min(V, G), D > 0.0 )) ],
     [ assert(fuzzy_conclusion(light, D)) ]).

% Rain alone (prudent) is a moderate risk; reduced visibility or grip is a high one.
rule('Defuzzify weather speed ceiling',
     350,
     [ weather(_, _, _),
       test(fuzzy_speed_ceiling(Target, Risk)) ],
     [ assert(proposal(reduce_speed, Target, Risk, road_hazard,
                       'Weather conditions (fuzzy) require a reduced speed')) ]).

% In rain, two TTC readings below 3 s within 2 s are enough to brake. A reading counts only
% when it was measured in the rain: a rain reading within 400 ms of it (coincides[400ms]).
rule('Persistent low TTC in rain increases conservatism',
     560,
     [ weather(rain, _, _),
       test(persistent_low_ttc_in_rain(3.0, Count)),
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
       test(allen_after(Now, Start, 1200)) ],
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

% A lower limit ahead is not in force yet, but the vehicle has to be down to it at the sign.
% Far from the sign the approach speed is above any speed and the rule stays out; from
% there the target follows the approach speed down to the new limit.
rule('Slow down in time for the speed limit ahead (slow)',
     310,
     [ pending_speed_limit(Next, SignPosition),
       speed_limit(Limit),
       ego_speed(V),
       ego_position(Position),
       test(limit_ahead_target(Next, SignPosition, Position, V, Limit, Target)),
       test(V > Target + 0.1),
       test(limit_ahead_reason(Next, Reason)) ],
     [ assert(proposal(reduce_speed, Target, moderate, speed_compliance, Reason)) ]).

rule('Slow down in time for the speed limit ahead (hold)',
     310,
     [ pending_speed_limit(Next, SignPosition),
       speed_limit(Limit),
       ego_speed(V),
       ego_position(Position),
       test(limit_ahead_target(Next, SignPosition, Position, V, Limit, Target)),
       test(V =< Target + 0.1),
       test(limit_ahead_reason(Next, Reason)) ],
     [ assert(proposal(maintain_speed, Target, low, speed_compliance, Reason)) ]).

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
       test(V < Target - 0.5) ],
     [ assert(proposal(maintain_speed, Target, low, speed_compliance,
                       'Road is clear; building back up to the scenario cruise speed')) ]).

% Driver acceleration request: accepted for 1.5 s. A comfort proposal only applies when no
% ceiling, hold or manoeuvre speed holds in the cycle, which the arbitration settles, so the
% rule needs no guard.
rule('Allow acceleration request when safe context',
     205,
     [ acceleration_request(Target, Age),
       test((Target > 0, Age < 1500)) ],
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
% of a STOP wins the tie. It steps aside during a driver lane change request: the driver's
% request is answered first. A vehicle going around a stationary vehicle needs no such
% step: the lane change is arbitrated in its own channel and moves off at the same time.
rule('Move off when stopped and nothing holds the vehicle',
     -90,
     [ test(moving_off),
       speed_limit(Limit),
       not(lane_change_request(_)),
       test(Target is min(30.0, Limit)) ],
     [ assert(proposal(continue, Target, low, maneuver_safety,
                       'Vehicle was stopped and no rule holds it any more')) ]).

% Only when no speed (longitudinal) proposal was made: a lane proposal alone does not
% count, as in Drools, where the context priority only follows speed proposals.
rule('Default maintain speed baseline',
     -100,
     [ ego_speed(V),
       not(test(speed_proposal)) ],
     [ assert(proposal(maintain_speed, V, low, comfort_progress,
                       'No restrictive condition detected')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Sub-objetivos (encadeamento inverso, chamados com prove/1)
%
% "É seguro mudar de faixa para este lado?" é um objetivo provado a partir de sub-objetivos,
% e estes a partir dos factos; as regras de desvio (encadeamento para a frente) chamam-no
% nas condições. Replica as queries de lane-safety.drl. Side é o lado do ego (left|right),
% como em lane_occupancy/3, não uma faixa.

safe_to_change_lane(Side) :-
    maneuver_idle,
    no_continuous_line(Side),
    side_clear(Side).

% Nenhuma mudança de faixa em movimento.
maneuver_idle :-
    known(lane_change_state(idle)).

% A linha a cruzar não é contínua: entre RIGHT e CENTER a right marking, entre CENTER e LEFT
% a left marking.
no_continuous_line(left) :-
    known(ego_lane(right)),
    known(right_marking(Marking)),
    Marking \== continuous.
no_continuous_line(left) :-
    known(ego_lane(center)),
    known(left_marking(Marking)),
    Marking \== continuous.
no_continuous_line(right) :-
    known(ego_lane(center)),
    known(right_marking(Marking)),
    Marking \== continuous.
no_continuous_line(right) :-
    known(ego_lane(left)),
    known(left_marking(Marking)),
    Marking \== continuous.

% O sensor de ângulo morto dá esse lado livre.
side_clear(Side) :-
    known(lane_occupancy(Side, false, _)).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Predicados auxiliares (chamados a partir de test/1)

% The lane two to the left (LEFT, seen from RIGHT) is clear more than 20 m beyond a vehicle D
% metres ahead in the lane to the left: no vehicle there within radar range, or it is
% further. Drools: FarLeftLaneVehicleState($farLeft) with $farLeft > $distance + 20.0.
far_left_clear_beyond(D) :-
    (   known(far_left_lane_vehicle(_, Far, _))
    ->  Far > D + 20.0
    ;   true
    ).

% The side was read after the last lane change settled: for a tick or two after it settles,
% the blind-spot sensor (every 200 ms) still describes the sides of the lane it came from.
% Drools: LaneOccupancyState(timestamp > $settled) with $settled the laneSettledAtMillis.
fresh_side_reading(Side) :-
    known(lane_occupancy_read(Side, Read)),
    known(lane_settled_at(Settled)),
    Read > Settled.

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

% Inside the 12 m gap behind a moving front vehicle, not opening the gap fast.
reopening_gap(V, D, Rel) :-
    D =< 12.0,
    Rel > -0.3,
    \+ front_vehicle_stopped(V, Rel).

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

% When two overtake rules start in the same cycle, the one that fired last prevails, as in
% Drools, where the later modify of the state fact wins.
effective_overtaking(true, Origin, false, Start, Target) :-
    known(overtake_started(_, _, _)),
    findall(Id-overtake_started(T, O, S), wm_fact(Id, overtake_started(T, O, S)), Started),
    msort(Started, Sorted),
    last(Sorted, _-overtake_started(Target, Origin, Start)), !.
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

% A continuous line on a side of the ego's lane: the right marking lies between CENTER and
% RIGHT, the left marking between LEFT and CENTER (WorldState.getMarkingBetween).
continuous_line_closes_lane(center) :-
    (   known(left_marking(continuous))
    ;   known(right_marking(continuous))
    ), !.
continuous_line_closes_lane(right) :-
    known(right_marking(continuous)).
continuous_line_closes_lane(left) :-
    known(left_marking(continuous)).

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

% StoppingPolicy.approachSpeedKmh: the fastest the vehicle may still be going D metres
% before a sign posting Limit, so that slowing gently brings it to the limit a buffer before
% the sign.
approach_speed(Limit, D, Speed) :-
    deceleration(anticipation, A),
    param(speedLimitApproachBufferMeters, Buffer),
    LimitMps is max(0.0, Limit) / 3.6,
    Room is max(0.0, D - Buffer),
    Speed is sqrt(LimitMps * LimitMps + 2.0 * A * Room) * 3.6.

% The rule applies once the approach speed is within 1 km/h of the current speed; the target
% is that approach speed, never above the limit in force.
limit_ahead_target(Next, SignPosition, Position, V, Limit, Target) :-
    D is SignPosition - Position,
    approach_speed(Next, D, Approach),
    Approach =< V + 1.0,
    Target is min(Limit, Approach).

limit_ahead_reason(Next, Reason) :-
    format(atom(Reason), 'Speed limit ~w km/h ahead; slowing to reach it at the sign', [Next]).

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
    allen_after(Now, Start, 1200).

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

% The same count, of the readings a rain reading coincides with.
persistent_low_ttc_in_rain(Threshold, Count) :-
    ttc_window_ms(Window),
    rain_coincidence_ms(Coincidence),
    aggregate_all(count,
                  ( known(ttc_sample(_, T, Age)), T > 0.0, T < Threshold, Age < Window,
                    once(( known(rain_sample(RainAge)), allen_coincides(Age, RainAge, Coincidence) )) ),
                  Count).

% Allen's relations between point events (Drools CEP), on their times in ms:
% after[Min]: Later comes at least Min ms after Earlier; coincides[Max]: at most Max ms apart.
allen_after(Later, Earlier, Min) :-
    Later - Earlier >= Min.
allen_coincides(T1, T2, Max) :-
    abs(T1 - T2) =< Max.

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

% A proposal in the longitudinal (speed) channel: anything but a lane action.
speed_proposal :-
    known(proposal(Action, _, _, _, _)),
    \+ memberchk(Action, [keep_lane, change_lane_left, change_lane_right, return_right]), !.

% Lógica difusa: the membership functions and the centroid of FuzzySupport.java.

% The degrees of a visibility or grip reading (clamped to 0..1) in the four input terms.
fuzzy_input_degrees(Value, Poor, Reduced, Fair, Good) :-
    X is max(0.0, min(1.0, float(Value))),
    fuzzy_trapezoid(X, 0.0, 0.0, 0.35, 0.5, Poor),
    fuzzy_triangle(X, 0.35, 0.5, 0.7, Reduced),
    fuzzy_triangle(X, 0.5, 0.7, 0.85, Fair),
    fuzzy_trapezoid(X, 0.7, 0.85, 1.0, 1.0, Good).

fuzzy_output_set(very_slow, 15.0, 25.0, 35.0).
fuzzy_output_set(slow, 30.0, 40.0, 50.0).
fuzzy_output_set(moderate, 45.0, 55.0, 65.0).
fuzzy_output_set(prudent, 55.0, 65.0, 75.0).
fuzzy_output_set(light, 65.0, 75.0, 85.0).

% Output terms, most restrictive first (ties in the strongest term go to the first).
fuzzy_output_terms([very_slow, slow, moderate, prudent, light]).

fuzzy_triangle(X, A, B, C, D) :-
    (   ( X =< A ; X >= C ) -> D = 0.0
    ;   X =< B -> D is (X - A) / (B - A)
    ;   D is (C - X) / (C - B)
    ).

fuzzy_trapezoid(X, A, B, C, Dd, D) :-
    (   ( X < A ; X > Dd ) -> D = 0.0
    ;   ( X >= B, X =< C ) -> D = 1.0
    ;   X < B -> D is (X - A) / (B - A)
    ;   D is (Dd - X) / (Dd - C)
    ).

% The ceiling, when a restrictive term (any but light) holds: the centroid of the
% aggregated output, sampled every 0.5 km/h from 0 to 130 km/h.
fuzzy_speed_ceiling(Target, Risk) :-
    findall(T-D, known(fuzzy_conclusion(T, D)), Conclusions),
    once(( member(T0-D0, Conclusions), T0 \== light, D0 > 0.0 )),
    fuzzy_centroid(Conclusions, Target),
    fuzzy_strongest_term(Conclusions, Strongest),
    (   Strongest == prudent -> Risk = moderate ; Risk = high ).

fuzzy_centroid(Conclusions, Centroid) :-
    fuzzy_centroid_sums(0, Conclusions, 0.0, 0.0, Weighted, Area),
    Area > 0.0,
    Centroid is Weighted / Area.

fuzzy_centroid_sums(I, _, W, A, W, A) :-
    I > 260, !.
fuzzy_centroid_sums(I, Conclusions, W0, A0, W, A) :-
    X is I * 0.5,
    fuzzy_aggregate(Conclusions, X, 0.0, Mu),
    W1 is W0 + X * Mu,
    A1 is A0 + Mu,
    I1 is I + 1,
    fuzzy_centroid_sums(I1, Conclusions, W1, A1, W, A).

fuzzy_aggregate([], _, Mu, Mu).
fuzzy_aggregate([T-D | Rest], X, Mu0, Mu) :-
    fuzzy_output_set(T, A, B, C),
    fuzzy_triangle(X, A, B, C, M),
    Mu1 is max(Mu0, min(D, M)),
    fuzzy_aggregate(Rest, X, Mu1, Mu).

fuzzy_strongest_term(Conclusions, Strongest) :-
    fuzzy_output_terms(Terms),
    foldl(fuzzy_stronger(Conclusions), Terms, none-(-1.0), Strongest-_).

fuzzy_stronger(Conclusions, Term, Best0-Degree0, Best-Degree) :-
    (   member(Term-D, Conclusions), D > Degree0
    ->  Best = Term, Degree = D
    ;   Best = Best0, Degree = Degree0
    ).

% Boxed in behind slow traffic, as the cycle leaves it: started in an earlier cycle and not
% cleared in this one.
current_boxed_in(Since) :-
    \+ known(boxed_in_cleared),
    known(boxed_in(_, Since)).
