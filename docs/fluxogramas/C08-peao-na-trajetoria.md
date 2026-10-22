# C08 — Peão na trajetória ou a atravessar

- **Branch / commit:** working tree a 21/10/2026
- **Estado da informação:** confirmado pelo código, por testes e em execução.

## Fluxograma

```mermaid
flowchart TD
    C08_S(["Início: a câmara deteta um peão à frente,<br/>a mais de 0 m e até 120 m"]) --> C08_DR{"R7: há um peão em conflito<br/>a mais de 32 m?"}
    C08_DR -- "Não" --> C08_AR["R7: fase da aproximação = NONE<br/>(se ainda não for)"]
    C08_DR -- "Sim" --> C08_D6
    C08_AR --> C08_D6{"crossingState == CROSSED?<br/>o peão terminou a travessia"}
    C08_D6 -- "Sim" --> C08_X2["Nenhuma regra dispara:<br/>a travessia está livre, o veículo retoma"]
    C08_D6 -- "Não" --> C08_D0{"O peão está em conflito?<br/>inPath == true OU<br/>crossingState == CROSSING"}
    C08_D0 -- "Não" --> C08_X0["Nenhuma regra dispara:<br/>o peão fica só a ser seguido"]
    C08_D0 -- "Sim" --> C08_D1{"distância ≤ 6,0 m?"}
    C08_D1 -- "Sim" --> C08_P1["R3 propõe EMERGENCY_BRAKE<br/>velocidade 0; risco CRITICAL<br/>prioridade 70 EMERGENCY_SAFETY"]
    C08_D1 -- "Não" --> C08_D10{"R8: travar até 6 m antes do peão<br/>pede mais de 2,8 x 1,1 m/s²?<br/>v² / 2 x max 0,5, d - 6"}
    C08_D10 -- "Sim" --> C08_P6["R8 propõe EMERGENCY_BRAKE<br/>velocidade 0; risco CRITICAL<br/>prioridade 70 EMERGENCY_SAFETY<br/>(as regras de 60 abaixo também propõem, mas perdem)"]
    C08_D10 -- "Não" --> C08_D2{"distância #gt; 6,0 m E ≤ 32,0 m?"}
    C08_D2 -- "Sim" --> C08_P2["R1 propõe STOP<br/>velocidade 0; risco HIGH<br/>prioridade 60 LEGAL_MANDATORY_STOP"]
    C08_D2 -- "Não" --> C08_D7{"crossingState == CROSSING E<br/>veículo parado (#lt; 1 km/h) E<br/>distância ≤ 45,0 m?"}
    C08_D7 -- "Sim" --> C08_P5["R4 propõe STOP<br/>velocidade 0; risco HIGH<br/>prioridade 60 LEGAL_MANDATORY_STOP"]
    C08_D7 -- "Não" --> C08_D5{"R5: fase ≠ BRAKING E velocidade acima<br/>da curva de 2,0 m/s² que termina 18 m antes do peão?"}
    C08_D5 -- "Sim" --> C08_A5["R5: fase = BRAKING"]
    C08_D5 -- "Não" --> C08_D8{"R6: fase == BRAKING E velocidade<br/>na curva de 1,4 m/s² ou abaixo?"}
    C08_D8 -- "Sim" --> C08_A6["R6: fase = HOLDING"]
    C08_D8 -- "Não" --> C08_D9
    C08_A5 --> C08_D9{"R2: fase da aproximação"}
    C08_A6 --> C08_D9
    C08_D9 -- "BRAKING" --> C08_P3["R2 propõe BRAKE<br/>velocidade 20; risco HIGH<br/>prioridade 60 LEGAL_MANDATORY_STOP"]
    C08_D9 -- "HOLDING" --> C08_P4["R2 propõe MAINTAIN_SPEED<br/>velocidade atual; risco MODERATE<br/>prioridade 60 LEGAL_MANDATORY_STOP"]
    C08_D9 -- "NONE" --> C08_D3{"velocidade acima da curva<br/>de 1,4 m/s² que termina<br/>18 m antes do peão?"}
    C08_D3 -- "Sim" --> C08_P4
    C08_D3 -- "Não" --> C08_X1["Nenhuma regra dispara:<br/>ainda há espaço para travar mais tarde"]
    C08_P1 --> C08_M2[["Consultar M02 — Arbitragem de propostas"]]
    C08_P6 --> C08_M2
    C08_P2 --> C08_M2
    C08_P3 --> C08_M2
    C08_P4 --> C08_M2
    C08_P5 --> C08_M2
    C08_X0 --> C08_M2
    C08_X1 --> C08_M2
    C08_X2 --> C08_M2
    C08_M2 --> C08_M3[["Consultar M03 — Atuador"]]
    C08_M3 --> C08_D4{"Sem evento de peão há mais de 1000 ms?"}
    C08_D4 -- "Sim" --> C08_A["Clear stale pedestrian perception:<br/>pedestrianId = none; inPath = false;<br/>crossingState = WAITING"]
    C08_A --> C08_C4[["Se o veículo parou: consultar C04 — Arranque após paragem"]]
    C08_D4 -- "Não" --> C08_E(["Fim: reavaliado no tick seguinte"])
    C08_C4 --> C08_E

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef prop fill:#d4edda,stroke:#2e7d32,color:#000
    classDef est fill:#e9d8fd,stroke:#6b46c1,color:#000
    classDef nao fill:#eeeeee,stroke:#888888,color:#000
    classDef ref fill:#d6e4ff,stroke:#1a56db,color:#000
    class C08_DR,C08_D0,C08_D1,C08_D2,C08_D3,C08_D4,C08_D5,C08_D6,C08_D7,C08_D8,C08_D9,C08_D10 dec
    class C08_P1,C08_P2,C08_P3,C08_P4,C08_P5,C08_P6 prop
    class C08_A,C08_AR,C08_A5,C08_A6 est
    class C08_X0,C08_X1,C08_X2 nao
    class C08_M2,C08_M3,C08_C4 ref
```

