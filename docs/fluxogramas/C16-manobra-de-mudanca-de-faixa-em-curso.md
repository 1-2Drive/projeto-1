# C16 — Manobra de mudança de faixa em curso

- **Branch / commit:** working tree a 21/10/2026
- **Estado da informação:** confirmado pelo código e por testes de integração (regras no Drools e no Prolog).

Junta a máquina de estados física da manobra (Java) com as duas regras que mantêm a decisão enquanto o veículo se move.

## Fluxograma C16-A — Máquina de estados da manobra

```mermaid
flowchart TD
    C16_S(["Início: laneChangeState == IDLE"]) --> C16_D1{"Atuador recebeu CHANGE_LANE_LEFT, CHANGE_LANE_RIGHT<br/>ou RETURN_RIGHT e a guarda de M03 aceitou?"}
    C16_D1 -- "Não" --> C16_S
    C16_D1 -- "Sim" --> C16_D2{"requestLaneChange: estado é CHANGING ou COMPLETED,<br/>ou destino == faixa atual sem estar em REQUESTED?"}
    C16_D2 -- "Sim" --> C16_X["Pedido ignorado: mantém a manobra atual"]
    C16_D2 -- "Não" --> C16_A1["laneChangeState = REQUESTED<br/>targetLane = destino; progresso = 0"]
    C16_A1 --> C16_A2["Tick seguinte, física: laneChangeState = CHANGING<br/>progresso += 1,2 m/s x 0,1 s / distância lateral"]
    C16_A2 --> C16_D3{"progresso ≥ 1,0?"}
    C16_D3 -- "Não" --> C16_RB[["Em cada tick: consultar C16-B"]]
    C16_RB --> C16_A2
    C16_D3 -- "Sim" --> C16_A3["laneChangeState = COMPLETED<br/>posição lateral = eixo da faixa de destino"]
    C16_A3 --> C16_A4["settleManeuver, no mesmo tick:<br/>faixa do veículo = targetLane<br/>laneSettledAtMillis = agora"]
    C16_A4 --> C16_A5["Tick seguinte: normalizeCompletedManeuver<br/>laneChangeState = IDLE"]
    C16_A5 --> C16_E(["Fim: as regras que exigem IDLE voltam a poder disparar"])
    C16_X --> C16_RB

    classDef prob fill:#e53935,stroke:#7f0000,stroke-width:3px,color:#fff
    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef est fill:#e9d8fd,stroke:#6b46c1,color:#000
    classDef nao fill:#eeeeee,stroke:#888888,color:#000
    classDef ref fill:#d6e4ff,stroke:#1a56db,color:#000
    class C16_D1,C16_D2,C16_D3 dec
    class C16_A1,C16_A2,C16_A3,C16_A4,C16_A5 est
    class C16_X nao
    class C16_RB ref
```

Com 3,5 m entre eixos de faixa e 1,2 m/s, a manobra demora cerca de 2,9 s (30 ticks). As regras assumem no máximo `StoppingPolicy.LANE_CHANGE_SECONDS` = 3,0 s (`SimulationConfig.laneChangeSeconds` também 3,0 s) e que o carro sai da faixa de origem ao fim de `LANE_EXIT_SECONDS` = 2,0 s (usado em `roomToLeaveLane`).

## Fluxograma C16-B — Manutenção da decisão

```mermaid
flowchart TD
    C16B_S(["Início C16-B: cada ciclo"]) --> C16B_D1{"laneChangeState == REQUESTED ou CHANGING?"}
    C16B_D1 -- "Não" --> C16B_X["R1 e R2 não disparam"]
    C16B_D1 -- "Sim" --> C16B_F{{"R1 e R2 são avaliadas de forma independente"}}
    C16B_F --> C16B_D2{"R1: targetLane == LEFT<br/>OU faixa == RIGHT E targetLane == CENTER?"}
    C16B_D2 -- "Sim" --> C16B_P1["R1 propõe CHANGE_LANE_LEFT<br/>sem velocidade; risco LOW<br/>prioridade 30 MANEUVER_SAFETY"]
    C16B_D2 -- "Não" --> C16B_X1["R1 não dispara"]
    C16B_F --> C16B_D3{"R2: targetLane == RIGHT<br/>OU faixa == LEFT E targetLane == CENTER?"}
    C16B_D3 -- "Sim" --> C16B_P2["R2 propõe CHANGE_LANE_RIGHT<br/>sem velocidade; risco LOW<br/>prioridade 30 MANEUVER_SAFETY"]
    C16B_D3 -- "Não" --> C16B_X2["R2 não dispara"]
    C16B_P1 --> C16B_M2[["Consultar M02 — Arbitragem de propostas"]]
    C16B_P2 --> C16B_M2
    C16B_X --> C16B_M2
    C16B_X1 --> C16B_M2
    C16B_X2 --> C16B_M2
    C16B_M2 --> C16B_M3[["Consultar M03 — Atuador e guarda de segurança"]]
    C16B_M3 --> C16B_E(["Regresso a C16-A"])

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef prop fill:#d4edda,stroke:#2e7d32,color:#000
    classDef nao fill:#eeeeee,stroke:#888888,color:#000
    classDef ref fill:#d6e4ff,stroke:#1a56db,color:#000
    class C16B_D1,C16B_D2,C16B_D3 dec
    class C16B_P1,C16B_P2 prop
    class C16B_X,C16B_X1,C16B_X2 nao
    class C16B_M2,C16B_M3 ref
```

