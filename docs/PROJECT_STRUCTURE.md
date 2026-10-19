# Project Structure

**Audience:** developers and maintainers who need to find their way around the code base, build it, run it, and change it.

**Related documents:** [ARCHITECTURE.md](ARCHITECTURE.md) explains how the components interact, [EXECUTION_FLOW.md](EXECUTION_FLOW.md) follows one simulation tick step by step, and [BUSINESS_RULES.md](BUSINESS_RULES.md) catalogues the rules.

> **Technology note.** This documentation was requested on the assumption that the project was a *Prolog-based* expert system. At commit `70082b4` the project has **two** interchangeable inference backends:
> - **Drools**: a Java rule engine, the default;
> - **SWI-Prolog**: the project's own production-rule interpreter written in Prolog, run as an external `swipl` process.
>
> Both are hosted by a Java 17 / Spring Boot application with an Angular user interface. Everything below describes commit `70082b4` on branch `feat/drools-realtime-driving-expert-system`.

---

## 1. Technologies and Tools

| Area | Technology (version) | Where it is declared |
|---|---|---|
| Language and runtime | Java 17 (`<release>17</release>`) | [`pom.xml`](../pom.xml) |
| Build tool | Apache Maven (3.9.x; 3.9.11 used for the verification runs) | [`pom.xml`](../pom.xml) |
| Rule engine 1 (default) | Drools / KIE **8.44.0.Final**: DRL rules, stream (CEP) mode, entry points, agenda groups, queries, live query, truth maintenance | [`pom.xml`](../pom.xml), [`kmodule.xml`](../src/main/resources/META-INF/kmodule.xml), [`rules/`](../src/main/resources/rules) |
| Rule engine 2 | **SWI-Prolog 9.x** (external program, not a Maven dependency) running the project's [`engine.pl`](../src/main/prolog/engine.pl) | [`src/main/prolog/`](../src/main/prolog), `pom.xml` resource entry, README |
| Backend web layer | Spring Boot **3.3.4** (`spring-boot-starter-web`, `spring-boot-starter-websocket`, `spring-boot-starter-validation`): REST and a plain WebSocket | [`pom.xml`](../pom.xml), `src/main/java/com/one2drive/projeto1/api/` |
| JSON | Jackson 2.17.2 (scenario file; Prolog protocol) | [`pom.xml`](../pom.xml) |
| Legacy desktop client | JavaFX **21.0.2** | [`DrivingExpertSystemApp.java`](../src/main/java/com/one2drive/projeto1/ui/DrivingExpertSystemApp.java) |
| Web frontend | Angular **18.2** (18.2.14 installed), RxJS 7.8, TypeScript 5.5 | [`frontend/package.json`](../frontend/package.json) |
| Backend tests | JUnit Jupiter 5.10.2 (incl. parameterised tests over both backends), Maven Surefire 3.2.5 | [`pom.xml`](../pom.xml) |
| Frontend unit tests | Karma 6.4 + Jasmine 5 (ChromeHeadless) | [`frontend/package.json`](../frontend/package.json), [`frontend/angular.json`](../frontend/angular.json) |
| Browser end-to-end tests | Playwright 1.48 (real backend and frontend) | [`frontend/playwright.config.ts`](../frontend/playwright.config.ts) |
| Developer launch scripts | Bash, PowerShell, cmd | [`scripts/`](../scripts) |

There is no database, no message broker and no external service. All state lives in memory, in one Java process plus, when selected, one `swipl` child process.

---

## 2. Repository Layout

