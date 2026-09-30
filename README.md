# Intelligent Driving Decision Support System

Academic Expert System implementing a continuous driving decision support loop with Java 17, Drools CEP, and a professional Angular realtime interface.

The expert-system policy remains fully in Java + Drools. Angular is presentation and interaction only.

## Overview

A continuous perception/decision loop is simulated end to end:

```text
WORLD -> SENSORS -> EVENTS/FACTS -> DROOLS WORKING MEMORY
      -> INFERENCE ENGINE -> DRIVING DECISION -> VEHICLE ACTUATOR
      -> UPDATED WORLD -> SENSORS AGAIN
```

The loop runs continuously while the simulation is running. Changing the world
while the vehicle is moving produces new sensor observations, new inference, and
new vehicle behavior.

## Project Goal

- Prove a realtime loop: world change -> sensor perception -> Drools inference -> decision -> vehicle reaction -> new world state.
- Keep expert policy as a central rules engine, not hardcoded UI conditions.
- Provide clear explainability (timeline, working memory, source rule, evidence).

The project is inspired by the perception/decision loop of ADAS systems. It does not replicate Tesla/FSD or any proprietary autonomous-driving product.

## Expert System Concepts

- **Knowledge Base:** DRL rules under `src/main/resources/rules`.
- **Working Memory:** current state facts and typed sensor events in the KieSession.
- **Inference Engine:** a stateful Drools `KieSession` in stream mode.
- **Forward Chaining:** sensor facts activate rules, rules propose conclusions, and the selected `DrivingDecision` drives the actuator.
- **Explanation Module:** real agenda and working-memory listeners feed the backend timeline, decision evidence, and Working Memory inspector.

## Technology Stack

- Java 17 (compiler release 17)
- Maven 3.9+
- Spring Boot 3.3.4 (REST + SSE realtime stream)
- Drools / KIE 8.44.0.Final (DRL, CEP, entry points, live query)
- JavaFX 21.0.2 (legacy client retained)
- Angular 18 (`frontend/`)
- JUnit 5 (JUnit Jupiter) + Surefire
- Karma + Jasmine (Angular unit tests)
- Playwright (real-stack browser E2E)

## Architecture

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

### CEP / Temporal Reasoning

- Event metadata includes `@role(event)`, `@timestamp`, and `@expires`.
- Sliding windows and `accumulate` are used for persistent TTC risk and temporal hazard correlation.

### Sliding Windows

Two temporal patterns drive the collision-avoidance behaviour:

- **Persistent critical TTC** - at least three `TTCEvent` samples below `2.5 s`
  inside a `window:time(2s)` window triggers `EMERGENCY_BRAKE`. A single noisy
  sample does not satisfy it, so an isolated reading cannot cause a persistent
  collision rule.
- **Approaching distance trend** - a `window:length(5)` sliding window over
  `FrontDistanceEvent` detects a decreasing trend.

Event expiry (`@expires` between 3 s and 30 s) keeps the event history bounded.

### Simulation Engine

`SimulationEngine.tick()` runs at a fixed 100 ms cadence from a single-threaded
scheduler and, in order:

1. Advances physics for the ego vehicle and all active actors.
2. Advances simulation time and the KIE pseudo clock by exactly one tick, so CEP
   windows, stop-hold timers, and event timestamps share one clock domain.
3. Flushes queued world mutations (API writes made while running).
4. Runs scenario timed actions.
5. Updates the `VehicleStateFact`.
6. Observes sensors according to per-sensor range and cadence.
7. Routes sensor events into Drools entry points and fires all rules.
8. Applies the arbitrated `DrivingDecision` to the world via `DecisionActuator`.
9. Publishes the snapshot.

Reset (used by `RESET` and scenario load) clears pending mutations, disposes and
rebuilds the KieSession, resets the clock, clears sensor cadence state and the
explanation trace, so no perception state leaks between runs.

### Sensors

| Sensor | Rate | Range | Produces |
|---|---|---|---|
| Speed | 100 ms | - | `VehicleSpeedEvent` |
| Front radar | 100 ms | 130 m | `FrontDistanceEvent`, `RelativeSpeedEvent`, `TTCEvent` |
| Camera | 200 ms | 120 m | signs, lights, pedestrian, pothole, barrier, roundabout, roadworks, obstacle |
| Blind spot | 200 ms | 30 m | `LaneOccupancyEvent` (left/right) |
| Environment | 400 ms | 200 m | weather, visibility, grip |

