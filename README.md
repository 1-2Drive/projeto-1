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

## Rule engines: Drools and SWI-Prolog

Decisions come from one of two interchangeable rule engines behind
`inference.ExpertSystemBackend`:

- **Drools** (default): the full DRL knowledge base, CEP, live query.
- **SWI-Prolog**: our own forward-chaining production-rule engine (`src/main/prolog/`):
  each rule with a precedence equal to its Drools salience, an agenda that fires the
  highest precedence first, and re-matching driven by a dependency
  index built from the rules. It replicates every Drools decision rule, under the same
  rule names; `PrologDroolsEquivalenceTest` checks both engines decide alike, and most
  simulation tests run on both. It runs as one persistent `swipl` process, with JSON over
  stdin/stdout, and needs `swipl` on `PATH`.

Both feed the same `DecisionContext` arbitration, actuator, timeline and working-memory
views. Choose the engine at startup with `expert.backend=drools|prolog`, or switch at
runtime with the **Rule engine** toggle in the control bar or
`POST /api/simulation/backend/{drools|prolog}`. A switch reloads the current scenario. If
the active engine fails, the simulation pauses and shows the error; it never drives on a
decision nobody inferred.

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
explanation trace, so no perception state leaks between runs. The ego vehicle is
placed at rest (0 km/h, `STOPPED`) and the scenario's entry speed is applied by
`START`, so a stopped simulation never reports a cruising speed.

Every tick starts a fresh decision cycle: the `DecisionContext` is reset and updated in
the KieSession, so all rules re-evaluate against the current state facts on every tick.
Rules that read raw stream events use sliding windows, so only recent samples count.

### Road map

The road is a matrix, defined per scenario in `scenarios.json` (`map`): one row per
5 m block along the road and one column per lane (`LEFT`, `CENTER`, `RIGHT`). A 100x3
map is a 500 m road. After the last row the car continues at row 0, so the road loops
and every distance (sensors, road view) is measured around the loop. A scenario
without `map` gets an empty 100x3 road (`SimulationConfig`: block length and rows).

`0` is a free cell; any other number is a legend code:

| Code | Element |
|---|---|
| 1 | Obstacle (high severity) |
| 2 | Pothole (high severity) |
| 3 | Closed barrier |
| 4 | Stop sign |
| 6 / 7 | Red / green traffic light |
| 8 - 13 | Speed limit 30 / 50 / 70 / 90 / 100 / 120 km/h |

A scenario can add or replace codes with `mapLegend`, for example
`"mapLegend": {"14": {"type": "SPEED_LIMIT", "speedLimitKmh": 40}}`. Elements added
from the toolbox with other parameters get a new code automatically. Each occupied
cell becomes an ordinary road element at the start of its block, named after the cell
(`r24-CENTER`), so sensors and rules work on it like on any other element. Vehicles
and pedestrians are not part of the matrix: they move freely on the same loop.

The **Road map** panel under the road view shows the whole matrix and the car's block.
Pick a value in the palette and click or drag over cells to paint them, also while the
simulation runs (`PUT /api/world/map/cells`).

When an obstacle blocks the car's lane and neither side lane is free,
`Stop before obstacle when no lane is free` stops it 10 m short. Once a lane opens the
car steers through it and `Move off when stopped and nothing holds the vehicle` takes it
back up to 30 km/h (or the speed limit, if lower). From there
`Build back up to the scenario cruise speed when no sign governs the road` keeps
accelerating until the vehicle reaches the scenario's entry speed, so a brake, an
overtake or a stop never leaves the car stranded below the speed the road asks for.

### Sensors

| Sensor | Rate | Range | Produces |
|---|---|---|---|
| Speed | 100 ms | - | `VehicleSpeedEvent` |
| Front radar | 100 ms | 130 m | `FrontDistanceEvent`, `RelativeSpeedEvent`, `TTCEvent` |
| Camera | 200 ms | 120 m × visibility factor (84 m in rain) | signs, lights, pedestrian, pothole, barrier, obstacle |
| Blind spot | 200 ms | 30 m | `LaneOccupancyEvent` per side of the car: vehicles alongside, fixed hazards up to 60 m ahead in that lane; past the road edge the side is occupied |
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
| `ROAD_STREAM` | speed/stop sign, obstacle, pothole, barrier events |
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
- `POST /api/simulation/lane-change/{LEFT|RIGHT}` (driver request; Drools decides whether it is safe)

### Simulation/state

- `GET /api/simulation/state`
- `GET /api/simulation/status`

### Scenarios

- `GET /api/scenarios`
- `POST /api/scenarios/{id}/load`