```text
projeto-1/
├── pom.xml                         Maven build: Spring Boot + Drools + JavaFX + JUnit; copies src/main/prolog to prolog/
├── README.md                       Project README (user-facing guide; some statements are outdated, see §12)
├── docs/                           This documentation set (+ an earlier audit report, kept unchanged)
├── diagrams/                       Earlier Mermaid diagrams (01–08) and a Portuguese conceptual model
├── Relatorio_Previo_ENGCIA/        Portuguese LaTeX preliminary report (mostly section skeletons)
├── scripts/                        dev.sh, dev.ps1, dev.cmd: start backend + frontend together
├── screenshot_*.png                13 evidence screenshots; one Playwright test rewrites 4 of them (see §12)
├── src/
│   ├── main/
│   │   ├── java/com/one2drive/projeto1/        178 files
│   │   │   ├── api/                Spring Boot application, REST controllers, DTOs, mappers, runtime loop, WebSocket
│   │   │   ├── config/             SimulationConfig: numeric simulation parameters
│   │   │   ├── decision/           DecisionPriority, DecisionContext, DecisionProposal, DrivingDecision,
│   │   │   │                       LaneChangeCheck, StoppingPolicy
│   │   │   ├── domain/             World model: ego vehicle, actors, road elements, enums (incl. DecisionChannel)
│   │   │   ├── inference/          ExpertSystemBackend interface, backend type, input, exception
│   │   │   ├── drools/             DroolsBackend, KnowledgeSession, EventRouter, FuzzySupport, inspection
│   │   │   │   ├── events/         24 sensor/command/internal event classes + abstract BaseEvent
│   │   │   │   ├── facts/          26 belief and state classes kept in working memory
│   │   │   │   └── listeners/      Agenda and working-memory listeners feeding the timeline
│   │   │   ├── prolog/             PrologBackend, PrologKnowledgeSession, PrologProcess (swipl), results
│   │   │   ├── explanation/        ExplanationTrace (bounded timeline) and entry types
│   │   │   ├── scenario/           Scenario JSON model, loader, world factory
│   │   │   ├── sensors/            Simulated sensors and their scheduler
│   │   │   ├── simulation/         SimulationEngine, world state, physics, actuator, road map, telemetry
│   │   │   └── ui/                 JavaFX legacy client
│   │   ├── prolog/                 engine.pl, driving_state.pl, driving_rules.pl, server.pl
│   │   └── resources/
│   │       ├── META-INF/kmodule.xml   Knowledge base and session declaration
│   │       ├── application.properties  server.port, expert.backend
│   │       ├── rules/              19 DRL files: 119 rules and 6 queries
│   │       └── scenarios/scenarios.json  15 predefined scenarios
│   └── test/java/com/one2drive/projeto1/   37 files: 32 test classes + harnesses
│       ├── api/service/, decision/, domain/, sensors/   Unit tests
│       ├── drools/                 Rule-level tests, agenda phases, fuzzy arithmetic, harness
│       ├── prolog/                 Prolog rule tests, Drools/Prolog equivalence, Prolog E2E, harness
│       └── simulation/             Engine-level end-to-end tests (most run on both backends)
└── frontend/                       Angular application, Karma unit tests, Playwright end-to-end tests
    ├── src/app/                    core/ (API, WebSocket, state, models), simulation/, scenario/, telemetry/,
    │                               expert-system/, shared/
    ├── e2e/                        20 Playwright tests (simulation.spec.ts, realtime.spec.ts)
    ├── proxy.conf.json             Dev-server proxy: /api → http://localhost:8080 (REST and WebSocket)
    └── playwright.config.ts
```

Directories that exist locally but are not version-controlled (see [`.gitignore`](../.gitignore)): `target/`, `.dev-logs/`, `frontend/node_modules/`, `frontend/.angular/`, `frontend/dist/`.

---

## 3. Backend Packages and Responsibilities

All Java sources are under `src/main/java/com/one2drive/projeto1/`.

