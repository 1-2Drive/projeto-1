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
%   ego_path_lane(Lane)   a faixa do percurso: a de destino durante uma mudança de faixa, senão a do ego
%   ego_position(M)   a posição do ego na estrada, em metros percorridos
%   speed_limit(Kmh)  signed_speed_limit(Kmh) | no_speed_sign  cruise_speed(Kmh)
%   pending_speed_limit(Kmh, SignPositionM)   sinal lido à frente, ainda não alcançado
%   traffic_light(Id, red|yellow|green, DistanceM)  light_commitment(Id, stop|proceed)
%   pedestrian(Id, DistanceM)  crossing_state(Id, waiting|crossing|crossed)  pedestrian_in_path(Id)
%   ttc_sample(Target, TtcS, AgeMs)  rain_sample(AgeMs)   uma leitura de chuva do sensor de ambiente
%   stop_sign(Id, DistanceM)  stop_hold_started(Id, Ms)  stop_completed(Id)
%   previous_decision(Action, Rule)  previous_priority(Value)   a última decisão arbitrada
%   move_off_state(Active, Ms)   o MoveOffState do Drools
%   pedestrian_braking_state(Phase, Ms)   o PedestrianBrakingState do Drools (none, braking, holding)
%   right_lane_blocked_ahead   um perigo na faixa da direita a 150 m ou menos (Java)
%   presentation_demo          o cenário de demonstração guiada (Java)
%   lane_change_request(left|right)   um pedido do condutor ainda ativo (1,5 s)
%   acceleration_request(TargetKmh, AgeMs)   o último pedido de aceleração do condutor
%   front_vehicle(Target, DistanceM, RelSpeedMps)   RelSpeed > 0: a aproximar-se
%   front_distance_sample(Target, DistanceM, AgeMs)  front_read_now(Target)
%   left_lane_vehicle(Target, DistanceM, RelSpeedMps)  left_read_now(Target)
%   right_lane_vehicle(Target, DistanceM, RelSpeedMps)   a faixa à direita, do mesmo radar
%   far_left_lane_vehicle(Target, DistanceM, RelSpeedMps)   duas faixas à esquerda (LEFT, visto de RIGHT)
%   lane_occupancy(left|right, Occupied, FrontDistanceM)   o lado do ego, não uma faixa
%   lane_occupancy_read(left|right, Ms)   quando esse lado foi lido (LaneOccupancyState.timestamp)
%   lane_settled_at(Ms)   quando a última mudança de faixa assentou (laneSettledAtMillis), -1 se nenhuma
%   lane_request_at(Ms)   quando o condutor pediu a última mudança de faixa (LaneChangeRequestState.timestamp), -1 se nenhuma
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

% A curva de aproximação e a paragem obrigatória travam com conforto; um peão visto pela primeira
% vez quando só uma travagem forte ainda para 6 m antes dele precisa dessa travagem forte já, e
% não aos 6 m.
rule('Emergency brake for pedestrian when comfortable braking no longer stops in time',
     879,
     [ pedestrian_conflict(P),
       pedestrian(P, D),
       ego_speed(V),
       test(D > 6.0),
       test(pedestrian_hard_braking(V, D)) ],
     [ assert(proposal(emergency_brake, 0.0, critical, emergency_safety,
                       'Pedestrian ahead; comfortable braking no longer stops short of it')) ]).