Radar distance uses a small deterministic measurement noise (seeded), while
relative speed and TTC are derived from real world state.

### Drools Event Streams

`EventRouter` maps each event type to a named entry point declared in
`kmodule.xml`:

| Entry point | Events |
|---|---|
| `VEHICLE_STREAM` | `VehicleSpeedEvent` |
| `PERCEPTION_STREAM` | `FrontDistanceEvent`, `RelativeSpeedEvent`, `TTCEvent`, `PedestrianDetectedEvent`, `LaneOccupancyEvent` |
| `ROAD_STREAM` | speed/stop/yield sign, obstacle, pothole, barrier, roundabout, roadworks events |
| `TRAFFIC_STREAM` | `TrafficLightEvent` |
| `ENVIRONMENT_STREAM` | weather, visibility, grip, reduced visibility, reduced grip |
| `COMMAND_STREAM` | `AccelerationRequestEvent`, `LaneChangeRequestEvent` |

`WorkingMemoryInspector` renders each fact with its type, value, source, timestamp,
and stream so the inspector reflects real entry-point routing.

### Forward Chaining

Each running tick follows `WORLD -> SENSORS -> EVENTS -> WORKING MEMORY -> RULE ACTIVATION -> DECISION -> ACTUATOR -> WORLD`. Java owns physics, coordinates, timestamps, and sensor observations. Drools is the only driving-policy authority.

### Listeners

The agenda listener records match activation, cancellation, before-fire, and after-fire events. The working-memory listener records insert, update, and delete events. Timeline timestamps use the KIE session clock, so they align with simulation time and CEP windows.

### Queries

`queries.drl` exposes `Current Driving Decision`. The production session opens a live query and closes it with the KieSession; direct API decision reads are mapped from the same stateful session.

### Explanation Module

The Angular timeline, Working Memory, and Explanation tabs render backend listener output. The UI does not infer safety conditions or fabricate rule activations.

## World truth vs sensor perception vs expert knowledge

The implementation enforces this separation.

Example (speed sign):

- World truth: a `SpeedLimitSign` exists in world coordinates.
- Sensor perception: camera emits `SpeedLimitDetectedEvent` only once sign is within camera range.
- Expert knowledge: Drools only knows and reasons from detected events/facts, not from hidden world objects.

The API can add road objects and actors while the simulation is running. Mutations are applied at the next simulation tick, then observed according to the configured sensor range and cadence.

## Backend API

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

### Realtime SSE

- `GET /api/stream/snapshots` (Server-Sent Events)

The publisher pushes a `snapshot` event containing the full mapped snapshot on
every tick and after every command/mutation. Disconnected clients are removed
without interrupting the simulation loop.

### Snapshot payload

`GET /api/simulation/state` (and the SSE payload) returns the ego state,
environment, speed advisor, current decision with evidence, sensor readings, world
objects, Working Memory rows, the bounded recent timeline, recent rules/facts,
the active pipeline stage, tick size, backend-observed detection IDs (all, camera,
radar), and presentation-demo phase statuses.

Verified local URLs:

- Angular UI: `http://localhost:4200`
- Backend: `http://localhost:8080`
- Status: `http://localhost:8080/api/simulation/status`
- SSE: `http://localhost:8080/api/stream/snapshots`

## Angular Frontend

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

The Angular application is presentation-only. It renders backend coordinates, sensor readings, decisions, listener timelines, Working Memory, explanations, and scenario controls. It does not calculate TTC thresholds, speed policy, pedestrian safety, traffic-light policy, or lane-change safety.

## Repository Structure

```text
projeto-1/
  pom.xml
  scripts/
    dev.sh                    one-command full stack helper
  diagrams/                   mermaid architecture and flow diagrams
  src/main/java/com/one2drive/projeto1/
    api/                      Spring Boot controllers, models, mappers, runtime
    decision/                 DrivingDecision, DecisionContext, DecisionPriority
    domain/                   world model types
    drools/                   KnowledgeSession, EventRouter, facts, events, listeners
    explanation/              ExplanationTrace, timeline entries
    scenario/                 scenario library, definitions, factory
    sensors/                  speed, radar, camera, blind spot, environment
    simulation/               SimulationEngine, WorldState, physics, actuator
    ui/                       JavaFX legacy client
  src/main/resources/
    META-INF/kmodule.xml
    rules/                    DRL knowledge base
    scenarios/scenarios.json
  src/test/java/com/one2drive/projeto1/
    drools/                   rule + CEP tests
    simulation/               engine integration tests
  frontend/
    e2e/                      Playwright specs
    src/app/                  Angular application
```