R1 = `Maintain ongoing lane change left avoidance`; R2 = `Maintain ongoing lane change right avoidance`.

## Situações a resolver

Os nós a **vermelho** nos fluxogramas acima assinalam passos onde há um problema por resolver.

| Nó | Problema | Confirmação | Como reproduzir |
|---|---|---|---|
| C16_A4 | ~~A faixa do veículo só é atualizada no fim da manobra. Durante o movimento lateral, as regras continuam a tratar o carro como estando na faixa de origem.~~ **Resolvido onde importava**: `VehicleStateFact.physicalLane` dá a faixa onde o carro está fisicamente (da posição lateral; Prolog `ego_physical_lane/1`) e a emergência por obstáculo (C13) usa-a. `VehicleStateFact.pathLane` (Prolog `ego_path_lane/1`) dá a faixa de destino enquanto a manobra não está IDLE: as aproximações a buraco, obstáculo e barreira fechada (C12, C13, C14) leem-na, por isso veem o perigo na faixa para onde o carro vai. As restantes regras continuam a usar a faixa comprometida, de propósito | Confirmado em execução a 18/10 | Caso A10 |

Os casos (A01 a A22, B01 a B08) estão descritos em `relatorio-validacao.md`, secção 8 (documento que não está neste repositório).

## Tabela de decisões

| Nó | Origem | Condição real | Caminho verdadeiro | Caminho falso | Resultado |
|---|---|---|---|---|---|
| C16_D1 | `DecisionActuator.applyLaneDecision()` 123-152 | Mudança de faixa com a manobra IDLE, aceite pela guarda (faixa alvo diferente da atual, `WorldState.isLaneSafe(alvo, alcance do ângulo morto, 60 m)` e sem linha contínua). O espaço para seguir o veículo da frente na faixa de destino já não é verificado aqui: é o subobjetivo `roomToFollow` das regras (C15) | C16_D2 | permanece IDLE | Com a manobra em curso o atuador não aceita outra |
| C16_D2 | `DecisionActuator.requestLaneChange()` 203-217 | `state == CHANGING \|\| state == COMPLETED`, ou `target == ego.getLane() && state != REQUESTED` | C16_X | C16_A1 | Não inverte uma manobra em movimento |
| C16_D3 | `WorldPhysicsEngine.advanceLaneChange()` 134-174 | `progress >= 1.0` | C16_A3 | C16-B | Fim do movimento lateral |
| C16B_D1 | R1, R2 | `laneChangeState == LaneChangeState.REQUESTED \|\| laneChangeState == LaneChangeState.CHANGING` | C16B_F | C16B_X | — |
| C16B_D2 | R1 | `targetLane == Lane.LEFT \|\| (lane == Lane.RIGHT && targetLane == Lane.CENTER)` | C16B_P1 | C16B_X1 | Manobra para a esquerda |
| C16B_D3 | R2 | `targetLane == Lane.RIGHT \|\| (lane == Lane.LEFT && targetLane == Lane.CENTER)` | C16B_P2 | C16B_X2 | Manobra para a direita |

## Ficha técnica

**Identificação**

| Campo | Valor |
|---|---|
| ID | C16 |
| Nome | Manobra de mudança de faixa em curso |
| Objetivo | Manter a mudança de faixa como decisão enquanto o veículo se desloca lateralmente e impedir uma segunda manobra |
| Condição de ativação | `laneChangeState` em `REQUESTED` ou `CHANGING` |
| Dados de entrada | `VehicleStateFact` (`laneChangeState`, `lane`, `targetLane`), `EgoVehicleState` (progresso, posição lateral) |

**Lógica**

| Elemento | Ficheiro:linha | Salience | Ação | Prioridade |
|---|---|---|---|---|
| R1 `Maintain ongoing lane change left avoidance` | `avoidance.drl:46` | 475 | `CHANGE_LANE_LEFT` | 30 |
| R2 `Maintain ongoing lane change right avoidance` | `avoidance.drl:66` | 475 | `CHANGE_LANE_RIGHT` | 30 |
| Início da manobra | `DecisionActuator.requestLaneChange()` — `DecisionActuator.java:203-217` | — | — | — |
| Avanço lateral | `WorldPhysicsEngine.advanceLaneChange()` — `WorldPhysicsEngine.java:134-174` (posição com suavização *smoothstep*) | — | — | — |
| Conclusão | `SimulationEngine.settleManeuver()` (701), `normalizeCompletedManeuver()` (629) | — | — | — |