R1 = `Stop for pedestrian in crossing zone`; R2 = `Brake for pedestrian proximity`; R3 = `Emergency stop for pedestrian extremely close`; R4 = `Hold for pedestrian still crossing`; R5 = `Start braking for pedestrian above the braking curve`; R6 = `Hold speed for pedestrian once under the lower curve`; R7 = `End the pedestrian approach once no pedestrian is approached`; R8 = `Emergency brake for pedestrian when comfortable braking no longer stops in time`.

As regras são independentes no motor. R1, R2 e R3 têm intervalos de distância disjuntos; R8 sobrepõe-se a R1 e R2 (qualquer distância acima de 6 m) e ganha-lhes pela prioridade 70 sempre que a travagem confortável, com 10 % de margem, já não pára o veículo 6 m antes do peão (a 90 km/h um peão a 90 m, onde a travagem confortável precisa de 110 m); R4 pode disparar no mesmo intervalo de R2, mas só com o veículo parado. R5, R6 e R7 não propõem: guardam a fase da aproximação (`PedestrianBrakingState`: `NONE`, `BRAKING`, `HOLDING`) e disparam antes de R2 (salience 607 contra 605), por isso R2 lê a fase já atualizada no ciclo. O fluxograma mostra-as em sequência apenas por clareza.

## Os três estados do peão

| Estado | Como é percebido | Efeito |
|---|---|---|
| Sem conflito | `inPath == false` e `crossingState == WAITING`: está na faixa de rodagem, fora da trajetória e sem atravessar | Fica só a ser seguido |
| A atravessar | `crossingState == CROSSING`: está a atravessar a via e ainda não chegou ao outro lado, em qualquer faixa | O veículo cede passagem enquanto durar. Sair da faixa do veículo não termina a travessia |
| Travessia terminada | `crossingState == CROSSED`: chegou ao passeio do outro lado | As regras de peão deixam de segurar o veículo |

Um peão na trajetória (`inPath == true`) está em conflito quer esteja a atravessar quer não.

## Situações a resolver

Não há nós a vermelho neste cenário. Ver `relatorio-validacao.md`, secção 12 (relatório que não está no repositório).

