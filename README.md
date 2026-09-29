# Drools Realtime Driving Expert System

Academic project implementing a realtime driving decision support system with Java 17, Drools CEP, and a JavaFX simulator.

The project keeps decision policy in Drools (rules) while simulation, sensors, and UI remain separate concerns.

## Project goal

- Simulate driving scenarios with dynamic road events and actors.
- Produce one driving decision per cycle using a rule-based expert system.
- Explain decisions with fired-rule and working-memory evidence.

## Tech stack

- Java 17
- Maven
- Drools 8.44.0.Final
- JavaFX 21.0.2
- JUnit 5

## Architecture (Java + Drools)

### Knowledge Base

- DRL files under `src/main/resources/rules`.
- Rule sets for speed, signs, lights, hazards, weather, lane change, temporal logic, emergency logic, and defaults.

### Working Memory

- Facts in `src/main/java/com/one2drive/projeto1/drools/facts`.
- Events in `src/main/java/com/one2drive/projeto1/drools/events`.
- `DecisionContext` and `DrivingDecision` are inserted and updated each cycle.

### Inference Engine

- Stateful `KieSession` created in `KnowledgeSession`.
- `kmodule.xml` configures stream event processing (`eventProcessingMode="stream"`).
- Entry points are used by event type (`VEHICLE_STREAM`, `PERCEPTION_STREAM`, `ROAD_STREAM`, `TRAFFIC_STREAM`, `ENVIRONMENT_STREAM`, `COMMAND_STREAM`).

### Forward chaining strategy

- Sensor and command events are inserted.
- State-update rules derive latest facts.
- Domain rules propose candidate decisions into `DecisionContext`.
- Priority/conflict strategy picks final decision (`DecisionContext.propose`).
- Final `DrivingDecision` is applied by simulation actuator.

### CEP and temporal reasoning

- Event declarations include `@role(event)`, `@timestamp(timestamp)`, `@expires(...)`.
- Temporal rules use sliding windows (`window:time`, `window:length`) and `accumulate`.
- Critical TTC persistence and rain+TTC conservatism are handled in temporal/emergency rules.

## Module responsibilities

- `simulation` - world physics, tick loop, timed actions, actuator.
- `sensors` - speed/radar/camera/blind-spot/environment event generation.
- `drools` - session bootstrap, event routing, facts/events/listeners/queries.
- `ui` - JavaFX rendering and controls, no policy decisions.
- `explanation` - timeline/rule/fact trace model.

## Sensors

- `SpeedSensor`
- `FrontRadarSensor` (distance, relative speed, TTC)
- `CameraSensor` (signs, lights, obstacles, road structures, pedestrian)
- `BlindSpotSensor`
- `EnvironmentSensor` (weather, visibility, grip, reduced visibility/grip events)

## Simulation loop

Per tick (`SimulationEngine.tick`):

1. Advance physics and clocks.
2. Apply pending world mutations and timed actions.
3. Update vehicle state fact.
4. Collect and route sensor events.
5. Fire rules.
6. Apply `DecisionContext` -> `DrivingDecision`.
7. Actuate ego vehicle and produce snapshot.

## Explanation module

- `RuleRuntimeTraceListener` captures fired rules.
- `WorkingMemoryTraceListener` captures insert/update/delete fact events.
- `ExplanationTrace` stores timeline entries.
- UI surfaces current decision reason, source rule, evidence, and working-memory snapshot.

## Queries and live updates

- Query: `Current Driving Decision` in `src/main/resources/rules/queries.drl`.
- LiveQuery integration in `KnowledgeSession` (`DecisionLiveQueryListener`) for UI update callbacks.
- Tests use pseudo clock session without live query to keep deterministic lifecycle.

## Scenarios

Defined in `src/main/resources/scenarios/scenarios.json`:

- `S1` Empty safe road
- `S2` 90 to 50 speed limit
- `S3` Red traffic light
- `S4` Stop sign
- `S5` Roundabout
- `S6` Pothole
- `S7` Closed barrier
- `S8` Pedestrian crossing
- `S9` Critical TTC
- `S10` Lane change blocked
- `S11` Rain low grip
- `S11B` Yield sign approach
- `S12` Complex sequential scenario

## How to run

### Build

```bash
mvn clean compile
```

### Test

```bash
mvn clean test
```

### Launch UI

```bash
mvn javafx:run
```

## Platform notes

### macOS

- Ensure JDK 17+ and Maven are installed.
- JavaFX native modules are resolved by Maven (`javafx-maven-plugin`).
- If macOS warns about native access in newer JDKs, the app can still start; warning is non-fatal in current setup.

### Windows

- Install JDK 17+ and Maven.
- Run commands from PowerShell or Command Prompt in repository root.
- If JavaFX runtime issues appear, ensure architecture-compatible JDK/JavaFX dependencies are used (Maven handles classifier resolution automatically in this project).

## How to extend

1. Add/adjust domain events or facts in `drools/events` and `drools/facts`.
2. Update event declarations in `rules/00-declarations.drl`.
3. Route new events via `EventRouter` entry points.
4. Add state update logic in `rules/01-state-updates.drl` if needed.
5. Add behavior rules in dedicated DRL files.
6. Add unit and E2E tests.
7. Optionally add scenario examples in `scenarios.json`.

## Validation summary

- Rule-level tests: `DrivingExpertSystemRulesTest`.
- E2E simulation tests: `SimulationEngineE2ETest`.
- Current suite size: 25 tests.

Latest branch validation:

- `mvn clean test` passed (`Tests run: 25, Failures: 0, Errors: 0`).

## Limitations

- JavaFX UI smoke validation is startup-only in non-interactive/headless CLI environments.
- This project uses a simplified 1D/3-lane road model and does not model full vehicle dynamics.
- Sensor models are deterministic/simplified and not calibrated against real-world hardware.
- Rule conflict resolution is priority-based and does not include probabilistic uncertainty modeling.

## Diagrams

- `diagrams/01-system-architecture.md`
- `diagrams/02-rule-stream-routing.md`
- `diagrams/03-simulation-tick-sequence.md`
- `diagrams/04-stop-sign-state-machine.md`
- `diagrams/05-yield-and-weather-decision-flow.md`
- `diagrams/06-core-class-diagram.md`
- `diagrams/07-scenario-timed-actions.md`
- `diagrams/08-test-coverage-map.md`