| Package | Key classes | Responsibility |
|---|---|---|
| `api` | `DrivingExpertSystemServerApplication` | Spring Boot entry point (`main`) |
| `api.config` | `SimulationCoreConfig`, `CorsConfig` | Creates the singleton `SimulationEngine` (pseudo clock, scenario S1, backend from `expert.backend`), loads the scenario library, allows CORS from `http://localhost:4200` |
| `api.controller` | `SimulationController`, `ScenarioController`, `WorldController`, `EnvironmentController`, `DroolsController`, `ExplanationController`, `RealtimeWebSocketHandler`, `ApiExceptionHandler` | REST and WebSocket endpoints (§9.4); `ExpertSystemException` → HTTP 503 |
| `api.service` | `SimulationRuntimeService`, `RealtimeSnapshotPublisher`, `ApiModelMapper`, `ApiRequestMapper`, `InferenceNarrator` | 100 ms scheduler, backend switch, DTO mapping, non-blocking WebSocket publishing, narrative |
| `api.model` | `ApiSimulationSnapshot`, `ApiDecisionView` (speed and lane channels, proposals, lane checks), `ApiSystemStatus`, request DTOs | Data transfer objects with Bean Validation constraints |
| `config` | `SimulationConfig` | Tick, map, accelerations, sensor ranges and rates, light timings (§5.2) |
| `decision` | `DecisionPriority`, `DecisionContext`, `DecisionProposal`, `DrivingDecision`, `LaneChangeCheck`, `StoppingPolicy` | Priority levels; per-channel arbitration of all proposals; the decision; why-not; stopping figures and formulas |
| `domain` | `EgoVehicleState`, `DynamicActor`, `OtherVehicle`, `Pedestrian`, `RoadElement` subclasses (`SpeedLimitSign`, `StopSign`, `TrafficLight`, `Crosswalk`, `Pothole`, `StaticObstacle`, `Barrier`), enums (`Lane`, `DrivingAction`, `DecisionChannel`, `RiskLevel`, `LaneChangeState`, `PedestrianCrossingState`, …) | Ground-truth world model and shared vocabulary |
| `inference` | `ExpertSystemBackend`, `ExpertBackendType`, `DecisionInput`, `ExpertSystemException` | Backend-neutral interface used by the engine |
| `drools` | `DroolsBackend`, `KnowledgeSession`, `EventRouter`, `DroolsEntryPoints`, `DroolsSupport`, `FuzzySupport`, `WorkingMemoryInspector`, `FactDescription`, `DecisionLiveQueryListener` | The Drools backend: session, routing, proposals, fuzzy arithmetic, why-not queries, fact rendering |
| `drools.events` | `VehicleSpeedEvent`, `FrontDistanceEvent`, `TTCEvent`, `LeftLaneVehicleEvent`, `CrosswalkDetectedEvent`, `StoppedAtStopLineEvent`, … (24 classes extending `BaseEvent`) | Time-stamped observations, commands and one rule-generated event |
| `drools.facts` | `VehicleStateFact`, `CurrentSpeedLimit`, `PendingSpeedLimit`, `FrontVehicleState`, `LaneOccupancyState`, `TrafficLightStateFact`, `BoxedInFact`, `FuzzyMembership`, `FuzzyConclusion`, … (26 classes) | Beliefs and internal states |
| `drools.listeners` | `RuleRuntimeTraceListener`, `WorkingMemoryTraceListener` | Timeline entries for activations, firings and fact changes |
| `prolog` | `PrologBackend`, `PrologKnowledgeSession`, `PrologProcess`, `PrologCycleResult`, `PrologProposal`, `PrologEngineException` | The SWI-Prolog backend: process management, JSON protocol, mapping of proposals, working memory and lane checks |
| `explanation` | `ExplanationTrace`, `TimelineEntry`, `TimelineEntryType` | Bounded (500 entries) chronological trace |
| `scenario` | `ScenarioLibrary`, `ScenarioFactory`, `ScenarioDefinition`, `*Definition`, `*Type` enums | Loads `scenarios.json` and builds a `WorldState` |
| `sensors` | `SensorManager`, `SpeedSensor`, `FrontRadarSensor`, `CameraSensor`, `BlindSpotSensor`, `EnvironmentSensor` | Observe the world at their own rates and emit events |
| `simulation` | `SimulationEngine`, `WorldState`, `WorldPhysicsEngine`, `DecisionActuator`, `RoadMap`, `MapLegend`, clocks, `SensorTelemetry`, `SensorSignificance`, `SimulationSnapshot` | The perception–decision–action loop, physics and light cycle, the road matrix, snapshots |
| `ui` | `DrivingExpertSystemApp` | JavaFX client (legacy; Drools only) |

---

## 4. Organisation of Rules, Facts and Events

### 4.1 Drools rule files

All files are in [`src/main/resources/rules/`](../src/main/resources/rules) and share the DRL package `rules`. Every rule belongs to an agenda group (`perception`, `decision` or `fallback`), except the control rule.

| File | Rules | Content |
|---|---|---|
| [`00-declarations.drl`](../src/main/resources/rules/00-declarations.drl) | 0 | Declares the 24 event types (`@role(event)`, `@timestamp`, `@expires`) |
| [`01-state-updates.drl`](../src/main/resources/rules/01-state-updates.drl) | 32 | `perception`: newest reading → belief; pending speed limit; stale clearing |
| [`control.drl`](../src/main/resources/rules/control.drl) | 1 | Orders the phases: perception → decision → fallback |
| [`emergency.drl`](../src/main/resources/rules/emergency.drl) | 4 | Emergency braking: persistent and immediate TTC, pedestrian ≤ 6 m, HIGH obstacle ≤ 8 m |
| [`pedestrian.drl`](../src/main/resources/rules/pedestrian.drl) | 6 | Braking curves and stop for a pedestrian in the path or crossing |
| [`front-vehicle.drl`](../src/main/resources/rules/front-vehicle.drl) | 21 | Following distance, overtaking and return, Art. 36 left-lane rule, boxed-in traffic |
| [`temporal.drl`](../src/main/resources/rules/temporal.drl) | 1 | Rain combined with repeated low TTC (Allen `coincides`) |
| [`speed.drl`](../src/main/resources/rules/speed.drl) | 5 | Legal limit, limit ahead, cruise speed, driver acceleration request |
| [`traffic-signs.drl`](../src/main/resources/rules/traffic-signs.drl) | 6 | Stop sign approach, full stop, 1.2 s hold (Allen `after`), proceed |
| [`traffic-lights.drl`](../src/main/resources/rules/traffic-lights.drl) | 7 | Commitment, red approach and hold, yellow, green |
| [`road-hazards.drl`](../src/main/resources/rules/road-hazards.drl) | 3 | Pothole slow-down; stopping before an obstacle |
| [`lane-safety.drl`](../src/main/resources/rules/lane-safety.drl) | 0 (+5 queries) | `safeToChangeLane` and its sub-goals (backward chaining) |
| [`avoidance.drl`](../src/main/resources/rules/avoidance.drl) | 10 | Lane changes around potholes and obstacles; keeping an ongoing lane change |
| [`lane-change.drl`](../src/main/resources/rules/lane-change.drl) | 4 | Lane changes requested from outside (allow or block) |
| [`road-law.drl`](../src/main/resources/rules/road-law.drl) | 1 | Keep-right rule |
| [`barrier.drl`](../src/main/resources/rules/barrier.drl) | 6 | Closed barrier: change lane or stop; open barrier |
| [`fuzzy-weather.drl`](../src/main/resources/rules/fuzzy-weather.drl) | 8 | Fuzzy weather controller (fuzzification, inference, defuzzification) |
| [`defaults.drl`](../src/main/resources/rules/defaults.drl) | 4 | Default baseline; moving off after a standstill |
| [`queries.drl`](../src/main/resources/rules/queries.drl) | 0 (+1 query) | Query "Current Driving Decision" |