## Tabela de decisões

| Nó | Regra | Condição real | Caminho verdadeiro | Caminho falso | Resultado |
|---|---|---|---|---|---|
| C08_D6 | `Update pedestrian state` | `PedestrianDetectedEvent.crossingState == CROSSED` copiado para o facto | C08_X2 | C08_D0 | Travessia livre |
| C08_DR | R7 | `PedestrianBrakingState(phase != NONE)` e `not PedestrianStateFact((inPath == true \|\| crossingState == CROSSING) && distanceMeters > 32.0)` | C08_D6 | C08_AR | A aproximação acaba quando o peão desaparece, deixa de estar em conflito ou entra nos 32 m (decide R1) |
| C08_D0 | R1, R2, R3 | `PedestrianStateFact(inPath == true \|\| crossingState == PedestrianCrossingState.CROSSING)` | C08_D1 | C08_X0 | — |
| C08_D1 | R3 | `distanceMeters <= 6.0` | C08_P1 | C08_D2 | Emergência |
| C08_D10 | R8 | `PedestrianStateFact(inPath == true \|\| crossingState == CROSSING, distanceMeters > 6.0)` e `($speed / 3.6)² / (2.0 * Math.max(0.5, $distance - 6.0)) > StoppingPolicy.COMFORT_DECELERATION_MPS2 * 1.1` | C08_P6 | C08_D2 | Travagem forte já, em vez de esperar pelos 6 m |
| C08_D2 | R1 | `distanceMeters > 6.0, distanceMeters <= 32.0` | C08_P2 | C08_D7 | Paragem |
| C08_D7 | R4 | `VehicleStateFact(speedKmh < 1.0)` e `PedestrianStateFact(crossingState == PedestrianCrossingState.CROSSING, distanceMeters > 32.0, distanceMeters <= 45.0)` | C08_P5 | C08_D3 | Continua parado |
| C08_D5 | R5 | `PedestrianBrakingState(phase != BRAKING)`, peão em conflito a mais de 32 m e `$speed / 3.6 > Math.sqrt(2.0 * 2.0 * Math.max(0.0, $distance - 18.0))` | C08_A5 | C08_D8 | Começa a travar acima da curva de cima |
| C08_D8 | R6 | `PedestrianBrakingState(phase == BRAKING)`, peão em conflito a mais de 32 m e `$speed / 3.6 <= Math.sqrt(2.0 * 1.4 * Math.max(0.0, $distance - 18.0))` | C08_A6 | C08_D9 | Só deixa de travar abaixo da curva de baixo |
| C08_D9 | R2 | `PedestrianBrakingState($phase : phase)` | C08_P3 (`BRAKING`), C08_P4 (`HOLDING`) | C08_D3 (`NONE`) | — |
| C08_D3 | R2 | `$phase == NONE` e `$speed / 3.6 > Math.sqrt(2.0 * 1.4 * Math.max(0.0, $distance - 18.0))` | C08_P4 | C08_X1 | Um veículo que ainda não travou fica entre as curvas sem acelerar |
| C08_D4 | `Clear stale pedestrian perception` | `pedestrianId != "none"` e `$now - $timestamp > 1000` | C08_A | fim | Peão deixou de ser visto |

## Ficha técnica

**Identificação**

| Campo | Valor |
|---|---|
| ID | C08 |
| Nome | Peão na trajetória ou a atravessar |
| Objetivo | Ceder passagem a um peão que está na trajetória ou a atravessar a via, até a travessia terminar |
| Condição de ativação | `PedestrianStateFact` com `inPath` ou `crossingState == CROSSING`: a 32 m ou menos sempre; mais longe, com a velocidade acima da curva de travagem que termina 18 m antes do peão, ou acima do que a travagem confortável ainda pára 6 m antes dele |
| Dados de entrada | `PedestrianStateFact` (id, distância, `inPath`, `crossingState`); `VehicleStateFact.speedKmh`; `PedestrianBrakingState` (fase da aproximação) |

**Lógica**

