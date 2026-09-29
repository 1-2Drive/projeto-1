# Drools Realtime Driving Expert System

Academic project implementing a continuous driving decision support loop with Java 17, Drools CEP, and a professional Angular realtime interface.

The expert-system policy remains fully in Java + Drools. Angular is presentation and interaction only.

## Project goal

- Prove a real-time loop: world change -> sensor perception -> Drools inference -> decision -> vehicle reaction -> new world state.
- Keep expert policy as a central rules engine, not hardcoded UI conditions.
- Provide clear explainability (timeline, working memory, source rule, evidence).

## Tech stack

- Java 17
- Maven
- Spring Boot (REST + SSE realtime stream)
- Drools 8.44.0.Final
- JavaFX 21.0.2 (legacy client retained)
- Angular 18 (`frontend/`)
- JUnit 5 + Playwright

## Target architecture

```text
Angular Frontend (presentation only)
        |
        | REST + SSE
        v
Spring Boot API
        |
        v
SimulationEngine (core loop)
        |
        v
WorldState -> Sensors -> Drools Entry Points -> Working Memory -> Inference Engine
        |
        v
DrivingDecision -> DecisionActuator -> Updated WorldState -> next cycle
```

### Separation of concerns (mandatory)

- Angular does not evaluate driving-policy conditions (`ttc`, pedestrian safety thresholds, speed-limit enforcement, lane occupancy conflict, etc.).
- Angular responsibilities: visualize, animate backend coordinates, send user commands, display sensor/WM/rule/decision/explanation data.
- Policy source-of-truth remains in DRL and Java Drools integration.

## Backend modules

- `src/main/java/com/one2drive/projeto1/simulation` - world model, physics, actuator, tick cycle.
- `src/main/java/com/one2drive/projeto1/sensors` - speed/radar/camera/blind-spot/environment perception.
- `src/main/java/com/one2drive/projeto1/drools` - KieSession, router, facts/events, listeners, query support.
- `src/main/java/com/one2drive/projeto1/api` - Spring Boot API, request/response models, realtime SSE publishing.
- `src/main/java/com/one2drive/projeto1/ui` - JavaFX legacy consumer.
- `src/main/resources/rules` - DRL knowledge base.

## Knowledge base and inference

### Knowledge Base

- DRL files under `src/main/resources/rules`:
  - declarations, state updates, emergency, temporal, speed, lights, signs, lane change, hazards, weather, defaults, queries.

### Working Memory

- Facts under `drools/facts`.
- Events under `drools/events` routed via entry points:
  - `VEHICLE_STREAM`
  - `PERCEPTION_STREAM`
  - `ROAD_STREAM`
  - `TRAFFIC_STREAM`
  - `ENVIRONMENT_STREAM`
  - `COMMAND_STREAM`

### Inference engine

- Stateful `KieSession` in stream mode (`kmodule.xml`).
- Forward chaining on real sensor events.
- Conflict handling with `DecisionContext` priority strategy.

### CEP / temporal

- Event metadata includes `@role(event)`, timestamp, and expiry.
- Sliding windows and `accumulate` used for persistent TTC risk and temporal hazard correlation.

## World truth vs sensor perception vs expert knowledge

The implementation enforces this separation.

Example (speed sign):

- World truth: a `SpeedLimitSign` exists in world coordinates.
- Sensor perception: camera emits `SpeedLimitDetectedEvent` only once sign is within camera range.
- Expert knowledge: Drools only knows and reasons from detected events/facts, not from hidden world objects.

## API endpoints

### Simulation commands

- `POST /api/simulation/start`
- `POST /api/simulation/pause`
- `POST /api/simulation/resume`
- `POST /api/simulation/stop`
- `POST /api/simulation/reset`

### Simulation/state

- `GET /api/simulation/state`
- `GET /api/simulation/status`

### Scenarios

- `GET /api/scenarios`
- `POST /api/scenarios/{id}/load`

### World mutation

- `POST /api/world/elements`
- `PUT /api/world/elements/{id}`
- `DELETE /api/world/elements/{id}`
- `POST /api/world/actors`
- `PUT /api/world/actors/{id}`
- `DELETE /api/world/actors/{id}`

### Environment

- `POST /api/environment`

### Drools / explanation

- `GET /api/drools/working-memory`
- `GET /api/drools/decision`
- `GET /api/drools/rules/recent`
- `GET /api/drools/timeline/recent`
- `GET /api/explanations/recent`

### Realtime stream

- `GET /api/stream/snapshots` (Server-Sent Events)

## Angular frontend structure

Located in `frontend/`.

```text
frontend/src/app/
  core/
    api/
    websocket/
    models/
    state/
  simulation/
    simulation-page/
    road-canvas/
    world-state/
  scenario/
    toolbox/
    scenario-library/
    element-properties/
  telemetry/
    speed-advisor/
    sensors-panel/
    vehicle-state/
  expert-system/
    decision-panel/
    working-memory/
    rule-timeline/
    explanation-panel/
  shared/
    status-badge/
    flow-stage/
```

## Running the project

### Backend only (API + Drools + simulation)

macOS/Linux:

```bash
mvn spring-boot:run
```

Windows (PowerShell):

```powershell
mvn spring-boot:run
```

### Frontend only

macOS/Linux:

```bash
cd frontend
npm install
npm start
```

Windows (PowerShell):

```powershell
cd frontend
npm install
npm start
```

Frontend runs on `http://localhost:4200` and proxies `/api` to backend.

### Full stack helper script (macOS/Linux)

```bash
./scripts/dev.sh
```

### Two-terminal alternative

Terminal A:

```bash
mvn spring-boot:run
```

Terminal B:

```bash
cd frontend
npm start
```

## Tests

### Backend tests

```bash
mvn clean test
```

### Frontend unit tests

```bash
cd frontend
npm test
```

### Frontend E2E (real backend required)

1. Start backend (`mvn spring-boot:run`)
2. Start frontend (`cd frontend && npm start`)
3. Run E2E:

```bash
cd frontend
npm run e2e
```

## JavaFX legacy UI

The JavaFX interface remains available as a legacy/demo client:

```bash
mvn javafx:run
```

It is no longer the primary UI, but core simulation and Drools remain shared.

## Scenarios

Defined in `src/main/resources/scenarios/scenarios.json`.

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

## Validation focus

Integration tests validate dynamic behavior end-to-end without injecting final decision facts:

- speed sign detection lifecycle,
- TTC persistence and emergency response,
- pedestrian dynamic insertion,
- dynamic pothole insertion while running,
- priority conflict (pedestrian safety over green-light progression),
- weather mutation impact.

## Known limitations

- SSE is used for realtime state streaming; WebSocket can be added later if bidirectional stream semantics are needed.
- World model remains simplified (1D longitudinal movement + lane abstraction) for academic clarity.
- JavaFX interaction is still manual in GUI environments; automated UI acceptance targets Angular.

## Diagrams

- `diagrams/01-system-architecture.md`
- `diagrams/02-rule-stream-routing.md`
- `diagrams/03-simulation-tick-sequence.md`
- `diagrams/04-stop-sign-state-machine.md`
- `diagrams/05-yield-and-weather-decision-flow.md`
- `diagrams/06-core-class-diagram.md`
- `diagrams/07-scenario-timed-actions.md`
- `diagrams/08-test-coverage-map.md`