### World mutation

- `PUT /api/world/map/cells` with `{"row": 24, "lane": "CENTER", "code": 1}` (`0` frees the cell)
- `POST /api/world/elements` (written into the map cell `distanceAheadMeters` ahead)
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

## Quick Start

### First time

```bash
cd ~/Documents/learn/projeto-1
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean test
cd frontend
npm ci
cd ..
```

### Every time

```bash
cd ~/Documents/learn/projeto-1
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./scripts/dev.sh
```

That single command starts the Drools backend on `http://localhost:8080` and the
Angular UI on `http://localhost:4200`, and waits until both answer before printing
`ready`. Press `Ctrl-C` once to stop both services.

Then open **<http://localhost:4200>** in a browser.

### The manual workflow

The simulation starts **stopped on an empty road** and waits for you. The road canvas
shows `Press START to begin` until the first tick, so nothing is simulated before you ask.

1. Press **START**. The vehicle moves and the expert system starts perceiving.
2. Add a road object from the **Scenario Toolbox** (left). Clicking a control opens the
   configuration drawer; set the values and press **Add to road**.
3. Watch the object travel toward the ego vehicle on the road canvas. It is only shown
   with a `CAMERA` or `RADAR` badge once a sensor has actually detected it inside its
   configured range — the ranges are listed in the Sensors panel.
4. Watch Drools react: the default `IMPORTANT` timeline shows meaningful perception,
   rule, decision, actuation and maneuver transitions; raw numerical samples remain in
   Sensors and Working Memory. The Decision panel shows the action, risk, recommended
   speed, decision age and fired rule.
5. Use **PAUSE** / **RESUME** to step through, and **RESET** to return to the empty road.

### A two-minute demonstration

1. **START** the simulation.
2. Click the **50** speed-limit sign, set distance to `150 m`, **Add to road**.
3. When the camera reaches the sign (around 120 m of travel), the legal limit drops to
   `50`, the gauge turns red, and Drools issues `REDUCE_SPEED`; the vehicle decelerates.
4. Click **Vehicle**, set distance `45 m` and speed `15 km/h`, **Add to road**.
   The radar detects it, computes TTC, and the timeline records the safety decision.

### Optional: scripted demo

The **RUN DEMO** button in the top-right corner replays the `S14_PRESENTATION_DEMO`
scenario end to end with a phase strip. It is optional — manual mode is the default
path and demo chrome stays hidden unless the demo is running.

## Manual Avoidance Demo

This is the shortest path to seeing the complete perception → reasoning → decision →
physical-action chain:

1. Start the project and open the Angular UI.
2. Confirm the header says `STOPPED` and the road shows `Press START to begin`.
3. Press **START**.
4. In **Road signs / Hazards**, choose **Pothole**.
5. Set distance to `100 m`, lane to `Center`, severity to `High`, then press **Add to road**.
6. Keep the left lane clear. The blind-spot panel must report `CLEAR`.
7. Watch the camera detect the pothole only when it enters camera range.
8. Watch **Current situation** list the perception and lane-safety evidence.
9. Watch Drools select `CHANGE_LANE_LEFT` with the avoidance rule.
10. Watch the ego vehicle physically move from the center lane to the left lane over
    approximately three seconds. The road view shows the lateral coordinate, turn
    indicator and maneuver progress from the backend.
11. Watch `MANEUVER STARTED` and `MANEUVER COMPLETED` in the important timeline.
12. Open **Timeline** and **Explanation** to inspect the rule, decision and evidence.

### Unsafe avoidance variant

Add a front vehicle at approximately `20 m` in both the left and right lanes, using the
same speed as the ego vehicle so both blind spots remain occupied. Add the center-lane
pothole again. Drools must not select a lane change; the vehicle remains centered and
uses the existing conservative `REDUCE_SPEED`, `BRAKE` or `STOP` policy as the hazard
approaches.

### Evidence controls

The default Timeline filter is `IMPORTANT`, which shows perception transitions, fired
rules, decisions, actuation and maneuver lifecycle events. Continuous radar/speed/TTC
samples remain available in the Sensors and Working Memory views. Use `FREEZE VIEW` to
inspect the technical timeline without stopping the simulation; `PAUSE` remains the
separate control that freezes the simulation itself.

### Useful URLs

| What | URL |
| --- | --- |
| Angular UI | <http://localhost:4200> |
| Backend status | <http://localhost:8080/api/simulation/status> |
| Realtime stream (SSE) | <http://localhost:8080/api/stream/snapshots> |
| Working memory | <http://localhost:8080/api/drools/working-memory> |
| Recent decisions | <http://localhost:8080/api/explanations/recent> |

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
reporting readiness. Press `Ctrl-C` to stop both services; it terminates each service
process group, so the forked JVM and the Angular dev server are both stopped.