| Regra | Ficheiro:linha | Salience | Ação | Prioridade |
|---|---|---|---|---|
| R1 `Stop for pedestrian in crossing zone` | `pedestrian.drl:48` | 600 | `STOP` | 60 |
| R5 `Start braking for pedestrian above the braking curve` | `pedestrian.drl:68` | 607 | — (fase = `BRAKING`) | — |
| R6 `Hold speed for pedestrian once under the lower curve` | `pedestrian.drl:80` | 607 | — (fase = `HOLDING`) | — |
| R7 `End the pedestrian approach once no pedestrian is approached` | `pedestrian.drl:94` | 607 | — (fase = `NONE`) | — |
| R2 `Brake for pedestrian proximity` | `pedestrian.drl:104` | 605 | `BRAKE` ou `MAINTAIN_SPEED` | 60 |
| R4 `Hold for pedestrian still crossing` | `pedestrian.drl:132` | 602 | `STOP` | 60 |
| R3 `Emergency stop for pedestrian extremely close` | `emergency.drl:62` | 880 | `EMERGENCY_BRAKE` | 70 |
| R8 `Emergency brake for pedestrian when comfortable braking no longer stops in time` | `emergency.drl:87` | 879 | `EMERGENCY_BRAKE` | 70 |
| `Update pedestrian state` | `01-state-updates.drl:218` | 1000 | — | — |
| `Clear stale pedestrian perception` | `01-state-updates.drl:523` | 900 | — | — |

**Como a câmara classifica o peão** (`WorldState.nearestPedestrianAhead`, `WorldState.java:219`; evento emitido em `CameraSensor.java:63`). Só contam peões ativos, à frente e dentro do alcance.

- `inPath`: o peão está na mesma faixa que o veículo, ou a 3,5 m ou menos da sua posição lateral.
- `crossingState`: `CROSSING` enquanto o peão anda de um lado ao outro da via (`Pedestrian.isCrossingInProgress`, `Pedestrian.java:74`); `WAITING` se está na faixa de rodagem sem atravessar; `CROSSED` quando chega ao outro lado.

A câmara comunica o peão em conflito mais próximo; se não houver, o peão parado mais próximo; se não houver, o que acabou de atravessar.

**Persistência da travessia.** O estado fica no `PedestrianStateFact` e só muda quando a câmara comunica outro. Enquanto for `CROSSING`, as regras continuam a segurar o veículo, esteja o peão em que faixa estiver. O peão que a câmara está a seguir só é retirado do mundo depois de a câmara o ter comunicado como `CROSSED` (`SimulationEngine.consumePassedRoadElements`), para as regras verem o fim da travessia em vez de o peão desaparecer. A interface desenha os objetos no centro da faixa; se o peão ficasse no mundo, apareceria parado na faixa junto ao passeio.

**Passadeira.** Ao colocar um peão no mundo é pintada uma passadeira no bloco onde ele atravessa (`WorldState.addActor` → `ensureCrosswalkAt`), se o bloco estiver livre. A câmara reporta a passadeira (`CrosswalkDetectedEvent`), mas nenhuma regra a lê: o comportamento depende só do facto do peão.

**Sentido da travessia** (`Pedestrian.java:18`): um peão criado na faixa esquerda atravessa para o passeio direito (+5,5 m); um peão criado na faixa central ou direita atravessa para o passeio esquerdo (−5,5 m).

**Curvas de travagem e fase da aproximação.** A aproximação segue curvas que terminam 18 m antes do peão, como a aproximação ao semáforo vermelho termina antes da linha. Acima da curva de 2,0 m/s² o veículo começa a travar (a 60 km/h, a 87 m ou menos) e continua a travar até ficar abaixo da curva de 1,4 m/s²; daí em diante segura a velocidade, sem voltar a acelerar em direção ao peão, até a curva de cima o apanhar e travar outra vez. Um veículo que ainda não travou segura a velocidade entre as duas curvas (a 60 km/h, entre 87 e 117 m) e não recebe proposta abaixo da curva de 1,4 m/s². Sem esta memória, o veículo seguia a curva de cima a alternar travar e manter em cada ciclo (14 mudanças em 4 s no S7). A 32 m entra R1. Um veículo a 60 km/h pára a cerca de 22 m de um peão que não saia. No Prolog, a fase é `pedestrian_braking/2` em `driving_state.pl`.