No Prolog, R1 e R2 têm os mesmos nomes e precedência 475 (`driving_rules.pl`), sobre `lane_change_state/1`, `target_lane/1` e `ego_lane/1`. A prioridade 30 é intencional: o comentário em `avoidance.drl:43-45` explica que, com prioridade 40, a manobra contaria como retenção e cancelaria o arranque de C04 (que lê a prioridade da decisão anterior).

**Rastreabilidade**

| Elemento | Local |
|---|---|
| Estados | `domain/LaneChangeState.java` (`IDLE`, `REQUESTED`, `CHANGING`, `COMPLETED`) |
| Velocidade lateral | `SimulationConfig.defaultConfig()`: 1,2 m/s (`laneChangeMetersPerSecond`) |
| Eixos das faixas | `domain/Lane.java:9-11` (−3,5 m, 0, +3,5 m) |
| Testes | `PrologRulesTest.ongoingLaneChangeKeepsItsDecision` (827); `LaneChangeManeuverE2ETest` (Drools e Prolog): `potholeAvoidanceMovesVehicleLeft` (95), `doesNotOscillateAfterAvoidance` (214); `RoadLawAndOvertakingE2ETest` (Drools e Prolog): `regressionTestMultiLaneReturnLeftToCenterToRight` (357), `bug3RegressionAfterOvertakingProgressivelyReturnsRight` (852); `SideReadingAfterLaneChangeE2ETest.returnRightUsesASideReadingTakenInTheCurrentLane` (leitura lateral depois de a manobra assentar); `LaneChangeWhyNotE2ETest.laneChangeInProgressBlocksBothSides` |

**Relações**

- Iniciado por C11, C12, C13, C14, C15 e C17.
- Enquanto dura, bloqueia as regras que exigem `laneChangeState == IDLE` (diretamente ou através de `maneuverIdle` em `safeToChangeLane`): início de ultrapassagem e regresso à direita (C11, incluindo as regras de trânsito bloqueado de C11-D), desvios (C12, C13, C14), `Closed barrier requires stop` (C14), `Approaching trend from sliding distance window` e `Hold speed on an approaching trend below its target` (C09), `Start moving off after a standstill` (C04) e `Enforce keep right road law when right lane is safe` (C17).
- Não bloqueia as aproximações a perigos fixos: `Pothole ahead reduce speed` (C12), `Stop before obstacle ahead while still in its lane` (C13) e `Stop before closed barrier while still in its lane` (C14) leem `pathLane`, que durante a manobra é a faixa de destino; deixam de valer para um perigo na faixa que o carro está a deixar.
- Conflitos (M02): R1 e R2 estão no canal lateral (30) e não competem com propostas de velocidade. No canal lateral perdem para os desvios (40, 50); no mesmo nível, como são propostas primeiro (salience 475), ganham às outras mudanças de faixa de 30. Uma decisão de velocidade ≥ 60 veta-as.

**Limitações** (deduzidas do código)

- Quando a decisão de C16 perde ou é vetada, o movimento lateral **não pára**: é a física que o avança enquanto o estado for `REQUESTED` ou `CHANGING`, e o atuador não aceita outra manobra até ao fim. Só a decisão apresentada muda.
- Não existe estado de manobra abortada nem regresso à faixa de origem.
- A guarda de M03 só é avaliada no início da manobra (com a manobra IDLE); durante o movimento lateral não volta a verificar a faixa de destino.
- **Leitura do sensor depois da manobra (correção 1).** `settleManeuver` guarda o instante em que a manobra assentou (`EgoVehicleState.laneSettledAtMillis`, passado às regras em `VehicleStateFact.laneSettledAtMillis` e ao Prolog como `lane_settled_at/1`). As regras de regresso à direita (C11, C17) só aceitam leituras do lado direito feitas depois desse instante, porque o sensor de ângulo morto lê a cada 200 ms e a última leitura ainda pode ser a de antes da manobra.
- A documentação existente (`docs/report-implementation-audit.md`) refere um estado `ABORTED` e uma duração de 2,5 s; nenhum dos dois existe no código.
- R1 e R2 não têm teste unitário de regras no Drools; no Prolog há `ongoingLaneChangeKeepsItsDecision`.

## Exemplos de entradas

| Entrada | Caminho | Resultado |
|---|---|---|
| CENTER, `CHANGING`, `targetLane` LEFT | C16B_D2 Sim | `CHANGE_LANE_LEFT` mantido |
| RIGHT, `REQUESTED`, `targetLane` CENTER | C16B_D2 Sim | `CHANGE_LANE_LEFT` mantido |
| LEFT, `CHANGING`, `targetLane` CENTER | C16B_D3 Sim | `CHANGE_LANE_RIGHT` mantido |
| CENTER, `CHANGING` para LEFT, peão a 10 m | R1 (lateral, 30); C08 propõe `STOP` (60) | `STOP`; a mudança de faixa é vetada na decisão, mas o movimento lateral continua |
| `CHANGING`, nova decisão `CHANGE_LANE_RIGHT` | C16_D2 Sim | Pedido ignorado |
| `IDLE` | C16B_D1 Não | Sem proposta de C16 |