## Prerequisites

- JDK 17 (the Maven compiler release is 17; JDK 17.0.20.1 was used for validation).
- Maven 3.9+ (validated with 3.9.16).
- Node.js 20.x and npm 10.x (validated with Node 20.20.2 and npm 10.8.2).
- Angular 18 and Playwright are installed from `frontend/package-lock.json` via `npm ci`.

If your shell does not already point at a JDK, export it first, for example:

```bash
export JAVA_HOME="/path/to/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Installation

macOS / Linux:

```bash
git clone https://github.com/1-2Drive/projeto-1.git
cd projeto-1
git checkout feat/drools-realtime-driving-expert-system
mvn clean test
cd frontend
npm ci
cd ..
```

Windows (PowerShell):

```powershell
git clone https://github.com/1-2Drive/projeto-1.git
Set-Location projeto-1
git checkout feat/drools-realtime-driving-expert-system
mvn clean test
Set-Location frontend
npm ci
Set-Location ..
```

## Running the project

### Running Backend

macOS/Linux:

```bash
mvn spring-boot:run
```

Windows (PowerShell):

```powershell
mvn spring-boot:run
```

### Running Frontend

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

### Running Full Stack

macOS/Linux (one command):

```bash
./scripts/dev.sh
```

The helper waits for the backend status endpoint and the Angular root page before
reporting readiness. Press `Ctrl-C` to stop both child processes.

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

## Testing

### Backend tests

```bash
mvn clean test
```

### Angular unit tests

```bash
cd frontend
npm test
```

### Production build

```bash
npm run build
```

### Full validation command sequence

```bash
export JAVA_HOME="/path/to/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean test
cd frontend
npm ci
npm test
npm run build
```

Start the backend and frontend in two terminals, then run `npm run e2e` in the
frontend terminal to complete the browser E2E step.

## Browser E2E

The Playwright suite runs against the **real** stack: real Angular app, real
Spring Boot API, real `SimulationEngine`, real sensors, real SSE stream, and real
Drools session. It does not mock `DrivingDecision`.

1. Start the backend (`mvn spring-boot:run`)
2. Start the frontend (`cd frontend && npm start`)
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

## Scenario Editor

The left Toolbox supports speed limits, stop/yield signs, traffic lights, roundabouts, potholes, roadworks, obstacles, barriers, front vehicles, pedestrians, rain, low visibility, and low grip. Configurable actions collect the supported lane, distance, state, severity, speed, and approach-speed fields before calling the REST API.

## Presentation Demo

`S14_PRESENTATION_DEMO` is a backend timed scenario for a 90-second classroom walkthrough. It schedules world mutations only: a speed sign, rain, a slower front vehicle, yield/roundabout elements, a pedestrian, and weather recovery. The backend marks phases from observed sensor/rule/decision evidence; Angular only displays those statuses.

## Predefined Scenarios

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
## Validation focus

Integration tests validate dynamic behavior end-to-end without injecting final decision facts:

- speed sign detection lifecycle,
- TTC persistence, emergency actuation, and recovery,
- pedestrian dynamic insertion,
- dynamic pothole insertion while running,
- priority conflict (pedestrian safety over green-light progression),
- weather mutation impact,
- reset isolation and KieSession rebuild.

The direct integration suite covers 30 backend tests: 22 rule/CEP tests and 8 SimulationEngine flows. Angular has 11 unit tests. Browser E2E has 3 tests covering runtime speed-limit, pedestrian, and presentation-demo paths with the real stack.

## Manual Test

1. Open `http://localhost:4200` and verify Backend `CONNECTED`, Drools `ACTIVE`, and Stream `LIVE`.
2. Click `RESET`, `START`, set distance to `150`, and add speed limit `50`. The sign appears in World State immediately, but the camera evidence appears only as the ego vehicle approaches. Observe the speed advisor change to `REDUCE_SPEED` and recommendation `50 km/h`.
3. Add a front vehicle ahead with a lower speed. Observe physical distance, relative speed, TTC samples, a persistent TTC rule activation, `EMERGENCY_BRAKE`, and subsequent speed reduction.
4. Add a pedestrian while running. Observe the world object before perception, then the pedestrian event and `BRAKE`/`STOP` decision when it enters camera range.
5. Add a pothole and observe world mutation, later camera event, hazard rule, and vehicle adaptation.
6. Use Rain, Low visibility, or Low grip and observe environment events, Working Memory updates, conservative rules, and speed adaptation.
7. Run a green light with a pedestrian in the path and confirm pedestrian safety wins the lower-priority progress decision.