**Rastreabilidade**

| Elemento | Local |
|---|---|
| Factos | `drools/facts/PedestrianStateFact.java`, valor inicial em `KnowledgeSession.java:122`; `drools/facts/PedestrianBrakingState.java`, valor inicial `NONE` em `KnowledgeSession.java:131-132` |
| Evento | `drools/events/PedestrianDetectedEvent.java` |
| Modelo do peão | `domain/Pedestrian.java`, `domain/PedestrianCrossingState.java` |
| Prolog | As mesmas regras em `driving_rules.pl` (R2 com as variantes `(brake)` e `(hold speed)`; R8 com `pedestrian_hard_braking/2`); a disjunção "na trajetória ou a atravessar" é a conclusão intermédia `pedestrian_conflict/1` (regras `Pedestrian conflicts with trajectory (in path)` e `(crossing)`, precedência 1000); o peão persiste em `pedestrian_track/5` e a fase em `pedestrian_braking/2` (`driving_state.pl`) |
| Cenário de demonstração | `S7` (Pedestrian crossing: peão a 95 m na faixa CENTER a 3 km/h, ego a 60 km/h); também em `S11` (aos 11 s, a 150 m) e em `S12_PRESENTATION_DEMO` (aos 47 s, a 120 m na faixa LEFT a 2 km/h) |
| Testes | `DrivingExpertSystemRulesTest`: `pedestrianTriggersBraking` (199), `conflictGreenLightAndPedestrianPedestrianWins` (546), `pedestrianAt35MetersPrevailsOverRedLightApproach` (822), `pedestrianBetween10And20MetersStopsTheVehicleBeforeARedLight` (840), `pedestrianWithin6MetersTriggersEmergencyBrake` (859), `pedestrianFarAheadAtHighSpeedIsBrakedForHardWhenComfortableBrakingCannotStop` (873), `pedestrianFarAheadAtModerateSpeedIsBrakedForAlongTheCurve` (888), `pedestrianCrossingIntoThePathIsBrakedForBeforeItIsInThePath` (903), `visiblePedestrianWithoutConflictIsOnlyMonitored` (918), `pedestrianStillCrossingOutsideTheEgoLaneKeepsTheVehicleStopped` (931), `pedestrianThatFinishedCrossingNoLongerHoldsTheVehicle` (954), `crossingPedestrianWithin6MetersTriggersEmergencyBrakeEvenOutsideTheEgoLane` (976), `committedToProceedAtYellowStillStopsForAPedestrian` (1084), `brakingForAPedestrianContinuesBetweenTheCurvesThenHoldsTheSpeed` (1234), `aVehicleBetweenTheCurvesThatWasNotBrakingHoldsItsSpeed` (1257); `PrologRulesTest`: `pedestrianTriggersBraking` (189), `pedestrianFarAheadAtHighSpeedIsBrakedForHard` (200), `pedestrianFarAheadAtModerateSpeedIsBrakedForAlongTheCurve` (211), `pedestrianExtremelyCloseTriggersEmergencyBrake` (221), `pedestrianNotInConflictIsOnlyTracked` (233), `brakingForAPedestrianContinuesBetweenTheCurvesThenHoldsTheSpeed` (573), `aVehicleBetweenTheCurvesThatWasNotBrakingHoldsItsSpeed` (598); `PrologDroolsEquivalenceTest`, casos "pedestrian ..."; `PedestrianConflictE2ETest` (parametrizado nos dois motores): `anticipatesPedestrianCrossingFromLeftTowardsTheEgoLane` (76), `stopsNearAPedestrianStandingInItsLane` (120), `doesNotBrakeForPedestrianInLeftLaneThatWillNotCross` (145), `neverSpeedsUpWhileThePedestrianIsStillOnTheCrossing` (170), `staysStoppedAfterThePedestrianLeavesItsLaneButIsStillCrossing` (214), `afterThePedestrianHasCrossedTheRedLightIsStillRespected` (250); `PedestrianAtSpeedE2ETest` (dois motores): `standingPedestrianAheadAtNinetyIsNotReached` (86), `pedestrianCrossingIntoTheLaneAtNinetyIsNotReached` (101), `pedestrianAheadAtFiftyIsBrakedForComfortably` (116); `OtherVehiclesGiveWayToPedestriansE2ETest.theVehicleAheadStopsForTheCrossingPedestrian` (28); `CrosswalkE2ETest`; `SimulationEngineE2ETest`: `pedestrianDynamicallyAddedOnlyDetectedWithinRangeAndTriggersSafetyRule` (510), `priorityConflictPedestrianOverridesGreenLight` (601); `RoadLawAndOvertakingE2ETest.pedestrianCrossingBrakesThenResumesAfterCrossingClears` (196) |