% Só conta uma leitura dos últimos 300 ms, e só na faixa em que o veículo está fisicamente: ao
% desviar-se da faixa do obstáculo, fica para lá dele assim que a sua posição lateral sai dessa
% faixa, mesmo enquanto a faixa assumida (fixada depois do atuador) ainda é a antiga.
rule('Emergency braking for high severity obstacle at critical distance',
     870,
     [ obstacle_sample(_, D, high, Lane, Age),
       ego_physical_lane(Lane),
       test((D > 0, D =< 8.0, Age < 300)) ],
     [ assert(proposal(emergency_brake, 0.0, critical, emergency_safety,
                       'High severity obstacle critically close')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Peões

% Conhecimento intermédio: o Drools escreve "inPath == true || crossingState == CROSSING" em cada
% regra; aqui a disjunção é uma conclusão própria, com uma regra por ramo. Precedência acima de
% todos os leitores, para que as regras que exigem a ausência de conflito nunca disparem antes de
% ele ser conhecido.
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

% A fase de aproximação (pedestrian_braking/2, o PedestrianBrakingState do Drools: none, braking,
% holding) fica decidida antes da proposta do ciclo: a travagem começa acima da curva de 2,0 m/s2
% que termina 18 m antes do peão e só termina abaixo da curva de 1,4 m/s2, onde o veículo mantém
% a velocidade até a curva superior o voltar a apanhar; a aproximação acaba quando já não há
% nenhum peão de que se aproxime (desapareceu, sem conflito, ou dentro da zona de paragem de 32 m).
rule('Start braking for pedestrian above the braking curve',
     607,
     [ pedestrian_braking_state(Phase, _),
       test(Phase \== braking),
       pedestrian_conflict(P),
       pedestrian(P, D),
       ego_speed(V),
       time_ms(Now),
       test(D > 32.0),
       test(above_pedestrian_curve(V, D, 2.0)) ],
     [ assert(pedestrian_braking_started(Now)) ]).

rule('Hold speed for pedestrian once under the lower curve',
     607,
     [ pedestrian_braking_state(braking, _),
       pedestrian_conflict(P),
       pedestrian(P, D),
       ego_speed(V),
       test(D > 32.0),
       not(test(above_pedestrian_curve(V, D, 1.4))) ],
     [ assert(pedestrian_braking_ended) ]).

rule('End the pedestrian approach once no pedestrian is approached',
     607,
     [ pedestrian_braking_state(Phase, _),
       test(Phase \== none),
       not(test(pedestrian_approached(32.0))) ],
     [ assert(pedestrian_approach_over) ]).

% Fase de travagem: travar.
rule('Brake for pedestrian proximity (brake)',
     605,
     [ pedestrian_conflict(P),
       pedestrian(P, D),
       ego_speed(V),
       test(D > 32.0),
       test(pedestrian_phase(braking)),
       test(pedestrian_reason(P, Reason)) ],
     [ assert(proposal(brake, 20.0, high, legal_mandatory_stop, Reason)) ]).

% Fase de manutenção, ou entre as curvas antes de qualquer travagem: manter a velocidade, nunca
% acelerar em direção ao peão.
rule('Brake for pedestrian proximity (hold speed)',
     605,
     [ pedestrian_conflict(P),
       pedestrian(P, D),
       ego_speed(V),
       test(D > 32.0),
       not(test(pedestrian_phase(braking))),
       % once/1: uma disjunção que se verifica pelos dois ramos é uma correspondência, não duas. O
       % motor guarda cada solução das condições como uma ativação própria e, com o índice de
       % dependências ativo, um duplicado deixado na agenda dispararia uma segunda vez.
       test(once(( pedestrian_phase(holding) ; above_pedestrian_curve(V, D, 1.4) ))),
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

% O compromisso de parar ou de avançar é tomado uma vez por semáforo e fase, e mantido no
% estado até o semáforo ficar verde ou outro semáforo o substituir.
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

% Aproximação ao vermelho, decidida pela distância necessária a partir da velocidade atual.
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

% Amarelo com compromisso de parar: como o vermelho, mas nunca uma travagem de emergência, porque
% o compromisso só foi tomado com espaço para uma paragem confortável.
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

% Demasiado perto e ainda a aproximar-se: travar até 3 km/h abaixo da velocidade do veículo da
% frente, para que a distância volte a abrir em vez de apenas manter a velocidade de aproximação.
rule('Brake when front vehicle distance low',
     520,
     [ front_vehicle(_, D, Rel),
       ego_speed(V),
       test((D > 0, D < 14.0, Rel > 0)),
       test(Target is max(0.0, (V / 3.6 - Rel) * 3.6 - 3.0)) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Front vehicle too close')) ]).

% Arts. 18.º e 24.º do CE: a velocidade a partir da qual 2,0 m/s2 ainda iguala a velocidade do
% veículo da frente 12 m atrás dele. Só propõe acima dessa velocidade, porque uma recomendação
% acima da velocidade atual faria o atuador acelerar. Atrás de um veículo parado, mantém a paragem
% até 2 m para lá do ponto de paragem, para que o ruído do radar não a liberte. Dentro da distância
% de 12 m atrás de um veículo em movimento, e sem a distância a abrir depressa, a curva nada diz
% (já não há espaço): o veículo segue então 3 km/h mais devagar do que o da frente até a distância
% voltar a abrir.
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

% Tendência na janela de 2 s, só com as distâncias do veículo seguido agora: quando o radar muda
% para outro veículo, o salto de distância entre os dois não é uma aproximação.
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

% Seguir um veículo em movimento à velocidade dele, logo fora da distância de 12 m, onde nenhuma
% regra acima se aplica a não ser que o ego se esteja a aproximar: de outro modo, a velocidade de
% cruzeiro levá-lo-ia contra o veículo da frente e a travagem (abaixo de 14 m) voltaria a assumir
% o controlo, em ciclo. A menos de 16 m, 1 km/h a menos, para que a distância estabilize longe dos
% 14 m da travagem, para lá do ruído do radar. A janela vai até 20 m, dividida pela aderência
% quando esta é baixa (no máximo o dobro).
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

% Abaixo dos 35 km/h que a regra da tendência pede, esse pedido faria o atuador acelerar em direção
% ao veículo de que se aproxima: enquanto se aproxima de facto, manter a velocidade.
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

% Uma ultrapassagem proposta que nunca saiu da sua faixa (outra regra ganhou o ciclo, ou o atuador
% recusou) ficaria ativa para sempre. Um segundo chega para uma manobra começar.
rule('Cancel overtaking that never left its original lane',
     540,
     [ overtaking(true, Origin, false, Start, _),
       ego_lane(Origin),
       lane_change_state(idle),
       time_ms(Now),
       test(Now - Start > 1000) ],
     [ assert(overtake_cancelled) ]).

% Ultrapassado assim que o ego saiu da faixa de origem, a faixa da direita está livre 80 m à frente
% (o veículo ultrapassado já lá não está) e o radar já não o tem à frente.
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

% Arts. 35.º e 36.º do CE: ultrapassar pela esquerda, com a faixa da esquerda livre. A janela para
% começar cresce com a velocidade de aproximação (5,5 s de TTC mais 10 m). Um veículo parado só é
% ultrapassado em andamento com essa margem toda. Passar para a esquerda, a partir de CENTER ou de
% RIGHT, só compensa se o veículo seguinte nessa faixa estiver claramente mais longe. Art. 41.º do
% CE: nunca com um peão no caminho nem com um semáforo vermelho ou amarelo à frente.
rule('Overtake slow front vehicle via left lane',
     525,
     [ ego_lane(Lane),
       test(memberchk(Lane, [right, center])),
       lane_change_state(idle),
       prove(no_continuous_line(left)),
       % o veículo da frente na faixa da esquerda, se houver, ainda pode ser seguido depois da mudança
       prove(room_to_follow(left)),
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

% A ultrapassagem acima exige que o ego se esteja a aproximar, o que nunca acontece depois de a
% regra de seguimento o ter acertado pela velocidade do veículo da frente. Esta é a saída do
% seguimento: à velocidade do veículo da frente (com 0,5 m/s de tolerância para cada lado), na
% janela de seguimento (6-30 m) e com esse veículo pelo menos 10 km/h abaixo da velocidade que o
% ego pretende (o limite sinalizado, ou a velocidade de cruzeiro do cenário), passa para a esquerda
% quando a faixa da esquerda está livre. As mesmas condições de faixa, linha, faixa à frente e
% legais (art. 41.º do CE) que a ultrapassagem.
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

% Art. 36.º do CE: um veículo mais lento na faixa da esquerda não é ultrapassado pela sua direita.
% A partir da faixa da direita, o ego primeiro coloca-se atrás dele (RIGHT -> CENTER) e esse
% veículo passa a ser o alvo; já atrás dele, a regra anterior continua CENTER -> LEFT. Art. 38.º
% do CE: só com LEFT livre bem para lá desse veículo (+20 m, como a ultrapassagem exige); caso
% contrário, o ego fica em RIGHT atrás dele.
rule('Move left behind slower left lane vehicle to overtake it on the left',
     525,
     [ ego_lane(right),
       prove(safe_to_change_lane(left)),
       ego_speed(V),
       time_ms(Now),
       left_lane_vehicle(Target, D, Rel),
       left_read_now(Target),
       test((D > 15.0, Rel > -0.5)),
       not(test(front_vehicle_within(D))),
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

% Parado atrás de um veículo parado (esquerda ocupada, ou demasiado perto para o contornar em
% andamento): contorná-lo assim que a faixa da esquerda ficar livre, dentro dos mesmos limites legais.
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

% Art. 38.º do CE: depois de ultrapassar, voltar para a direita uma faixa de cada vez, com essa
% faixa livre ao lado e 80 m à frente, e sem nenhuma mudança de faixa pedida pelo condutor.
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

% Trânsito lento em todas as faixas que o ego poderia usar para ultrapassar (front-vehicle.drl, as
% mesmas regras). boxed_in(Lane, Since) vem do ciclo anterior (driving_state.pl); as regras
% concluem boxed_in_started/2 ou boxed_in_cleared e current_boxed_in/1 lê o resultado.
% Encurralado: atrás de um veículo pelo menos 10 km/h abaixo da velocidade que o ego pretende, e
% sem poder passar para a esquerda (limite da estrada, linha contínua, faixa ocupada, ou não mais
% livre do que a sua própria faixa por 20 m). Ao fim de 3 s o ego deixa-se ficar para trás, volta
% para a direita com a faixa da direita livre ao lado e à frente (regra dos 2 segundos, 3 s com
% chuva ou pouca aderência, pelo menos 30 m) mas com trânsito nela a menos de 80 m (numa faixa da
% direita vazia ultrapassaria o veículo lento pela direita, art. 36.º do CE), e não volta a
% iniciar a mesma ultrapassagem até o caminho à frente ou à esquerda abrir.
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

% Art. 13.º do CE (circular pela direita): circular na faixa disponível e segura mais à direita.
% CENTER e LEFT são faixas de manobra: terminada uma ultrapassagem ou um desvio, com a faixa da
% direita livre ao lado e 80 m à frente, voltar para a direita uma faixa de cada vez. Nada pode
% exigir atenção: arranque, obstáculo, buraco, barreira fechada, peão em conflito, um veículo a
% menos de 80 m, uma ultrapassagem, uma mudança de faixa pedida pelo condutor. Vê as
% ultrapassagens que começam neste ciclo (precedência mais baixa). O arranque é o do início do
% ciclo, como no Drools, onde esta regra dispara antes das regras de arranque.
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
       not(test(driver_lane_change_hold(Now))),
       not(test(current_overtaking(true, _, _, _, _))) ],
     [ assert(proposal(return_right, no_target, low, maneuver_safety,
                       'Portuguese Highway Code Art. 13: Drive on the rightmost available safe lane')) ]).

% Art. 36.º do CE: enquanto ainda não está atrás de um veículo mais lento na faixa da esquerda, o
% ego não pode ficar a par dele. Segue a curva que iguala a velocidade desse veículo 35 m atrás
% dele (logo para lá do alcance do ângulo morto, para que a faixa seja lida como livre e o ego
% possa entrar). Se uma travagem confortável levasse o ego para lá do veículo
% (cannot_stay_behind/2): travar a fundo, como no Drools, já que ficar a par e ultrapassar pela
% direita é o que o art. 36.º proíbe.
rule('Do not pass a slower left lane vehicle on its right (emergency)',
     508,
     [ ego_speed(V),
       left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       test(cannot_stay_behind(D, Rel)),
       test(left_vehicle_curve(V, D, Rel, Curve)) ],
     [ assert(proposal(emergency_brake, Curve, high, speed_compliance,
                       'Slower vehicle in the left lane must not be passed on its right (Art. 36 CE)')) ]).

rule('Do not pass a slower left lane vehicle on its right (closing in)',
     508,
     [ ego_speed(V),
       left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       not(test(cannot_stay_behind(D, Rel))),
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
       not(test(cannot_stay_behind(D, Rel))),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V =< Curve),
       test(D < 33.0),
       test(Target is max(0.0, V / 3.6 - Rel - 1.5) * 3.6) ],
     [ assert(proposal(reduce_speed, Target, moderate, speed_compliance,
                       'Slower vehicle in the left lane must not be passed on its right (Art. 36 CE)')) ]).

rule('Do not pass a slower left lane vehicle on its right (hold)',
     508,
     [ ego_speed(V),
       left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       not(test(cannot_stay_behind(D, Rel))),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V =< Curve),
       test(D >= 33.0),
       test(Target is (V / 3.6 - Rel) * 3.6) ],
     [ assert(proposal(maintain_speed, Target, moderate, speed_compliance,
                       'Slower vehicle in the left lane must not be passed on its right (Art. 36 CE)')) ]).

% O mesmo para um veículo mais lento duas faixas à esquerda (LEFT, visto de RIGHT), como no
% Drools: depois de contornar pela direita um perigo no centro, esse veículo não é ultrapassado
% pela sua direita (art. 36.º do CE). Sem ficar para trás: daqui não há faixa para onde passar
% atrás dele.
rule('Do not pass a slower far left lane vehicle on its right (emergency)',
     508,
     [ ego_speed(V),
       far_left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       test(cannot_stay_behind(D, Rel)),
       test(left_vehicle_curve(V, D, Rel, Curve)) ],
     [ assert(proposal(emergency_brake, Curve, high, speed_compliance,
                       'Slower vehicle two lanes to the left must not be passed on its right (Art. 36 CE)')) ]).

rule('Do not pass a slower far left lane vehicle on its right (closing in)',
     508,
     [ ego_speed(V),
       far_left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       not(test(cannot_stay_behind(D, Rel))),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V > Curve) ],
     [ assert(proposal(reduce_speed, Curve, moderate, speed_compliance,
                       'Slower vehicle two lanes to the left must not be passed on its right (Art. 36 CE)')) ]).

rule('Do not pass a slower far left lane vehicle on its right (hold)',
     508,
     [ ego_speed(V),
       far_left_lane_vehicle(_, D, Rel),
       test((D > 0, Rel > -2.5)),
       test(must_not_pass_on_right(V, D, Rel)),
       not(test(cannot_stay_behind(D, Rel))),
       test(left_vehicle_curve(V, D, Rel, Curve)),
       test(V =< Curve),
       test(Target is (V / 3.6 - Rel) * 3.6) ],
     [ assert(proposal(maintain_speed, Target, moderate, speed_compliance,
                       'Slower vehicle two lanes to the left must not be passed on its right (Art. 36 CE)')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Desvio de perigos na faixa do ego
%
% O veículo contorna um perigo na sua faixa quando os sensores de ângulo morto confirmam
% que uma faixa adjacente está livre. As regras só disparam com o perigo na faixa do ego,
% por isso, depois de mudar de faixa, deixam de corresponder e a manobra não oscila.

% Enquanto a mudança de faixa está em execução, mantê-la como decisão. Valem também para uma
% ultrapassagem ou para um pedido do condutor, por isso propõem com prioridade de manobra.
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

% Buraco: a partir do centro, preferir a direita (a faixa livre mais à direita); a esquerda só com
% a direita ocupada.
rule('Avoid pothole in centre lane by changing right when right is clear',
     475,
     [ ego_lane(center),
       pothole(_, D, _, center),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear right lane (rightmost safe lane policy)')) ]).

rule('Avoid pothole in centre lane by changing left when right is occupied',
     470,
     [ ego_lane(center),
       pothole(_, D, _, center),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
       not(test(safe_to_change_lane(right))),
       prove(safe_to_change_lane(left)) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear left lane')) ]).

% Cada mudança de faixa verifica a linha que atravessa: entre RIGHT e CENTER a marcação da direita,
% entre CENTER e LEFT a marcação da esquerda; uma linha contínua nunca é atravessada. As regras de
% desvio provam-no, com a manobra inativa e o lado livre, através de safe_to_change_lane/1.
rule('Avoid pothole in left lane by changing right',
     470,
     [ ego_lane(left),
       pothole(_, D, _, left),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear right lane')) ]).

rule('Avoid pothole in right lane by changing left',
     470,
     [ ego_lane(right),
       pothole(_, D, _, right),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
       prove(safe_to_change_lane(left)) ],
     [ assert(proposal(change_lane_left, no_target, low, road_hazard,
                       'Avoiding pothole by moving into the clear left lane')) ]).

% Obstáculo: a partir do centro, preferir a esquerda; a direita só com a esquerda ocupada. Um
% obstáculo bloqueia a faixa, por isso contorná-lo é evitar uma colisão. Decidido aos 50 m, dentro
% dos 60 m em que o sensor de ângulo morto reporta perigos nas faixas laterais, para que um lado
% considerado livre esteja livre durante toda a manobra; até lá, a regra de paragem no obstáculo
% mantém a velocidade na curva de travagem confortável.
rule('Avoid obstacle in centre lane by changing left',
     470,
     [ ego_lane(center),
       obstacle(_, D, _, center),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
       prove(safe_to_change_lane(left)) ],
     [ assert(proposal(change_lane_left, no_target, low, collision_avoidance,
                       'Avoiding obstacle by moving into the clear left lane')) ]).

rule('Avoid obstacle in centre lane by changing right when left is occupied',
     470,
     [ ego_lane(center),
       obstacle(_, D, _, center),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
       not(test(safe_to_change_lane(left))),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, collision_avoidance,
                       'Avoiding obstacle by moving into the clear right lane')) ]).

rule('Avoid obstacle in left lane by changing right',
     470,
     [ ego_lane(left),
       obstacle(_, D, _, left),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
       prove(safe_to_change_lane(right)) ],
     [ assert(proposal(change_lane_right, no_target, low, collision_avoidance,
                       'Avoiding obstacle by moving into the clear right lane')) ]).

rule('Avoid obstacle in right lane by changing left',
     470,
     [ ego_lane(right),
       obstacle(_, D, _, right),
       test((D > 0, D =< 50.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)),
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

% Um buraco na trajetória do veículo (a sua faixa, ou a faixa para onde está a mudar) que não
% está a ser contornado é passado devagar (art. 24.º do CE). Não está a ser contornado enquanto
% nenhum lado for uma passagem segura, enquanto estiver demasiado perto para uma mudança de
% faixa sair da faixa antes dele (room_to_leave_lane/2), ou enquanto ainda estiver para lá dos
% 50 m a que o desvio é decidido. Aplica-se a partir da distância de que precisa um abrandamento
% confortável até à velocidade de passagem (nunca menos de 40 m): para lá dos 40 m segue a curva
% de antecipação até à velocidade de passagem e mantém a velocidade quando já está abaixo dela;
% dentro dos 40 m pede a velocidade de passagem, 20 km/h a menos de 15 m. Num buraco grave
% (high) trava-se a fundo quando a travagem confortável (com 10% de folga) já não chega à
% velocidade de passagem até ao buraco (pothole_hard_braking/3); num mais ligeiro só se abranda
% tanto quanto o conforto permite. A razão descreve a situação (pothole_reason/3), como no Drools.
rule('Pothole ahead reduce speed (emergency)',
     460,
     [ ego_path_lane(Lane),
       pothole(_, D, Severity, Lane),
       ego_speed(V),
       test(pothole_slow_window(V, W)),
       test((D > 0, D =< W)),
       not(test(pothole_avoidable_now(V, D))),
       test(pothole_hard_braking(V, D, Severity)),
       test(param(potholeCrossingSpeedKmh, Crossing)) ],
     [ assert(proposal(emergency_brake, Crossing, high, road_hazard,
                       'Severe pothole ahead; braking hard to reach its crossing speed (Art. 24 CE)')) ]).

rule('Pothole ahead reduce speed',
     460,
     [ ego_path_lane(Lane),
       pothole(_, D, Severity, Lane),
       ego_speed(V),
       test(pothole_slow_window(V, W)),
       test((D > 0, D =< W)),
       not(test(pothole_avoidable_now(V, D))),
       not(test(pothole_hard_braking(V, D, Severity))),
       test(pothole_target(D, Target)),
       test(once((D =< 40.0 ; Target < V))),
       test(pothole_reason(V, D, Reason)) ],
     [ assert(proposal(reduce_speed, Target, moderate, road_hazard, Reason)) ]).

rule('Pothole ahead reduce speed (hold)',
     460,
     [ ego_path_lane(Lane),
       pothole(_, D, Severity, Lane),
       ego_speed(V),
       test(pothole_slow_window(V, W)),
       test((D > 0, D =< W)),
       not(test(pothole_avoidable_now(V, D))),
       not(test(pothole_hard_braking(V, D, Severity))),
       test(pothole_target(D, Target)),
       test(D > 40.0),
       test(Target >= V),
       test(pothole_reason(V, D, Reason)) ],
     [ assert(proposal(maintain_speed, V, moderate, road_hazard, Reason)) ]).

% Obstáculo na faixa do ego enquanto o veículo ainda não começou a sair dessa faixa (nenhum
% lado livre, desvio vetado por uma paragem obrigatória, ou recusado pelo atuador): parar
% 10 m antes dele (fora da zona de emergência de 8 m). A curva só se aplica quando o veículo
% vai depressa demais para parar aí, para que um desvio que comece a tempo não seja abrandado.
% Segue a curva de travagem confortável; se a desaceleração necessária exceder a confortável
% em 10%, trava a fundo.
rule('Stop before obstacle ahead while still in its lane (stop)',
     465,
     [ ego_path_lane(Lane),
       obstacle(_, D, _, Lane),
       ego_speed(V),
       test(obstacle_stop_window(V, W)),
       test((D > 0, D =< W)),
       test(obstacle_margin(D, Margin)),
       test(Margin =< 0.5) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Obstacle ahead in own lane; stopping unless a lane change takes the vehicle out of its way')) ]).

rule('Stop before obstacle ahead while still in its lane (emergency)',
     465,
     [ ego_path_lane(Lane),
       obstacle(_, D, _, Lane),
       ego_speed(V),
       test(obstacle_stop_window(V, W)),
       test((D > 0, D =< W)),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_braking_excessive(V, Margin)) ],
     [ assert(proposal(emergency_brake, 0.0, high, collision_avoidance,
                       'Obstacle ahead in own lane; stopping unless a lane change takes the vehicle out of its way')) ]).

rule('Stop before obstacle ahead while still in its lane (brake)',
     465,
     [ ego_path_lane(Lane),
       obstacle(_, D, _, Lane),
       ego_speed(V),
       test(obstacle_stop_window(V, W)),
       test((D > 0, D =< W)),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       not(test(obstacle_braking_excessive(V, Margin))),
       test(obstacle_curve(Margin, Target)),
       test(Target < V) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Obstacle ahead in own lane; stopping unless a lane change takes the vehicle out of its way')) ]).

% Dentro da janela de paragem mas ainda acima da curva de travagem confortável: manter a
% velocidade enquanto o desvio é decidido, para o veículo não ganhar velocidade em direção
% ao obstáculo.
rule('Stop before obstacle ahead while still in its lane (hold)',
     465,
     [ ego_path_lane(Lane),
       obstacle(_, D, _, Lane),
       ego_speed(V),
       test(obstacle_stop_window(V, W)),
       test((D > 0, D =< W)),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       not(test(obstacle_braking_excessive(V, Margin))),
       test(obstacle_curve(Margin, Target)),
       test(Target >= V) ],
     [ assert(proposal(maintain_speed, V, moderate, collision_avoidance,
                       'Obstacle ahead in own lane; stopping unless a lane change takes the vehicle out of its way')) ]).

% Nenhum dos lados é seguro para entrar (a ocupação do ângulo morto já dá um lado como não
% seguro quando a linha desse lado é contínua, WorldState.sideLaneOccupancy) e a faixa do ego
% está fechada por uma linha contínua (a marcação esquerda fica entre LEFT e CENTER, a direita
% entre CENTER e RIGHT): não há para onde ir legalmente.
rule('Stop before obstacle when lane change prohibited by continuous line (stop)',
     466,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       ego_speed(V),
       test(obstacle_stop_window(V, W)),
       test((D > 0, D =< W)),
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
       ego_speed(V),
       test(obstacle_stop_window(V, W)),
       test((D > 0, D =< W)),
       lane_safe_to_enter(left, false),
       lane_safe_to_enter(right, false),
       test(continuous_line_closes_lane(Lane)),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_curve(Margin, Target)),
       test(Target < V) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Continuous line prohibits lane change; stopping before obstacle')) ]).

rule('Stop before obstacle when lane change prohibited by continuous line (hold)',
     466,
     [ ego_lane(Lane),
       obstacle(_, D, _, Lane),
       ego_speed(V),
       test(obstacle_stop_window(V, W)),
       test((D > 0, D =< W)),
       lane_safe_to_enter(left, false),
       lane_safe_to_enter(right, false),
       test(continuous_line_closes_lane(Lane)),
       test(obstacle_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_curve(Margin, Target)),
       test(Target >= V) ],
     [ assert(proposal(maintain_speed, V, moderate, collision_avoidance,
                       'Continuous line prohibits lane change; stopping before obstacle')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Barreiras

% Barreira fechada na faixa do ego: passar para uma faixa que o sensor de ângulo morto dá como
% livre, provado por safe_to_change_lane/1 (arts. 35.º e 38.º do CE). A barreira bloqueia a
% faixa, por isso contorná-la é evitar uma colisão. Nenhuma manobra em curso e nenhuma linha
% contínua na linha que atravessa (RIGHT-CENTER: a marcação direita; CENTER-LEFT: a marcação
% esquerda); caso contrário, o veículo para. Uma mudança de faixa só é passagem quando sai da
% faixa antes da barreira (room_to_leave_lane/2); mais perto do que isso, trava a regra de
% aproximação abaixo.
rule('Avoid closed barrier by changing left when safe',
     650,
     [ ego_lane(right),
       prove(safe_to_change_lane(left)),
       barrier(_, D, false, right),
       test((D > 0, D =< 55.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)) ],
     [ assert(proposal(change_lane_left, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing left to a verified clear lane')) ]).

rule('Avoid closed barrier by changing left from centre when safe',
     650,
     [ ego_lane(center),
       prove(safe_to_change_lane(left)),
       barrier(_, D, false, center),
       test((D > 0, D =< 55.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)) ],
     [ assert(proposal(change_lane_left, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing left to a verified clear lane')) ]).

rule('Avoid closed barrier by changing right from centre when safe',
     650,
     [ ego_lane(center),
       not(test(safe_to_change_lane(left))),
       prove(safe_to_change_lane(right)),
       barrier(_, D, false, center),
       test((D > 0, D =< 55.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)) ],
     [ assert(proposal(change_lane_right, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing right to a verified clear lane')) ]).

rule('Avoid closed barrier by changing right when safe',
     650,
     [ ego_lane(left),
       prove(safe_to_change_lane(right)),
       barrier(_, D, false, left),
       test((D > 0, D =< 55.0)),
       ego_speed(V),
       test(room_to_leave_lane(V, D)) ],
     [ assert(proposal(change_lane_right, no_target, high, collision_avoidance,
                       'Closed barrier ahead; changing right to a verified clear lane')) ]).

% Uma barreira fechada na trajetória do veículo (ego_path_lane/1: a sua faixa, ou a faixa para
% onde está a mudar) é abordada como um obstáculo: a partir da distância de paragem confortável
% (nunca menos de 55 m), mantendo a velocidade acima da curva de travagem, travando para a curva
% de antecipação quando está nela, a fundo quando a travagem confortável já não para 10 m antes
% dela. É um teto: não veta nada, e deixa de reter quando a mudança de faixa tira a trajetória
% da faixa da barreira.
rule('Stop before closed barrier while still in its lane (stop)',
     645,
     [ ego_path_lane(Lane),
       barrier(_, D, false, Lane),
       ego_speed(V),
       test(barrier_approach_window(V, W)),
       test((D > 0, D =< W)),
       test(barrier_margin(D, Margin)),
       test(Margin =< 0.5) ],
     [ assert(proposal(stop, 0.0, high, collision_avoidance,
                       'Closed barrier ahead in the path; stopping unless a lane change takes the vehicle out of its way')) ]).

rule('Stop before closed barrier while still in its lane (emergency)',
     645,
     [ ego_path_lane(Lane),
       barrier(_, D, false, Lane),
       ego_speed(V),
       test(barrier_approach_window(V, W)),
       test((D > 0, D =< W)),
       test(barrier_margin(D, Margin)),
       test(Margin > 0.5),
       test(obstacle_braking_excessive(V, Margin)) ],
     [ assert(proposal(emergency_brake, 0.0, high, collision_avoidance,
                       'Closed barrier ahead in the path; stopping unless a lane change takes the vehicle out of its way')) ]).

rule('Stop before closed barrier while still in its lane (brake)',
     645,
     [ ego_path_lane(Lane),
       barrier(_, D, false, Lane),
       ego_speed(V),
       test(barrier_approach_window(V, W)),
       test((D > 0, D =< W)),
       test(barrier_margin(D, Margin)),
       test(Margin > 0.5),
       not(test(obstacle_braking_excessive(V, Margin))),
       test(obstacle_curve(Margin, Target)),
       test(Target < V) ],
     [ assert(proposal(brake, Target, high, collision_avoidance,
                       'Closed barrier ahead in the path; stopping unless a lane change takes the vehicle out of its way')) ]).

rule('Stop before closed barrier while still in its lane (hold)',
     645,
     [ ego_path_lane(Lane),
       barrier(_, D, false, Lane),
       ego_speed(V),
       test(barrier_approach_window(V, W)),
       test((D > 0, D =< W)),
       test(barrier_margin(D, Margin)),
       test(Margin > 0.5),
       not(test(obstacle_braking_excessive(V, Margin))),
       test(obstacle_curve(Margin, Target)),
       test(Target >= V) ],
     [ assert(proposal(maintain_speed, V, moderate, collision_avoidance,
                       'Closed barrier ahead in the path; stopping unless a lane change takes the vehicle out of its way')) ]).

% Parar sempre que a barreira não possa ser contornada em segurança, seja qual for o motivo por
% que safe_to_change_lane/1 falha dos dois lados, para que o veículo desvie ou pare sempre. Só
% enquanto não começou a sair da faixa; a partir da distância de paragem confortável (nunca
% menos de 55 m); a travar a fundo quando a travagem confortável já não para antes da barreira.
rule('Closed barrier requires stop (emergency)',
     640,
     [ ego_lane(Lane),
       lane_change_state(idle),
       not(test(safe_to_change_lane(left))),
       not(test(safe_to_change_lane(right))),
       ego_speed(V),
       barrier(_, D, false, Lane),
       test(barrier_stop_window(V, W)),
       test((D >= 0, D =< W)),
       test(barrier_hard_braking(V, D)) ],
     [ assert(proposal(emergency_brake, 0.0, high, legal_mandatory_stop,
                       'Closed barrier ahead, no adjacent lane is safe and comfortable braking no longer stops in time')) ]).

rule('Closed barrier requires stop',
     640,
     [ ego_lane(Lane),
       lane_change_state(idle),
       not(test(safe_to_change_lane(left))),
       not(test(safe_to_change_lane(right))),
       ego_speed(V),
       barrier(_, D, false, Lane),
       test(barrier_stop_window(V, W)),
       test((D >= 0, D =< W)),
       not(test(barrier_hard_braking(V, D))) ],
     [ assert(proposal(stop, 0.0, high, legal_mandatory_stop,
                       'Closed barrier ahead and no adjacent lane is safe')) ]).

% Proposta de conforto: perde para qualquer teto de velocidade, retenção ou velocidade de
% manobra do ciclo, por isso não precisa de condição sobre o que mais foi proposto.
rule('Open barrier allows continue if no higher priority',
     170,
     [ barrier(_, D, true, _),
       test((D >= 0, D =< 35.0)) ],
     [ assert(proposal(continue, 35.0, low, comfort_progress,
                       'Barrier open and crossing allowed')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Condições meteorológicas: lógica difusa (fuzzy-weather.drl) e temporal.drl

% Controlador difuso da meteorologia (Mamdani), igual ao de fuzzy-weather.drl. A visibilidade e
% a aderência são vagas, por isso cada leitura pertence, com algum grau, aos termos poor /
% reduced / fair / good:
%   1. fuzificação     (360): weather/3 -> fuzzy_membership(Variable, Term, Degree)
%   2. inferência      (355): uma regra por regra difusa, AND = min, OR = max
%                             -> fuzzy_conclusion(Term, Degree)
%   3. composição e 4. desfuzificação (350): as conclusões são agregadas (max) e o seu
%                             centroide é a velocidade recomendada.
% O resultado é um único teto de velocidade em road_hazard, proposto só quando se verifica um
% termo restritivo. Os conjuntos e a amostragem do centroide são os de FuzzySupport.java, para
% que os dois motores calculem a mesma velocidade.

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

% A chuva é um valor nítido, não difuso (rain ou clear): entra na regra com grau 1 ou 0.
rule('Fuzzy: rain with good visibility and good grip -> prudent',
     355,
     [ weather(rain, _, _),
       fuzzy_membership(visibility, good, V),
       fuzzy_membership(grip, good, G),
       test(( D is min(V, G), D > 0.0 )) ],
     [ assert(fuzzy_conclusion(prudent, D)) ]).

% light só afina um teto que outro termo já fixou: sozinho não propõe nada.
rule('Fuzzy: no rain with good visibility and good grip -> light',
     355,
     [ weather(Condition, _, _),
       test(Condition \== rain),
       fuzzy_membership(visibility, good, V),
       fuzzy_membership(grip, good, G),
       test(( D is min(V, G), D > 0.0 )) ],
     [ assert(fuzzy_conclusion(light, D)) ]).

% Só a chuva (prudent) é um risco moderado; visibilidade ou aderência reduzidas, um risco alto.
rule('Defuzzify weather speed ceiling',
     350,
     [ weather(_, _, _),
       test(fuzzy_speed_ceiling(Target, Risk)) ],
     [ assert(proposal(reduce_speed, Target, Risk, road_hazard,
                       'Weather conditions (fuzzy) require a reduced speed')) ]).

% Com chuva, duas leituras de TTC abaixo de 3 s em 2 s bastam para travar. Uma leitura só conta
% quando foi medida à chuva: uma leitura de chuva até 400 ms dela (coincides[400ms]).
rule('Persistent low TTC in rain increases conservatism',
     560,
     [ weather(rain, _, _),
       test(persistent_low_ttc_in_rain(3.0, Count)),
       test(Count >= 2) ],
     [ assert(proposal(brake, 15.0, high, collision_avoidance,
                       'Rain + persistent low TTC => conservative braking')) ]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Sinal STOP

% A aproximação só começa acima da curva de 2.0 m/s2 que termina 2 m antes do sinal (a
% desaceleração mais fraca do atuador), e mantém-se até à linha: assim a regra não se desliga
% sempre que a travagem põe o veículo abaixo da curva. A ação segue a distância de paragem
% confortável (2.8 m/s2): quando esta já não para antes da linha, travar a fundo, até voltar a
% haver 1 m de folga. A velocidade recomendada é a atual (dentro do limite), não a da curva,
% que o atuador não conseguiria seguir.
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

% Na linha o veículo para e fica parado durante 1,2 s. A contagem começa quando a velocidade
% desce abaixo de 0,6 km/h; o instante de início fica guardado no estado entre ciclos.
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

% Enquanto a paragem não tiver durado 1,2 s. A condição lê o tempo, não só stop_completed, para
% não depender de qual das duas regras dispara primeiro. Se o sinal for atingido depressa demais
% (visto pela primeira vez já com a linha para trás), o mesmo teste da aproximação trava a fundo
% e para o mais cedo possível, continuando a dever a paragem completa (art. 21.º do CE, sinal B2).
rule('Stop sign requires full stop near line (emergency)',
     620,
     [ stop_sign(S, D),
       test(D =< 4.0),
       not(stop_completed(S)),
       time_ms(Now),
       not(test(stop_hold_complete(S, Now))),
       ego_speed(V),
       test(hard_braking_for_stop(V, D)) ],
     [ assert(proposal(emergency_brake, 0.0, high, legal_mandatory_stop,
                       'Stop sign passed at speed; stop as soon as possible')) ]).

rule('Stop sign requires full stop near line',
     620,
     [ stop_sign(S, D),
       test(D =< 4.0),
       not(stop_completed(S)),
       time_ms(Now),
       not(test(stop_hold_complete(S, Now))),
       ego_speed(V),
       not(test(hard_braking_for_stop(V, D))) ],
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

% Um limite mais baixo à frente ainda não está em vigor, mas o veículo tem de chegar ao sinal já
% a essa velocidade. Longe do sinal, a velocidade de aproximação está acima de qualquer
% velocidade e a regra não se aplica; a partir daí, o alvo segue a velocidade de aproximação até
% ao novo limite.
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

% Margem morta de 0,1 km/h, a mesma do atuador: "excede" e "cumpre" são complementos
% exatos.
rule('Reduce speed when exceeding detected limit',
     300,
     [ ego_speed(V),
       speed_limit(Limit),
       test(V > Limit + 0.1) ],
     [ assert(proposal(reduce_speed, Limit, moderate, speed_compliance,
                       'Vehicle exceeds current legal speed limit')) ]).

% Um limite sinalizado acima da velocidade atual é um alvo até ao qual o veículo pode subir. O
% limite por omissão não é um sinal e nunca é um alvo.
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

% Pedido de aceleração do condutor: aceite durante 1,5 s. Uma proposta de conforto só se aplica
% quando nenhum teto, retenção ou velocidade de manobra vigora no ciclo, o que a arbitragem
% resolve, por isso a regra não precisa de condição.
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

% Com prioridade de manobra: qualquer regra que ainda exija a paragem ganha; o "avançar" mais
% lento de um STOP ganha o empate. Cede durante um pedido de mudança de faixa do condutor: o
% pedido do condutor é atendido primeiro. Um veículo a contornar um veículo parado não precisa
% de ceder: a mudança de faixa é arbitrada no seu próprio canal e arranca ao mesmo tempo.
rule('Move off when stopped and nothing holds the vehicle',
     -90,
     [ test(moving_off),
       speed_limit(Limit),
       not(lane_change_request(_)),
       test(Target is min(30.0, Limit)) ],
     [ assert(proposal(continue, Target, low, maneuver_safety,
                       'Vehicle was stopped and no rule holds it any more')) ]).

% Só quando não foi feita nenhuma proposta de velocidade (longitudinal): uma proposta de faixa,
% sozinha, não conta, como no Drools, onde a prioridade do contexto só segue as propostas de
% velocidade.
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
    side_clear(Side),
    room_to_follow(Side).

% Nenhuma mudança de faixa em movimento.
maneuver_idle :-
    known(lane_change_state(idle)).

% A linha a cruzar não é contínua: entre RIGHT e CENTER, a marcação direita (right_marking);
% entre CENTER e LEFT, a marcação esquerda (left_marking).
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

% O radar frontal não vê veículo à frente na faixa desse lado, ou vê um longe e lento o
% suficiente para ser seguido depois da mudança (StoppingPolicy.roomToFollow): durante a
% manobra o intervalo encolhe, e no fim o ego tem de conseguir abrandar para a velocidade
% dele a followingGapMeters, travando à desaceleração de antecipação. Duas cláusulas, sem
% corte, porque o meta-interpretador prova o corpo conjunto a conjunto.
room_to_follow(Side) :-
    side_lane_vehicle(Side, D, Rel),
    room_to_follow_behind(D, Rel).
room_to_follow(Side) :-
    \+ side_lane_vehicle(Side, _, _).

side_lane_vehicle(left, D, Rel) :-
    known(left_lane_vehicle(_, D, Rel)).
side_lane_vehicle(right, D, Rel) :-
    known(right_lane_vehicle(_, D, Rel)).

room_to_follow_behind(D, Rel) :-
    known(ego_speed(V)),
    param(followingGapMeters, Gap),
    param(laneChangeSeconds, T),
    deceleration(anticipation, A),
    Ego is max(0.0, V) / 3.6,
    Front is max(0.0, Ego - Rel),
    GapAfter is D - max(0.0, Rel) * T,
    Room is max(0.0, GapAfter - Gap),
    Ego =< sqrt(Front * Front + 2.0 * A * Room).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Predicados auxiliares (chamados a partir de test/1)

% A faixa duas para a esquerda (LEFT, vista de RIGHT) está livre mais de 20 m para lá de um
% veículo D metros à frente na faixa da esquerda: nenhum veículo lá ao alcance do radar, ou
% está mais longe. Drools: FarLeftLaneVehicleState($farLeft) com $farLeft > $distance + 20.0.
far_left_clear_beyond(D) :-
    (   known(far_left_lane_vehicle(_, Far, _))
    ->  Far > D + 20.0
    ;   true
    ).

% Uma mudança de faixa pedida pelo condutor não é desfeita logo: a faixa estabilizou até 4,5 s
% depois do pedido (1,5 s para ser concedido + 3 s de manobra), por isso o manter-se à direita
% espera 5 s.
driver_lane_change_hold(Now) :-
    known(lane_request_at(Requested)),
    known(lane_settled_at(Settled)),
    Requested >= 0,
    Requested =< Settled,
    Requested >= Settled - 4500,
    Now - Settled < 5000.

% O lado foi lido depois de a última mudança de faixa estabilizar: durante um ou dois ticks
% depois de estabilizar, o sensor de ângulo morto (a cada 200 ms) ainda descreve os lados da
% faixa de onde veio. Drools: LaneOccupancyState(timestamp > $settled), com $settled o
% laneSettledAtMillis.
fresh_side_reading(Side) :-
    known(lane_occupancy_read(Side, Read)),
    known(lane_settled_at(Settled)),
    Read > Settled.

% Distância necessária para parar a partir de V km/h (StoppingPolicy.requiredDistanceMeters):
% tempo de reação + travagem + afastamento da linha de paragem.
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

% Ainda antes da linha (com tolerância), a 125 m ou menos, e já dentro da distância de que
% precisa a desaceleração de antecipação.
red_light_approach(D, V) :-
    D =< 125.0,
    \+ at_stop_line(D),
    stopping_distance(V, anticipation, Needed),
    D =< Needed.

at_stop_line(D) :-
    param(redLightStopLineOffsetMeters, Offset),
    param(stopLineToleranceMeters, Tolerance),
    D =< Offset + Tolerance.

% Metros que faltam a uma paragem confortável para parar na linha.
braking_shortfall(V, D, Shortfall) :-
    param(redLightStopLineOffsetMeters, Offset),
    braking_distance(V, comfort, Braking),
    Shortfall is Braking - (D - Offset).

% Antes da linha: faltam mais de 2 m a uma paragem confortável. Depois da linha: uma paragem
% confortável não cabe antes do semáforo (descontada a tolerância).
critical_stop_at_light(V, D) :-
    param(redLightStopLineOffsetMeters, Offset),
    param(stopLineToleranceMeters, Tolerance),
    braking_distance(V, comfort, Braking),
    ToLine is D - Offset,
    (   ToLine > 0.0
    ->  Braking - ToLine > 2.0
    ;   Braking > max(0.0, D - Tolerance)
    ).

% A curva de travagem a Deceleration m/s2 que termina 18 m antes do peão.
above_pedestrian_curve(V, D, Deceleration) :-
    V / 3.6 > sqrt(2.0 * Deceleration * max(0.0, D - 18.0)).

% A velocidade (m/s) do veículo da frente, a partir da do ego e da relativa.
front_vehicle_speed(V, Rel, Front) :-
    Front is max(0.0, V / 3.6 - Rel).

front_vehicle_stopped(V, Rel) :-
    front_vehicle_speed(V, Rel, Front),
    Front < 0.5.

% Velocidade (km/h) a partir da qual 2.0 m/s2 iguala a velocidade do veículo da frente 12 m
% atrás dele.
following_speed(V, D, Rel, Target) :-
    deceleration(anticipation, A),
    front_vehicle_speed(V, Rel, Front),
    Target is sqrt(Front * Front + 2.0 * A * max(0.0, D - 12.0)) * 3.6.

% A aproximar-se acima da velocidade de seguimento, ou parado (ou quase) atrás de um veículo
% parado a 14 m ou menos.
unsafe_following(V, D, Rel) :-
    (   Rel > 0.3,
        following_speed(V, D, Rel, Target),
        V > Target
    ->  true
    ;   D =< 14.0,
        Rel > -0.3,
        front_vehicle_stopped(V, Rel)
    ).

% Dentro do intervalo de 12 m atrás de um veículo da frente em movimento, sem que o intervalo
% esteja a abrir depressa.
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

% O estado da ultrapassagem depois das alterações concluídas neste ciclo. Uma ultrapassagem que
% começa agora prevalece; senão, cancelada ou terminada desativa-a; passed marca o alvo como
% ultrapassado.
current_overtaking(Active, Origin, Passed, Start, Target) :-
    effective_overtaking(Active0, Origin0, Passed0, Start0, Target0),
    Active = Active0, Origin = Origin0, Passed = Passed0, Start = Start0, Target = Target0.

% Quando duas regras de ultrapassagem começam no mesmo ciclo, prevalece a que disparou em
% último lugar, como no Drools, onde ganha o último modify do facto de estado.
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

% Pode começar a ultrapassar Target a partir de Lane: nenhuma ultrapassagem ativa, ou outro
% alvo, ou (só para o veículo da frente) outra faixa de origem.
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

% A velocidade que o ego pretende: o limite sinalizado ou, sem sinal, a velocidade de cruzeiro.
desired_speed(Desired) :-
    known(speed_limit(Limit)),
    (   known(no_speed_sign),
        known(cruise_speed(Cruise)),
        Cruise > 0
    ->  Desired is min(Cruise, Limit)
    ;   Desired = Limit
    ).

% O veículo da faixa da esquerda está em movimento (1,4 m/s ou mais) e o ego está perto dele
% ou acima da curva.
must_not_pass_on_right(V, D, Rel) :-
    Left is V / 3.6 - Rel,
    Left >= 1.4,
    (   D =< 37.0
    ->  true
    ;   left_vehicle_curve(V, D, Rel, Curve),
        V > Curve
    ).

% Velocidade (km/h) que iguala a velocidade do veículo da faixa da esquerda 35 m atrás dele,
% a 2.0 m/s2.
left_vehicle_curve(V, D, Rel, Curve) :-
    deceleration(anticipation, A),
    Left is V / 3.6 - Rel,
    Curve is sqrt(Left * Left + 2.0 * A * max(0.0, D - 35.0)) * 3.6.

% Uma linha contínua num lado da faixa do ego: a marcação direita fica entre CENTER e RIGHT,
% a esquerda entre LEFT e CENTER (WorldState.getMarkingBetween).
continuous_line_closes_lane(center) :-
    (   known(left_marking(continuous))
    ;   known(right_marking(continuous))
    ), !.
continuous_line_closes_lane(right) :-
    known(right_marking(continuous)).
continuous_line_closes_lane(left) :-
    known(left_marking(continuous)).

% Metros até ao ponto de paragem, StoppingPolicy.OBSTACLE_STOP_OFFSET_METERS antes do
% obstáculo.
obstacle_margin(D, Margin) :-
    param(obstacleStopOffsetMeters, Offset),
    Margin is max(0.0, D - Offset).

% Distância a partir da qual se para por uma barreira fechada na faixa do ego: a distância de
% paragem confortável a V km/h (reação + travagem), nunca menos de 55 m.
barrier_stop_window(V, W) :-
    deceleration(comfort, A),
    param(reactionTimeSeconds, Reaction),
    Vms is max(0.0, V) / 3.6,
    W is max(55.0, Vms * Reaction + Vms * Vms / (2.0 * A)).

% A travagem confortável (com 10% de folga) já não para o veículo antes da barreira.
barrier_hard_braking(V, D) :-
    deceleration(comfort, A),
    Vms is max(0.0, V) / 3.6,
    Vms * Vms / (2.0 * max(0.1, D)) > A * 1.1.

% A travagem confortável (com margem) já não para o veículo 6 m antes de um peão D m à frente:
% a distância de emergência das regras de peões.
pedestrian_hard_braking(V, D) :-
    deceleration(comfort, A),
    Vms is max(0.0, V) / 3.6,
    Vms * Vms / (2.0 * max(0.5, D - 6.0)) > A * 1.1.

% Distância a partir da qual se para por um obstáculo na faixa do ego: a distância de paragem
% confortável a V km/h (reação + travagem + o afastamento), nunca menos de 60 m.
obstacle_stop_window(V, W) :-
    obstacle_stopping_distance(V, D),
    W is max(60.0, D).

obstacle_stopping_distance(V, D) :-
    deceleration(comfort, A),
    param(reactionTimeSeconds, Reaction),
    param(obstacleStopOffsetMeters, Offset),
    Vms is max(0.0, V) / 3.6,
    D is Vms * Reaction + Vms * Vms / (2.0 * A) + Offset.

% Uma mudança de faixa iniciada agora sai da faixa antes de um perigo D metros à frente: o
% espaço percorrido durante a reação e até o veículo sair da sua faixa, mais o comprimento do
% próprio perigo (StoppingPolicy.roomToLeaveLane).
room_to_leave_lane(V, D) :-
    param(reactionTimeSeconds, Reaction),
    param(laneExitSeconds, Exit),
    param(hazardLengthMeters, Length),
    Vms is max(0.0, V) / 3.6,
    D >= Vms * (Reaction + Exit) + Length.

% Distância a partir da qual se aborda uma barreira fechada na trajetória: a distância de
% paragem confortável a V km/h mais o afastamento de 10 m, nunca menos de 55 m.
barrier_approach_window(V, W) :-
    deceleration(comfort, A),
    param(reactionTimeSeconds, Reaction),
    param(barrierStopOffsetMeters, Offset),
    Vms is max(0.0, V) / 3.6,
    W is max(55.0, Vms * Reaction + Vms * Vms / (2.0 * A) + Offset).

% Metros até ao ponto de paragem, StoppingPolicy.BARRIER_STOP_OFFSET_METERS antes da
% barreira.
barrier_margin(D, Margin) :-
    param(barrierStopOffsetMeters, Offset),
    Margin is max(0.0, D - Offset).

% Distância a partir da qual se abranda por um buraco na trajetória: o abrandamento confortável
% até à velocidade de passagem (reação + travagem), nunca menos de 40 m
% (StoppingPolicy.slowingDistanceMeters).
pothole_slow_window(V, W) :-
    param(potholeCrossingSpeedKmh, Crossing),
    deceleration(comfort, A),
    param(reactionTimeSeconds, Reaction),
    Vms is max(0.0, V) / 3.6,
    Cms is min(Vms, Crossing / 3.6),
    W is max(40.0, Vms * Reaction + (Vms * Vms - Cms * Cms) / (2.0 * A)).

% Velocidade pedida D metros antes de um buraco: 20 km/h nos últimos 15 m, a velocidade de
% passagem dentro dos 40 m e, para lá disso, a curva de antecipação que chega à velocidade de
% passagem no buraco.
pothole_target(D, Target) :-
    param(potholeCrossingSpeedKmh, Crossing),
    (   D < 15.0
    ->  Target = 20.0
    ;   D > 40.0
    ->  deceleration(anticipation, A),
        Cms is Crossing / 3.6,
        Target is max(Crossing, sqrt(Cms * Cms + 2.0 * A * D) * 3.6)
    ;   Target = Crossing
    ).

% O buraco está a ser contornado agora: dentro dos 50 m a que o desvio é decidido, com espaço
% para sair da faixa antes dele e um lado que é uma passagem segura (once: uma só solução, como
% no Drools).
pothole_avoidable_now(V, D) :-
    D =< 50.0,
    room_to_leave_lane(V, D),
    once(( safe_to_change_lane(left) ; safe_to_change_lane(right) )).

% Um buraco grave, e a travagem confortável (com 10% de folga) já não leva o veículo de V km/h
% à velocidade de passagem nos D metros que faltam.
pothole_hard_braking(V, D, high) :-
    param(potholeCrossingSpeedKmh, Crossing),
    deceleration(comfort, A),
    Vms is max(0.0, V) / 3.6,
    Cms is Crossing / 3.6,
    Vms > Cms,
    (Vms * Vms - Cms * Cms) / (2.0 * max(0.1, D)) > A * 1.1.

% Porque é que se abranda pelo buraco em vez de o contornar, tal como o Drools o formula.
pothole_reason(_, D, 'Pothole ahead; coming down to its crossing speed until a way round is decided (Art. 24 CE)') :-
    D > 50.0, !.
pothole_reason(V, D, 'Pothole too close to go round; crossing it slowly (Art. 24 CE)') :-
    \+ room_to_leave_lane(V, D), !.
pothole_reason(_, _, 'No free lane to go round the pothole; crossing it slowly (Art. 24 CE)').

% A travagem confortável levaria o ego mais de um comprimento de veículo para lá do veículo D
% metros à frente, de que se aproxima a Rel m/s (StoppingPolicy.closingDistanceMeters face ao
% intervalo).
cannot_stay_behind(D, Rel) :-
    deceleration(comfort, A),
    param(reactionTimeSeconds, Reaction),
    param(hazardLengthMeters, Length),
    Closing is max(0.0, Rel),
    Closing * Reaction + Closing * Closing / (2.0 * A) > D + Length.

% A desaceleração necessária para parar dentro da margem excede a travagem confortável em 10%.
obstacle_braking_excessive(V, Margin) :-
    deceleration(comfort, A),
    Vms is V / 3.6,
    Vms * Vms / (2.0 * Margin) > A * 1.1.

% Velocidade (km/h) na curva de travagem de antecipação (2.0 m/s2) que para dentro da margem:
% travar à taxa confortável em direção a ela mantém o veículo abaixo da curva confortável.
obstacle_curve(Margin, Target) :-
    deceleration(anticipation, A),
    Target is sqrt(2.0 * A * Margin) * 3.6.

% StoppingPolicy.approachSpeedKmh: a velocidade máxima a que o veículo ainda pode ir D metros
% antes de um sinal que indica Limit, para que um abrandamento suave o leve ao limite com uma
% distância de reserva antes do sinal.
approach_speed(Limit, D, Speed) :-
    deceleration(anticipation, A),
    param(speedLimitApproachBufferMeters, Buffer),
    LimitMps is max(0.0, Limit) / 3.6,
    Room is max(0.0, D - Buffer),
    Speed is sqrt(LimitMps * LimitMps + 2.0 * A * Room) * 3.6.

% A regra aplica-se assim que a velocidade de aproximação fica a 1 km/h ou menos da velocidade
% atual; o alvo é essa velocidade de aproximação, nunca acima do limite em vigor.
limit_ahead_target(Next, SignPosition, Position, V, Limit, Target) :-
    D is SignPosition - Position,
    approach_speed(Next, D, Approach),
    Approach =< V + 1.0,
    Target is min(Limit, Approach).

limit_ahead_reason(Next, Reason) :-
    format(atom(Reason), 'Speed limit ~w km/h ahead; slowing to reach it at the sign', [Next]).

% STOP: curvas de travagem que terminam 2 m antes do sinal.
above_stop_curve(V, D) :-
    deceleration(anticipation, A),
    V / 3.6 > sqrt(2.0 * A * max(0.0, D - 2.0)).

% Velocidade (km/h) na curva de travagem confortável a D metros do sinal.
stop_comfort_curve(D, Curve) :-
    deceleration(comfort, A),
    Curve is sqrt(2.0 * A * max(0.0, D - 2.0)) * 3.6.

% Metros que uma paragem confortável ainda deixaria por fazer para lá da margem de 2 m.
stop_shortfall(V, D, Shortfall) :-
    braking_distance(V, comfort, Braking),
    Shortfall is Braking - max(0.0, D - 2.0).

% Travar a fundo: uma paragem confortável termina mais de 1 m depois da linha, ou já estava em
% curso uma travagem a fundo por um STOP e ainda não há 1 m de folga.
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

% Agregação sobre a janela de 2 s (accumulate do Drools).
persistent_critical_ttc(Count, Minimum) :-
    ttc_window_ms(Window),
    findall(T, ( known(ttc_sample(_, T, Age)), T > 0.0, T < 2.5, Age < Window ), Ts),
    length(Ts, Count),
    Count > 0,
    min_list(Ts, Minimum).

% Leituras de TTC entre 0 e Threshold s na janela de 2 s.
persistent_low_ttc(Threshold, Count) :-
    ttc_window_ms(Window),
    aggregate_all(count,
                  ( known(ttc_sample(_, T, Age)), T > 0.0, T < Threshold, Age < Window ),
                  Count).

% A mesma contagem, das leituras com que coincide uma leitura de chuva.
persistent_low_ttc_in_rain(Threshold, Count) :-
    ttc_window_ms(Window),
    rain_coincidence_ms(Coincidence),
    aggregate_all(count,
                  ( known(ttc_sample(_, T, Age)), T > 0.0, T < Threshold, Age < Window,
                    once(( known(rain_sample(RainAge)), allen_coincides(Age, RainAge, Coincidence) )) ),
                  Count).

% Relações de Allen entre eventos pontuais (CEP do Drools), sobre os seus instantes em ms:
% after[Min]: Later ocorre pelo menos Min ms depois de Earlier; coincides[Max]: no máximo Max ms
% de diferença.
allen_after(Later, Earlier, Min) :-
    Later - Earlier >= Min.
allen_coincides(T1, T2, Max) :-
    abs(T1 - T2) =< Max.

% A decisão do ciclo anterior tinha, no máximo, a prioridade Name.
previous_priority_at_most(Name) :-
    priority_value(Name, Threshold),
    known(previous_priority(P)),
    P =< Threshold.

% O arranque está ativo depois das regras de estado deste ciclo.
moving_off :-
    known(move_off_started(_)), !.
moving_off :-
    \+ known(move_off_ended),
    known(move_off_state(true, _)).

% A fase de aproximação ao peão depois das regras de estado deste ciclo. A fase é calculada
% primeiro com um argumento não instanciado, para que os cortes a fixem antes de ser comparada
% com a pedida (pedestrian_phase(braking) tem de falhar, e não cair no estado guardado, quando
% este ciclo terminou a travagem).
pedestrian_phase(Phase) :-
    settled_pedestrian_phase(Settled),
    Phase = Settled.

settled_pedestrian_phase(braking) :-
    known(pedestrian_braking_started(_)), !.
settled_pedestrian_phase(none) :-
    known(pedestrian_approach_over), !.
settled_pedestrian_phase(holding) :-
    known(pedestrian_braking_ended), !.
settled_pedestrian_phase(Phase) :-
    known(pedestrian_braking_state(Phase, _)).

% Um peão em conflito está a ser abordado a mais de Min metros.
pedestrian_approached(Min) :-
    known(pedestrian_conflict(P)),
    known(pedestrian(P, D)),
    D > Min.

% Uma proposta no canal longitudinal (velocidade): tudo menos uma ação de faixa.
speed_proposal :-
    known(proposal(Action, _, _, _, _)),
    \+ memberchk(Action, [keep_lane, change_lane_left, change_lane_right, return_right]), !.

% Lógica difusa: as funções de pertença e o centroide de FuzzySupport.java.

% Os graus de uma leitura de visibilidade ou de aderência (limitada a 0..1) nos quatro termos
% de entrada.
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

% Termos de saída, do mais restritivo para o menos restritivo (os empates no termo mais forte
% ficam para o primeiro).
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

% O teto, quando se verifica um termo restritivo (qualquer um exceto light): o centroide da
% saída agregada, amostrado a cada 0,5 km/h de 0 a 130 km/h.
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

% Retido atrás de tráfego lento, tal como o ciclo o deixa: começou num ciclo anterior e não foi
% desbloqueado neste.
current_boxed_in(Since) :-
    \+ known(boxed_in_cleared),
    known(boxed_in(_, Since)).