Two implementation details matter when editing these files:

- **Imports are package-wide.** Every file declares `package rules`, so the wildcard imports in `01-state-updates.drl` (`drools.events.*`, `drools.facts.*`) make those classes visible in other files. `speed.drl` (`CruiseTargetFact`) and `road-law.drl` (`MoveOffState`) rely on this (A-10).
- **Rules do not choose the final action.** Each decision rule calls `droolsSupport.proposeDecision(...)`, and `DecisionContext` arbitrates afterwards ([ARCHITECTURE.md §5](ARCHITECTURE.md#5-action-selection-and-priority-resolution)). The README asks contributors to choose the priority by the *kind* of rule and to mirror every rule in Prolog.

### 4.2 Prolog sources

All in [`src/main/prolog/`](../src/main/prolog); Maven copies them to the classpath folder `prolog/`.

| File | Lines | Content |
|---|---|---|
| [`engine.pl`](../src/main/prolog/engine.pl) | 392 | Production-rule interpreter: working memory, dependency index, agenda, refraction, `prove/1` backward chaining, `explain/2`, `why_not/2`, `subgoal_results/2` |
| [`driving_state.pl`](../src/main/prolog/driving_state.pl) | 599 | State kept between cycles (the role of `01-state-updates.drl`); turns it into the facts of each cycle |
| [`driving_rules.pl`](../src/main/prolog/driving_rules.pl) | 1 933 | 107 rules mirroring the Drools decision rules (same names, precedence = salience); helper predicates; fuzzy sets |
| [`server.pl`](../src/main/prolog/server.pl) | 142 | JSON-lines protocol: `reset`, `tick`, `whynot`, `dependencyIndex` |

Code comments in the Prolog files are partly in Portuguese.

### 4.3 Facts and events

- **Events** (`drools/events/`) are observations stamped with simulation milliseconds.
  - Drools receives them through six named **entry points**, chosen by [`EventRouter`](../src/main/java/com/one2drive/projeto1/drools/EventRouter.java) and created on first use: `VEHICLE_STREAM`, `PERCEPTION_STREAM`, `ROAD_STREAM`, `TRAFFIC_STREAM`, `ENVIRONMENT_STREAM`, `COMMAND_STREAM`.
  - The Prolog backend receives the same objects serialised to JSON (with a `type` field).
- **State facts** (`drools/facts/`) are long-lived, mutable beliefs. `KnowledgeSession.create` inserts them once with neutral seed values, e.g. `CurrentSpeedLimit(120, "default")` and adjacent lanes *occupied* until reported.
- **Decision objects:** `DecisionContext` (all proposals of the cycle) and `DrivingDecision` (the published result) are also facts in the Drools session.

The per-rule catalogue is in [BUSINESS_RULES.md](BUSINESS_RULES.md).

---

## 5. Data and Configuration

### 5.1 Configuration files

| File | Content |
|---|---|
| [`src/main/resources/application.properties`](../src/main/resources/application.properties) | `server.port=8080`; `expert.backend=drools` (or `prolog`; Prolog needs `swipl`) |
| [`src/main/resources/META-INF/kmodule.xml`](../src/main/resources/META-INF/kmodule.xml) | Kbase `drivingKbase` (`packages="rules"`, `eventProcessingMode="stream"`) and ksession `drivingKsession` (`stateful`, `clockType="realtime"`). The clock type is overridden in code: pseudo clock in the server and tests, realtime in the JavaFX client |
| JVM property `prolog.executable` | Path of `swipl` when it is not on `PATH` or in the default install folders |
| [`frontend/proxy.conf.json`](../frontend/proxy.conf.json) | Angular dev server forwards `/api` to `http://localhost:8080` |
| [`frontend/.env.e2e`](../frontend/.env.e2e) | Backend and frontend URLs for end-to-end runs (no secrets) |

There are no environment-specific Spring profiles.

### 5.2 Simulation parameters

[`SimulationConfig.defaultConfig()`](../src/main/java/com/one2drive/projeto1/config/SimulationConfig.java):

| Parameter | Value | Parameter | Value |
|---|---|---|---|
| Tick length | 100 ms | Front radar range / rate | 130 m / 100 ms |
| Road block (matrix row) length | 5 m | Camera range / rate | 120 m × visibility / 200 ms |
| Default rows (empty road) | 100 (500 m loop) | Blind-spot range / rate | 30 m / 200 ms |
| Maximum acceleration | 1.6 m/s² | Environment sensor range / rate | 200 m / 400 ms |
| Comfortable deceleration | 2.8 m/s² | Speed sensor rate | 100 ms |
| Emergency deceleration | 8.5 m/s² | Radar distance noise (std. dev.) | 0.7 m (own lane) |
| Default cruise speed | 90 km/h | Lane-change lateral speed | 1.2 m/s (≈ 2.9 s per 3.5 m lane) |
| Yellow light | 3 s | Red light, counted while the ego waits at the line | 5 000 ms |
| `laneChangeSeconds` | 3.0 (getter only, not used) | | |

[`StoppingPolicy`](../src/main/java/com/one2drive/projeto1/decision/StoppingPolicy.java) holds the stopping figures shared by the rules and sent to Prolog:

| Constant | Value |
|---|---|
| Comfortable deceleration | 2.8 m/s² |
| Anticipation deceleration | 2.0 m/s² |
| Reaction time | 0.5 s |
| Red-light stop line | 18 m before the light (tolerance 3 m) |
| Stop-sign line | 15 m before the sign |
| Speed-limit approach buffer | 8 m |
| Obstacle stop offset | 10 m |
| Following gap | 12 m |
| Lane-change duration assumed by the rules | 3 s |

Many other thresholds (distances, TTC limits, target speeds) are literals in the DRL and Prolog rule files; [BUSINESS_RULES.md](BUSINESS_RULES.md) lists them.

### 5.3 Scenario data

[`src/main/resources/scenarios/scenarios.json`](../src/main/resources/scenarios/scenarios.json) is a JSON array of `ScenarioDefinition`. The fields are:

- `id`, `name`, `initialSpeedKmh` (also the cruise speed);
- `initialWeather` (`CLEAR` or `RAIN`);
- optional `initialLane` (default `RIGHT`) and `initialPositionMeters`;
- `leftMarking` / `rightMarking` (`DASHED` or `CONTINUOUS`; left = the LEFT|CENTER line, right = the CENTER|RIGHT line);
- `loopWorldObjects`;
- `map` (rows of `[LEFT, CENTER, RIGHT]` legend codes) and `mapLegend`;
- `roadElements`, `actors`, `timedActions`.

Default map legend codes ([`MapLegend.java`](../src/main/java/com/one2drive/projeto1/simulation/MapLegend.java)):

| Code | Element | Code | Element |
|---|---|---|---|
| `0` | free | `5` | crosswalk |
| `1` | obstacle (high severity) | `6` | red light |
| `2` | pothole (high severity) | `7` | green light |
| `3` | closed barrier | `8`–`13` | speed limits 30/50/70/90/100/120 km/h |
| `4` | stop sign | | |

The 15 scenarios are listed in [BUSINESS_DOMAIN.md §4](BUSINESS_DOMAIN.md#4-main-use-cases) and [TESTING.md §7](TESTING.md#7-scenario-coverage). They were renumbered S1–S15 after the baseline, and the speed-radar scenario was removed.

---

## 6. Entry Points and Start-up Sequence

| Entry point | How to start | What happens |
|---|---|---|
| Spring Boot server (primary) | `mvn spring-boot:run` | Spring creates `SimulationConfig`, `ScenarioLibrary` and `SimulationEngine` (S1, pseudo clock, backend from `expert.backend`, status `STOPPED`). `SimulationRuntimeService.startLoop()` schedules `tick()` + WebSocket publish every 100 ms. Ticks do nothing until `POST /api/simulation/start` |
| Angular UI | `cd frontend && npm start` | `ng serve` on port 4200 with `/api` proxied to 8080, WebSocket included; the page opens `ws://localhost:4200/api/ws/simulation` |
| Full stack | `./scripts/dev.sh` (macOS/Linux) or `scripts\dev.cmd` (Windows) | Starts both services, waits until both answer, stops both on Ctrl-C |
| JavaFX client (legacy) | `mvn javafx:run` | `DrivingExpertSystemApp` builds its own `SimulationEngine` with Drools and a **realtime** clock (A-07) |
| Prolog backend | select `prolog` in the UI toggle, set `expert.backend=prolog`, or `POST /api/simulation/backend/prolog` | `PrologProcess` starts `swipl -q -g main -t halt server.pl` from a temporary copy of `src/main/prolog` |

```mermaid
flowchart LR
    main["DrivingExpertSystemServerApplication.main"] --> spring["Spring context"]
    spring --> cfg["SimulationConfig.defaultConfig()"]
    spring --> lib["ScenarioLibrary.loadFromClasspath('/scenarios/scenarios.json')"]
    spring --> eng["SimulationEngine(config, S1, pseudoClock=true, backend)"]
    eng --> be{"expert.backend"}
    be -->|drools| ks["DroolsBackend → KnowledgeSession.createPseudoClock()"]
    be -->|prolog| pl["PrologBackend → swipl server.pl"]
    spring --> rt["SimulationRuntimeService.startLoop()"]
    rt -->|every 100 ms| tick["SimulationEngine.tick()"]
    rt -->|every 100 ms| pub["RealtimeSnapshotPublisher.publish() → WebSocket"]
```

The per-tick sequence is in [EXECUTION_FLOW.md](EXECUTION_FLOW.md).

---

## 7. Dependencies Between Components

```mermaid
flowchart TD
    FE["frontend (Angular)"] -->|REST + WebSocket| API["api (controllers, services)"]
    API --> SIM["simulation (SimulationEngine)"]
    JFX["ui (JavaFX)"] --> SIM
    SIM --> SEN["sensors"]
    SIM --> INF["inference (ExpertSystemBackend)"]
    INF --> DRL["drools (DroolsBackend)"]
    INF --> PRO["prolog (PrologBackend)"]
    PRO --> SWI[("swipl + src/main/prolog/*.pl")]
    DRL --> KB[("rules/*.drl")]
    DRL --> FACTS["drools.facts"]
    DRL --> DEC["decision"]
    PRO --> DEC
    SIM --> ACT["simulation (DecisionActuator, WorldPhysicsEngine)"]
    SIM --> SCN["scenario"]
    SEN --> WS["simulation (WorldState, RoadMap)"]
    SEN --> EVT["drools.events"]
    ACT --> DEC
    ACT --> WS
    SCN --> WS
    WS --> DOM["domain"]
    SIM --> EXP["explanation"]
```

Notable couplings:

- Both backends read `WorldState` directly for some vehicle-state fields (lane-safety flags, right-lane hazard, markings).
- `DecisionActuator` calls `WorldState.isLaneSafe` (see [ARCHITECTURE.md §9](ARCHITECTURE.md#9-architectural-limitations-and-audit-findings)).
- `PrologBackend` reuses the Drools event classes and `FactDescription` as its data model.

---

## 8. External Libraries

**Maven** ([`pom.xml`](../pom.xml); unchanged since the baseline except the Prolog resource entry):

- Drools, all 8.44.0.Final: `org.kie:kie-api`, `org.kie:kie-ci`, `org.drools:drools-core`, `drools-compiler`, `drools-model-compiler`, `drools-xml-support`;
- JavaFX 21.0.2: `org.openjfx:javafx-controls`, `javafx-graphics`;
- `com.fasterxml.jackson.core:jackson-databind` 2.17.2;
- Spring Boot 3.3.4: `org.springframework.boot:spring-boot-starter-web`, `spring-boot-starter-validation`;
- `org.slf4j:slf4j-api` 2.0.16;
- test scope: `org.junit.jupiter:junit-jupiter` 5.10.2.

Plugins: `maven-compiler-plugin` 3.13.0, `maven-surefire-plugin` 3.2.5, `spring-boot-maven-plugin` 3.3.4, `javafx-maven-plugin` 0.0.8.

**npm** ([`frontend/package.json`](../frontend/package.json)):

- runtime: `@angular/*` ^18.2, `rxjs` ~7.8, `zone.js` ~0.14.10, `tslib`;
- dev: `@angular/cli`, `@angular-devkit/build-angular` ^18.2.21, `typescript` ~5.5.2, `karma` ~6.4, `jasmine-core` ~5.2, `karma-chrome-launcher`, `karma-coverage`, `@playwright/test` ^1.48.2.

**External program:** SWI-Prolog 9.x, only for the Prolog backend and the Prolog tests.

---

## 9. Installation, Configuration and Execution

### 9.1 Prerequisites

- **JDK 17.** Verified in this analysis with Temurin 17.0.20.1.
- **Maven 3.9 or later.** Verified with 3.9.11.
- **Node.js and npm.** The README states Node 20.x / npm 10.x; the frontend unit tests were run here with Node 24.19.0 / npm 11.17.0.
- **Google Chrome (or Chromium),** for Karma and Playwright.
- **SWI-Prolog 9.x,** optional: needed for the Prolog backend and for 255 of the 473 backend tests, which are *skipped* without it. It was **not installed** on the analysis machine.

### 9.2 Build and run

| Purpose | Command | Verified during this analysis |
|---|---|---|
| Compile and run backend tests | `mvn clean test` | Yes, as `mvn -o -B test` on a copy of the repository: 473 run, 0 failures, 255 skipped (no SWI-Prolog) |
| Start backend on port 8080 | `mvn spring-boot:run` | No |
| Start with the Prolog backend | set `expert.backend=prolog` (needs `swipl`) | No |
| Install frontend dependencies | `cd frontend && npm ci` | No (dependencies were already installed) |
| Start frontend on port 4200 | `cd frontend && npm start` | No |
| Frontend unit tests | `cd frontend && npm test` | Yes, on a copy of `frontend/`: 57 executed, 52 passed, **5 failed** |
| Frontend production build | `cd frontend && npm run build` | No |
| Browser end-to-end tests | `cd frontend && npm run e2e` (backend and frontend running) | No ([TESTING.md §4](TESTING.md#4-test-execution-in-this-analysis)) |
| Full stack | `./scripts/dev.sh` or `scripts\dev.cmd` | No |
| JavaFX client | `mvn javafx:run` | No |

### 9.3 Useful URLs (from configuration)

- Angular UI: `http://localhost:4200`
- Backend status: `http://localhost:8080/api/simulation/status`
- Snapshot channel (WebSocket): `ws://localhost:8080/api/ws/simulation`

### 9.4 REST and WebSocket interface

| Method and path | Purpose |
|---|---|
| `POST /api/simulation/start`, `/pause`, `/resume`, `/stop`, `/reset` | Simulation lifecycle; `reset` reloads scenario S1 |
| `POST /api/simulation/backend/{drools\|prolog}` | Switch the deciding backend; reloads the current scenario with an empty working memory. HTTP 503 if the backend cannot start (previous one kept) |
| `POST /api/simulation/lane-change/{LEFT\|RIGHT}` | Lane-change request (a command; the rules decide) |
| `GET /api/simulation/state` | Full snapshot |
| `GET /api/simulation/status` | `backend` (constant `CONNECTED`), `drools` (`ACTIVE` or `ERROR`, for whichever backend is active), `simulation`, `expertBackend` |
| `GET /api/scenarios`, `POST /api/scenarios/{id}/load` | List and load scenarios |
| `PUT /api/world/map/cells` | Paint one matrix cell (`row`, `lane`, `code`; `0` frees it) |
| `POST /api/world/elements`, `PUT/DELETE /api/world/elements/{id}` | Add, update, remove road elements (`distanceAheadMeters` 1–1000) |
| `POST /api/world/actors`, `PUT/DELETE /api/world/actors/{id}` | Add, update, remove vehicles and pedestrians (`speedKmh` 0–180) |
| `POST /api/environment` | `rain` flag and/or `visibilityFactor`, `gripFactor` (0.2–1.0) |
| `GET /api/drools/working-memory`, `/decision`, `/rules/recent`, `/timeline/recent`; `GET /api/explanations/recent` | Inspection (the `/api/drools/...` paths serve whichever backend is active) |
| `ws://…/api/ws/simulation` | WebSocket, backend to frontend; JSON frames `{"event":"snapshot","payload":…}`: the last one on connect, then every tick and after each command |

World mutations sent while the simulation runs are queued and applied at the start of the next tick. CORS, and the WebSocket origin check, allow `http://localhost:4200` only.

---

## 10. Running the Existing Tests

| Suite | Location | Command | Count |
|---|---|---|---|
| Backend (JUnit 5) | `src/test/java/com/one2drive/projeto1/` | `mvn test` (single class: `mvn test -Dtest=SimulationEngineE2ETest`) | 479 test runs in 33 classes, of which 255 need SWI-Prolog |
| Frontend unit (Karma/Jasmine) | `frontend/src/app/**/*.spec.ts` | `cd frontend && npm test` | 62 cases in 10 files |
| Browser end-to-end (Playwright) | `frontend/e2e/simulation.spec.ts` | start backend + frontend, then `cd frontend && npm run e2e` | 18 tests |

Notes:

- `mvn test` runs only the JUnit suite.
- Without `swipl`, the Prolog tests are **skipped** through JUnit assumptions, not failed. That covers 3 Prolog classes, the PROLOG half of the 17 dual-backend E2E classes, and the dependency-index benchmark.
- `playwright.config.ts` uses a macOS Chrome path unless `PLAYWRIGHT_CHROMIUM_EXECUTABLE` is set.
- One Playwright test overwrites four `screenshot_*.png` files in the repository root.

Results, coverage and gaps are in [TESTING.md](TESTING.md).

---

## 11. Where to Look When Changing a Feature

| Change | Files to inspect |
|---|---|
| Add or change a driving rule | `src/main/resources/rules/<topic>.drl` **and** the mirror rule in `src/main/prolog/driving_rules.pl` (same name, precedence = salience); priority constants in `decision/DecisionPriority.java`; arbitration in `decision/DecisionContext.java`; tests in `drools/DrivingExpertSystemRulesTest.java`, `prolog/PrologRulesTest.java`, `prolog/PrologDroolsEquivalenceTest.java` |
| Change a stopping figure | `decision/StoppingPolicy.java` (sent to Prolog at reset; also used by the rules) |
| Change lane-change safety | `rules/lane-safety.drl`, `safe_to_change_lane/1` in `driving_rules.pl`, `decision/LaneChangeCheck.java`, `DroolsBackend.laneCheck` |
| Change the fuzzy controller | `rules/fuzzy-weather.drl`, `drools/FuzzySupport.java`, and the "Lógica difusa" section of `driving_rules.pl` |
| Add a new kind of observation | class in `drools/events/`; declaration in `rules/00-declarations.drl`; routing in `drools/EventRouter.java`; producer in `sensors/`; perception rule in `rules/01-state-updates.drl`; `on_event/2` in `driving_state.pl` |
| Add a sensor | `sensors/` (extend `AbstractSensor`); register it in the `SensorManager` list in the `SimulationEngine` constructor; ranges and rates in `config/SimulationConfig.java` |
| Change thresholds | DRL and Prolog literals; `SimulationConfig`; `StoppingPolicy`; `BlindSpotSensor.HAZARD_LOOKAHEAD_METERS` (60 m); `WorldState.sideLaneOccupancy` |
| How a decision moves the car | `simulation/DecisionActuator.java`; `simulation/WorldPhysicsEngine.java`; `domain/EgoVehicleState.java` |
| Lane-change execution | `DecisionActuator.applyLaneDecision` / `requestLaneChange`; `WorldPhysicsEngine.advanceLaneChange`; `SimulationEngine.settleManeuver` |
| Traffic-light cycle | `domain/TrafficLight.advance`, `WorldPhysicsEngine.updateWorld`, `SimulationConfig` (yellow, red hold) |
| Backends | `inference/*`, `drools/DroolsBackend.java`, `prolog/*`, `SimulationEngine.createBackend` / `switchBackend` |
| Scenario content | `scenarios.json`; `scenario/ScenarioFactory.java`; enums `ScenarioRoadElementType`, `ScenarioActorType`, `ScenarioTimedActionType` |
| Road matrix and legend | `simulation/RoadMap.java`, `simulation/MapLegend.java` |
| REST API | `api/controller/*`, `api/model/*`, `api/service/ApiModelMapper.java`, `ApiRequestMapper.java`; frontend client `frontend/src/app/core/api/backend-api.service.ts` |
| Timeline and explanations | `explanation/ExplanationTrace.java`, `drools/listeners/*`, `PrologBackend.traceFiredRules`, `simulation/SensorSignificance.java`, `api/service/InferenceNarrator.java` |
| Decision panel | `frontend/src/app/expert-system/decision-panel/` (channels, proposals and outcomes) |

---

## 12. Structural Observations

Details and evidence are in [ARCHITECTURE.md §9](ARCHITECTURE.md#9-architectural-limitations-and-audit-findings) and [BUSINESS_RULES.md §17](BUSINESS_RULES.md#17-consistency-audit).

- **Duplicated knowledge base.** Every decision rule exists in DRL and in Prolog, and their equivalence tests were skipped here (A-14).
- **Unused elements:**
  - `ProcessedEvent` is never instantiated;
  - `SimulationConfig.laneChangeSeconds` has only a getter;
  - no rule reads `VisibilityEvent`, `RoadGripEvent`, `ReducedVisibilityEvent`, `ReducedGripEvent` or `CrosswalkDetectedEvent`;
  - `DrivingAction.WAIT` is never proposed;
  - no rule reads `VehicleStateFact.leftAdjacentLaneSafe` or `rightAdjacentLaneSafe`.
- **Screenshots in the repository root:** the current Playwright suite writes only `screenshot_viewport_1440x900.png`, `screenshot_viewport_1920x1080.png`, `screenshot_toolbox_objects_road.png` and `screenshot_clean_ego_badge.png`. The other nine are leftovers, four of which are named after the removed speed radar (`screenshot_A_far_from_radar.png` … `screenshot_D_passed_radar.png`).
- **Outdated statements elsewhere:** some statements in `README.md`, `docs/report-implementation-audit.md` and `diagrams/` no longer match the code (test counts, rule counts, `window:length(5)`, `ABORTED`, Prolog rule names). The list is in [ARCHITECTURE.md §9.4](ARCHITECTURE.md#94-discrepancies-with-existing-documentation).