**Relações**

- Depois de a travessia terminar, C04 retoma o andamento; se houver um semáforo vermelho ou um STOP mais à frente, C05 ou C07 voltam a decidir (confirmado por teste para o vermelho).
- Um peão em conflito bloqueia C17 e o início das ultrapassagens de C11.
- Os outros veículos também cedem passagem (art. 103.º), mas isso é física do mundo simulado e não uma regra do sistema pericial: em `WorldPhysicsEngine.giveWayToPedestrians()`, um veículo que não é o ego trava para parar 15 m antes de um peão na sua faixa, a 3,5 m ou menos dela, ou a atravessar a via, até 60 m à frente, e retoma a sua velocidade quando o caminho fica livre. Antes atravessava o peão a velocidade constante (S11; teste `OtherVehiclesGiveWayToPedestriansE2ETest`). No S11, o carro e o peão passaram a ser criados a 150 m do ego, fora do ecrã (que mostra até ~144 m à frente), para entrarem pela direita.
- Conflitos (M02): R1, R2 e R4 têm a mesma prioridade (60) que o semáforo vermelho, o amarelo, o STOP e a barreira, e sobrepõem-se a todos os tetos de velocidade. Em empate ganha a velocidade mais baixa, por isso o peão ganha a uma aproximação que ainda permite velocidade e só perde para uma paragem mais forte. Disparam depois das regras do vermelho e do STOP: quando ambas pedem velocidade 0, fica a proposta do sinal (proposta primeiro). R3 e R8 (70) ganham a tudo; empatam com as emergências de C10 e C13, todas com velocidade 0. Uma mudança de faixa com prioridade inferior é vetada enquanto estas decisões forem uma travagem (`BRAKE`, `STOP`, `EMERGENCY_BRAKE`); com `MAINTAIN_SPEED` não é (M02).

**Limitações**

- R3 não tem limite inferior de distância nem verificação de `pedestrianId`.
- Só é considerado um peão de cada vez: o que está em conflito mais próximo.
- O veículo cede a qualquer peão a atravessar a via à frente, mesmo que este se afaste da sua faixa ou fosse acabar de atravessar antes de o veículo chegar.
- Um peão parado noutra faixa (`WAITING`) não é conflito, mesmo que esteja sobre a passadeira.
- Durante a aproximação a decisão passa de `BRAKE` a `MAINTAIN_SPEED` e de volta a `BRAKE` quando a curva de cima apanha o veículo, mas já não alterna em cada ciclo (fase guardada em `PedestrianBrakingState`); nunca acelera nesse troço.

## Exemplos de entradas