## Presentation Script

1. Open the Angular UI and explain the Backend, Drools, Stream, Simulation, and simulation-time indicators.
2. Click `RUN PRESENTATION DEMO` and point to the moving ego vehicle, road coordinates, and sensor zones.
3. Show the speed-limit sign in World State before it is detected by the camera.
4. Open `Inference Timeline` and point out `SENSOR`, `FACT`, `RULE`, `DECISION`, and `ACTUATOR` entries from the backend listeners.
5. Use `Working Memory` to show current state facts and entry-point/source columns.
6. Use `Explanation` to show the winning rule, reason, result, and evidence lines, including temporal TTC samples.
7. Explain that the world changes again after actuation and the loop repeats continuously.

## Known limitations

- SSE is used for realtime state streaming; WebSocket can be added later if bidirectional stream semantics are needed.
- World model remains simplified (1D longitudinal movement + lane abstraction) for academic clarity.
- JavaFX interaction is still manual in GUI environments; automated UI acceptance targets Angular.
- The world is an in-memory single-simulation academic model with simplified one-dimensional physics and lane abstraction; it is not a production ADAS controller.
- Angular production assets use Google Fonts when network access is available and fall back to system fonts otherwise.

- `S11B` Yield sign approach
- `S12` Complex sequential scenario
- `S14_PRESENTATION_DEMO` Presentation demo end-to-end

## Adding Rules

1. Add or edit a DRL file under `src/main/resources/rules` (or create a new one;
   all `*.drl` files on the classpath are compiled into the KieBase).
2. Declare any new event type in `00-declarations.drl` with `@role(event)`,
   `@timestamp(timestamp)`, and an appropriate `@expires`.
3. Route the event to an entry point in `EventRouter` and declare the entry point
   in `src/main/resources/META-INF/kmodule.xml`.
4. Write the rule and call `droolsSupport.proposeDecision($ctx, action,
   recommendedSpeed, riskLevel, DecisionPriority.X, reason, ruleName, evidence)`.
   Pick the priority so conflict resolution matches the intended safety order.
5. Add a rule-level test in `DrivingExpertSystemRulesTest`.

Do not decide vehicle behaviour outside Drools: the actuator only reacts to the
`DrivingDecision` produced by rules.

## Adding Sensors

1. Add a new event class under `drools/events` extending `BaseEvent`.
2. Implement the sensor under `sensors` (extend `AbstractSensor`), giving it an id,
   update rate, and detection range from `SimulationConfig`.
3. Register it in the `SensorManager` list inside `SimulationEngine`.
4. Route its events in `EventRouter`, declare them in `00-declarations.drl`, and
   consume them from a stream rule.
5. Expose the range in `SimulationConfig` so the road visualization and the sensor
   stay consistent.

## Adding Scenarios

1. Add an entry to `src/main/resources/scenarios/scenarios.json` with `id`, `name`,
   `initialSpeedKmh`, `initialWeather`, `roadElements`, `actors`, and `timedActions`.
2. Use existing enums for element/actor types (`ScenarioRoadElementType`,
   `ScenarioActorType`, `ScenarioTimedActionType`) so invalid values fail fast.
3. Reload the scenario from the Scenario Library panel or `POST
   /api/scenarios/{id}/load`.

## Architecture Diagrams

- `diagrams/01-system-architecture.md`
- `diagrams/02-rule-stream-routing.md`
- `diagrams/03-simulation-tick-sequence.md`
- `diagrams/04-stop-sign-state-machine.md`
- `diagrams/05-yield-and-weather-decision-flow.md`
- `diagrams/06-core-class-diagram.md`
- `diagrams/07-scenario-timed-actions.md`
- `diagrams/08-test-coverage-map.md`