Windows (one command, from the project root in PowerShell or cmd):

```powershell
scripts\dev.cmd
```

`dev.cmd` launches `scripts/dev.ps1` with `-ExecutionPolicy Bypass`, so it works even
when PowerShell scripts are disabled. It runs `npm ci` if `frontend/node_modules` is
missing, starts both services in hidden consoles, streams their output prefixed with
`[backend]` / `[frontend]` (full logs in `.dev-logs/`), and kills both process trees
on `Ctrl+C`.

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

The left Toolbox supports speed limits, stop signs, traffic lights, potholes, obstacles, barriers, front vehicles, pedestrians, rain, low visibility, and low grip. Configurable actions collect the supported lane, distance, state, severity, and speed fields before calling the REST API.

## Presentation Demo

`S14_PRESENTATION_DEMO` is a backend timed scenario for a 90-second classroom walkthrough. It schedules world mutations only: a speed sign, rain, a slower front vehicle, a pedestrian, and weather recovery. The backend marks phases from observed sensor/rule/decision evidence; Angular only displays those statuses.

## Predefined Scenarios

Defined in `src/main/resources/scenarios/scenarios.json`.

- `S1` Empty safe road
- `S2` 90 to 50 speed limit
- `S3` Red traffic light
- `S4` Stop sign
- `S6` Pothole
- `S7` Closed barrier
- `S8` Pedestrian crossing
- `S9` Critical TTC
- `S10` Lane change blocked
- `S11` Rain low grip
- `S12` Complex sequential scenario
- `S14_PRESENTATION_DEMO` Presentation demo end-to-end
- `S15_MATRIX_COURSE` Matrix obstacle course: a slalom through obstacles and a pothole that repeats every lap
- `S16_SLOW_VEHICLE_OVERTAKE` Slow vehicle overtake and return to the right lane
- `S17_CONTINUOUS_LINE_BLOCKED` Continuous line blocks avoidance to the right
- `S18_SPEED_RADAR_APPROACH` Speed radar approach and compliant pass

## Validation focus

Integration tests validate dynamic behavior end-to-end without injecting final decision facts:

- speed sign detection lifecycle,
- TTC persistence, emergency actuation, and recovery,
- pedestrian dynamic insertion,
- dynamic pothole insertion while running,
- priority conflict (pedestrian safety over green-light progression),
- stop sign full stop and release,
- weather mutation impact,
- reset isolation and KieSession rebuild.

The direct integration suite covers 81 backend tests: 23 rule/CEP tests, 7 fact description tests, 13 SimulationEngine flows, 7 lane change flows, 21 road-law and overtaking flows, 5 road map integration tests, and 5 road map unit tests. In total `mvn test` runs 389 backend tests (JUnit 5), and Angular has 38 Karma unit tests (`npm test`). Browser E2E has 16 Playwright tests covering startup, runtime speed-limit, pedestrian, pothole progressive lane change, blocked lane change fallback, sensor groups, and presentation-demo paths with the real stack.

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
- World model remains simplified for academic clarity: a looping road map of 5 m blocks and three lanes, with continuous longitudinal movement.
- JavaFX interaction is still manual in GUI environments; automated UI acceptance targets Angular.
- The world is an in-memory single-simulation academic model with simplified one-dimensional physics and lane abstraction; it is not a production ADAS controller.
- Angular production assets use Google Fonts when network access is available and fall back to system fonts otherwise.

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
   `initialSpeedKmh`, `initialWeather`, `map` (rows of `[LEFT, CENTER, RIGHT]` codes, see
   [Road map](#road-map)), optional `mapLegend`, `actors`, and `timedActions`.
2. Use existing enums for element/actor types (`ScenarioRoadElementType`,
   `ScenarioActorType`, `ScenarioTimedActionType`) so invalid values fail fast.
3. Reload the scenario from the Scenario Library panel or `POST
   /api/scenarios/{id}/load`.

## Architecture Diagrams

- `diagrams/01-system-architecture.md`
- `diagrams/02-rule-stream-routing.md`
- `diagrams/03-simulation-tick-sequence.md`
- `diagrams/04-stop-sign-state-machine.md`
- `diagrams/05-stop-sign-and-weather-decision-flow.md`
- `diagrams/06-core-class-diagram.md`
- `diagrams/07-scenario-timed-actions.md`
- `diagrams/08-test-coverage-map.md`