| Entrada | Caminho | Resultado |
|---|---|---|
| Peão na faixa do veículo a 40 m, veículo a 60 km/h | C08_D10 Sim (parar 6 m antes pede 4,1 m/s²) | `EMERGENCY_BRAKE` por R8 |
| Peão na faixa do veículo a 80 m, veículo a 90 km/h | C08_D10 Sim (4,2 m/s²) | `EMERGENCY_BRAKE` por R8 (teste `pedestrianFarAheadAtHighSpeedIsBrakedForHardWhenComfortableBrakingCannotStop`) |
| Peão na faixa do veículo a 80 m, veículo a 60 km/h | C08_D10 Não (1,9 m/s²); C08_D5 Sim | `BRAKE` 20 (teste `pedestrianFarAheadAtModerateSpeedIsBrakedForAlongTheCurve`) |
| Peão na faixa do veículo a 70 m, veículo a 60 km/h | C08_D10 Não (2,2 m/s²); C08_D5 Sim (curva de 2,0 m/s²: 87 m) | `BRAKE` 20 |
| Peão na faixa do veículo a 100 m, veículo a 60 km/h, ainda sem travar | C08_D5 Não (curva de 2,0 m/s²: 65 km/h); fase NONE; C08_D3 Sim (curva de 1,4 m/s²: 54,5 km/h) | `MAINTAIN_SPEED` 60 |
| Peão na faixa do veículo a 70 m, veículo a 40 km/h, ainda sem travar | C08_D3 Não (curva de 1,4 m/s²: 43,4 km/h) | Sem proposta |
| Fase `BRAKING`, peão a 60 m, veículo a 40 km/h | C08_D8 Não (curva de 1,4 m/s²: 39,0 km/h) | `BRAKE` 20 (continua a travar) |
| Fase `BRAKING`, peão a 60 m, veículo a 38 km/h | C08_D8 Sim | Fase `HOLDING`: `MAINTAIN_SPEED` 38 |
| Peão a 18 m, veículo a 50 km/h | C08_D10 Sim (8,0 m/s²) | `EMERGENCY_BRAKE` por R8 (teste `pedestrianTriggersBraking`) |
| Peão a 10, 15 e 20 m, veículo a 15 km/h, vermelho a 70 m | C08_D10 Não; C08_D2 Sim | `STOP` por R1 (teste `pedestrianBetween10And20MetersStopsTheVehicleBeforeARedLight`) |
| Peão a 32,0 m, veículo a 30 km/h | C08_D10 Não; C08_D2 Sim (limite incluído) | `STOP` |
| Peão a 6,0 m | C08_D1 Sim (limite incluído) | `EMERGENCY_BRAKE` (teste `pedestrianWithin6MetersTriggersEmergencyBrake`) |
| Peão a atravessar noutra faixa a 70 m, veículo a 60 km/h | C08_D0 Sim por `CROSSING`; C08_D5 Sim | `BRAKE` 20 (teste `pedestrianCrossingIntoThePathIsBrakedForBeforeItIsInThePath`) |
| Peão a atravessar, já fora da faixa do veículo, a 15 m; veículo parado | C08_D0 Sim por `CROSSING`; C08_D2 Sim | `STOP` (teste `pedestrianStillCrossingOutsideTheEgoLaneKeepsTheVehicleStopped`) |
| Peão a atravessar a 35 m; veículo parado | C08_D7 Sim | `STOP` por R4 (teste `pedestrianStillCrossingOutsideTheEgoLaneKeepsTheVehicleStopped`) |
| Peão a atravessar noutra faixa a 5 m | C08_D1 Sim | `EMERGENCY_BRAKE` (teste `crossingPedestrianWithin6MetersTriggersEmergencyBrakeEvenOutsideTheEgoLane`) |
| O mesmo peão comunicado como `CROSSED` | C08_D6 Sim | Sem proposta de peão (teste `pedestrianThatFinishedCrossingNoLongerHoldsTheVehicle`) |
| Peão parado na faixa esquerda, veículo na direita | C08_D0 Não | Sem proposta (teste `PedestrianConflictE2ETest.doesNotBrakeForPedestrianInLeftLaneThatWillNotCross`) |
| Peão a 35 m com semáforo vermelho a 70 m, veículo a 40 km/h | R8 não (2,1 m/s²); R2 (60, velocidade 20) contra C05 (60, velocidade atual) | `BRAKE` (teste `pedestrianAt35MetersPrevailsOverRedLightApproach`; a 60 km/h decidiria R8) |
| Peão a 14 m com semáforo verde a 20 m, veículo a 50 km/h | R8 (70) e R1 (60) contra C06 (10) | `EMERGENCY_BRAKE` por R8 (teste `conflictGreenLightAndPedestrianPedestrianWins`, que aceita qualquer travagem de uma regra de peão) |
