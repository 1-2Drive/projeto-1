# Testing and Validation

**Audience:** developers, maintainers and evaluators who need to know how the system is tested, what the tests prove, what was actually executed during this analysis, and where testing is missing.

**Related documents:** [BUSINESS_RULES.md](BUSINESS_RULES.md) gives the measured test coverage of each rule; [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md) links requirements and scenarios to tests.

---

## 1. Evidence Categories

This document keeps four kinds of statements apart:

| Category | Meaning | Where |
|---|---|---|
| **Existing tests** | Automated tests present in the repository at commit `70082b4` | §3 |
| **Tests executed in this analysis** | Existing tests actually run during this analysis, with date, environment and command | §4 |
| **Passed / failed / skipped** | Results observed in those runs (not copied from earlier reports) | §4 |
| **Proposed tests** | Tests suggested by this analysis; **not implemented and not executed** | §13 |

In addition:

- §5 reports **exploratory probes**: small programs written for this analysis and run against the real engine on a copy of the repository. They are evidence about behaviour, not part of the project's test suite.
- §6 reports **rule-firing coverage**, measured with a listener added to a copy of the code. No line or branch coverage tool is configured, so no coverage percentages are given.

---

## 2. Testing Tools and Frameworks

| Tool | Version (declared) | Used for |
|---|---|---|
| JUnit Jupiter | 5.10.2 | All backend tests: `@Test`, `@ParameterizedTest` with `@EnumSource(ExpertBackendType.class)` (one run per backend) or `@MethodSource`, `@Timeout`, `@DisplayName`, assumptions |
| Maven Surefire | 3.2.5 | Running the backend tests (`mvn test`) |
| Drools pseudo clock (`SessionPseudoClock`) | Drools 8.44.0.Final | Deterministic time in rule and engine tests |
| `DroolsTestHarness` | project class | Rule-level helper: production cycle order (`beginDecisionCycle`, speed event, events, `fireAllRules`, arbitration) |
| `PrologTestHarness`, `BackendTestSupport` | project classes | Prolog rule-level helper; engine factory per backend. Both call `assumeSwiplAvailable()`, which **skips** a test when `swipl --version` cannot run |
| Karma + Jasmine | Karma ~6.4, Jasmine ~5.2 | Angular unit tests in ChromeHeadless (`npm test`) |
| Angular TestBed | Angular 18.2 | Component and service tests |
| Playwright | ^1.48.2 | Browser end-to-end tests against the running backend and frontend (`npm run e2e`) |

Not present: Spring MVC test support (MockMvc, controller tests), JaCoCo or any Java coverage tool, mutation testing, load testing.

---

## 3. Test Organisation (Existing Tests)

### 3.1 Backend (JUnit 5): 479 test runs in 33 classes

All classes are under `src/test/java/com/one2drive/projeto1/`. "E2E" in a Java class name means an **engine-level integration test**: it runs the real `SimulationEngine` with sensors, rules and actuator, but without HTTP or a browser.

17 E2E classes are parameterised over `ExpertBackendType` and run every test once on Drools and once on Prolog. The "Prolog-dependent" column counts the runs that need SWI-Prolog.

| Class | Runs | Prolog-dependent | What it exercises |
|---|---|---|---|
| `drools/DrivingExpertSystemRulesTest` | 60 | 0 | Rules in isolation through the harness: knowledge-base compilation, speed limits (pending and active), lights and commitment, stop sign, pedestrians (curves, crossing, emergency), TTC, lanes, potholes, barrier, fuzzy weather, rain-TTC coincidence, following curve, overtakes, Art. 36, trends per target, obstacle emergency by physical lane, room to follow, conflicts, listeners, query |
| `drools/AgendaPhasesTest` | 1 | 0 | Control, perception, decision, fallback fire in that order |
| `drools/FuzzySupportTest` | 6 | 0 | Membership functions, centroid, `light` alone, tie-break |
| `drools/FactDescriptionTest` | 7 | 0 | Readable descriptions of facts and decisions |
| `decision/DecisionContextTest` | 5 | 0 | Ceilings by minimum, base capped by ceilings, mandatory stop capped by a lower ceiling, veto only while braking, lane tie-breaks |
| `domain/EgoVehicleStateTest` | 6 | 0 | Driving state reported for each action |
| `sensors/CameraSensorTest` | 5 | 0 | Range shortened by visibility; stop signs only in the ego lane |
| `api/service/RealtimeSnapshotPublisherTest` | 4 | 0 | A stalled WebSocket client does not block the publisher or the other clients; a client whose write fails is dropped and closed; a new client gets the last snapshot at once; a closed client is no longer sent |
| `api/RealtimeWebSocketE2ETest` | 4 | 0 | The real server on a free port and a real WebSocket client: the current snapshot on connect; S8's Drools `EMERGENCY_BRAKE` (rule, timeline, working memory) arriving in the tick it is inferred; a closed client dropped and a reconnecting one resumed at once; a foreign browser origin refused (403) |
| `simulation/DecisionActuatorTest` | 8 | 0 | Dead band, gentle acceleration, proportional and comfortable braking, `BRAKE` never raises the speed, lane change started as decided, lane-change duration assumption |
| `simulation/RoadMapTest` | 5 | 0 | Road matrix: cells, wrap-around, snapping, legend, invalid input |
| `simulation/CrosswalkE2ETest` | 2 | 0 | A pedestrian gets a crosswalk; the crosswalk outlives the pedestrian |
| `simulation/SimulationEngineE2ETest` | 28 | 2 | Full loop: speed signs (in sequence, every lane, higher limit), stop sign (own lane only, steady approach), TTC, pedestrian, pothole, weather, reset, red/green light, actuator guard, cruise recovery, passed objects, overtaken vehicles, rain settling |
| `simulation/RoadLawAndOvertakingE2ETest` | 36 | 18 | Moving off, overtaking (S14), continuous line (S15), pedestrian (S7), keep-right, multi-lane return, blocked return, regression tests `BUG 1…3` |
| `simulation/LaneChangeManeuverE2ETest` | 16 | 8 | Pothole avoidance choices, no oscillation, driver requests, rain |
| `simulation/LaneChangeLawE2ETest` | 4 | 2 | Barrier passed through the lane the sensor reports clear; the line crossed from RIGHT |
| `simulation/LaneChangeWhyNotE2ETest` | 11 | 6 | Why-not answers (no lane, vehicle alongside, continuous line, maneuver running, no room to follow); Drools vs Prolog answers |
| `simulation/LeftLaneOvertakingE2ETest` | 12 | 6 | Art. 36: slower vehicle in the left lane overtaken on the left, never passed on the right |
| `simulation/MultiVehicleOvertakingE2ETest` | 8 | 4 | Several slow vehicles; continuing CENTER → LEFT; every lane slow |
| `simulation/BoxedInTrafficE2ETest` | 10 | 5 | Slow traffic in every lane: no impossible overtake, give up and return right (also in rain and low visibility), minimum gap, no stop-and-go |
| `simulation/StationaryFrontVehicleE2ETest` | 6 | 3 | Going around a stationary vehicle; stopping behind it while blocked |
| `simulation/PedestrianConflictE2ETest` | 12 | 6 | Crossing and standing pedestrians, no conflict, crossing in progress, red light beyond a pedestrian |
| `simulation/RedTrafficLightStopE2ETest` | 10 | 4 | Red light at 30–90 km/h: no emergency, stops before the line, road-wide, held for the red time, moves off on green |
| `simulation/RedLightHoldTimerE2ETest` | 6 | 3 | Red time counted only at rest; pedestrian prevails over green |
| `simulation/TrafficLightCycleE2ETest` | 20 | 10 | Yellow commitment (stop or proceed), red first seen close or past the line, light behind the ego |
| `simulation/FarStopNearObstacleE2ETest` | 2 | 1 | Avoid an obstacle while slowing for a red light further ahead |
| `simulation/SideReadingAfterLaneChangeE2ETest` | 2 | 1 | Returning right waits for a reading of the new right side |
| `simulation/PresentationDemoE2ETest` | 2 | 1 | S12: overtakes in rain, stops for the pedestrian |
| `simulation/RoadMapE2ETest` | 7 | 2 | Looping road, wall of obstacles, S13 course without touching an obstacle, painting cells, road edge |
| `simulation/DependencyIndexBenchmarkTest` | 1 | 1 | Prolog only: decisions identical with and without the dependency index; cost printed |
| `prolog/PrologRulesTest` | 56 | 56 | Prolog rules in isolation, mirroring the Drools rule tests; why-not |
| `prolog/PrologDroolsEquivalenceTest` | 110 | 110 | Same inputs to both engines, single cycles and sequences: same decision |
| `prolog/PrologSimulationE2ETest` | 7 | 6 | Prolog in the full loop (lights, stop sign, working memory, switching); one test checks that a failed switch keeps the previous backend (runs without SWI-Prolog) |
| **Total** | **479** | **255** | |

Helpers (not test classes): `DroolsTestHarness`, `PrologTestHarness`, `BackendTestSupport`, `OvertakingProbe`, `TrafficLightProbe`.

Scenarios loaded from `scenarios.json` by tests that run without Prolog: S1, S2, S4, S5, S7, S8, S10, S12_PRESENTATION_DEMO, S13_MATRIX_COURSE, S14_SLOW_VEHICLE_OVERTAKE, S15_CONTINUOUS_LINE_BLOCKED. `DependencyIndexBenchmarkTest` loads all 15, but it is Prolog-only.

**How the rule harness differs from production.** Since the baseline, `DroolsTestHarness.cycle` follows the production order: `beginDecisionCycle` with session update, a `VehicleSpeedEvent` each cycle, arbitration and the decision update. It still:

- inserts no `CruiseTargetFact`, except where one test adds it;
- has no sensors, actuator or physics;
- inserts the events the test chooses.

### 3.2 Frontend unit tests (Karma/Jasmine): 62 cases in 10 files

| Spec file (under `frontend/src/app/`) | Declared `it(…)` | Topics |
|---|---|---|
| `app.component.spec.ts` | 1 | creates the app |
| `core/api/backend-api.service.spec.ts` | 2 | state request, start command |
| `core/state/simulation-state.service.spec.ts` | 5 | snapshot handling; active rule engine; backend switch and its failure; realtime reconnection with back-off |
| `core/websocket/realtime-stream.service.spec.ts` | 5 | WebSocket URL and snapshot frames; error on close and on a non-JSON frame; close on unsubscribe; silent-socket watchdog |
| `expert-system/decision-panel/decision-panel.component.spec.ts` | 7 | why-not per side, speed and lane channel lines, proposal outcome badges, collapse, emergency highlighting, empty state |
| `scenario/toolbox/toolbox.component.spec.ts` | 10 | control grouping, rain toggles, visibility slider, configuration drawer, road-wide elements without a lane, stop-sign lane, distance validation |
| `simulation/road-canvas/road-canvas.component.spec.ts` | 23 | world items, road-wide signs and lights, sensor fields (camera cone shortened by rain, radar corridor, blind-spot pads), lane-change motion, badges, passed objects, crosswalk, turn-signal lamps |
| `simulation/road-map/road-map.component.spec.ts` | 3 | matrix drawing, car position, painting |
| `simulation/simulation-page/simulation-page.component.spec.ts` | 2 | button states, demo button |
| `telemetry/speed-advisor/speed-advisor.component.spec.ts` | 1 | above-recommendation styling |

Some cases are generated in loops (e.g. one per turn-signal state), so Karma executes 62 cases from 59 declarations. These tests check presentation and HTTP/WebSocket plumbing with mocked data; they do not test driving rules.

### 3.3 Browser end-to-end tests (Playwright): 20 tests

Files `frontend/e2e/simulation.spec.ts` (1–18) and `frontend/e2e/realtime.spec.ts` (19–20). They run against the real stack (Angular + Spring Boot + the active backend) and need both servers running. They share that one simulation, so `playwright.config.ts` runs them one at a time (`workers: 1`). Tests:

1. starts on a clean stopped road and only runs after START;
2. manual speed limit added at 150 m drives the expert system;
3. speed limit has no lane selector and is painted on all three lanes;
4. traffic light has no lane selector and is drawn on all three lanes;
5. stop sign is placed only in the lane chosen in the drawer;
6. pedestrian interaction produces a safety decision;
7. pothole avoidance visibly moves the ego vehicle from RIGHT to CENTER;
8. blocked right lane forces pothole avoidance to use the clear left lane;
9. sensor groups show a radar target and a camera sign detection;
10. presentation demo progresses across its phases;
11. overtaking a slow vehicle shows turn signals, changes lane, passes, and returns to the right lane;
12. TEST A: full viewport responsiveness;
13. TEST B: toolbox object creation appears in road canvas and matrix;
14. TEST C: painting a matrix cell updates the road canvas;
15. TEST E: clean EGO badge;
16. capture evidence screenshots;
17. world mutation drawer layout stability;
18. VISUAL E2E: forward motion and approaching objects;
19. simulation updates arrive over the WebSocket, without HTTP polling;
20. a dropped channel reconnects on its own and goes live again.

Notes:

- `playwright.config.ts` defaults to a macOS Chrome path unless `PLAYWRIGHT_CHROMIUM_EXECUTABLE` is set.
- Test 16 writes four `screenshot_*.png` files into the repository root.

---

## 4. Test Execution in This Analysis

To avoid modifying the repository (including `target/` and `frontend/.angular/`), the suites were run on **copies** in a temporary directory. Maven ran **offline** (`-o`): every dependency was already in the local Maven cache, so nothing was downloaded or installed. SWI-Prolog is **not installed** on the analysis machine.

| Suite | Command | Copy of | Environment | When (UTC) | Result |
|---|---|---|---|---|---|
| Backend JUnit | `mvn -o -B test` | `pom.xml` + `src/` at `70082b4` | JDK 17 (Temurin), Maven 3.9.11, Windows 11 | 2026-10-18 11:22:56–11:23:15 | **473 run, 0 failures, 0 errors, 255 skipped — BUILD SUCCESS** |
| Frontend unit | `npm test` (= `ng test --watch=false --browsers=ChromeHeadless`) | `frontend/` with a full copy of `node_modules` (no `.angular`, `dist`) | Node 24.19.0, npm 11.17.0, Angular 18.2.14, Chrome Headless 155 | 2026-10-18 11:27:22–11:27:32 | **57 executed: 52 passed, 5 FAILED** (exit code 1) |
| Browser E2E | — | — | — | — | **Not executed** |

**Backend: what the skipped tests mean.**

- All 255 skips come from `assumeSwiplAvailable()` (JUnit message "SWI-Prolog (swipl) is not installed"):
  - `PrologDroolsEquivalenceTest` 110/110;
  - `PrologRulesTest` 56/56;
  - `PrologSimulationE2ETest` 6/7;
  - `DependencyIndexBenchmarkTest` 1/1;
  - the PROLOG run of every dual-backend E2E test.
- The **218 executed tests all passed**. They cover Drools and the backend-independent code.
- **No Prolog behaviour, and no claim of Drools/Prolog equivalence, was verified by execution.**
- The per-class numbers are in §3.1 (from the Surefire reports).

Warnings were benign: a Maven model warning for `javafx-controls`, and Drools "Unable to find pom.properties".

**Frontend: the five failures** (all genuine, i.e. reproducible from the code, not environmental):

| Spec | Test | Error | Cause |
|---|---|---|---|
| `SimulationPageComponent` | "disables pause and enables start when stopped" | `TypeError: this.expertBackend is not a function` | The spec's state stub lacks `expertBackend` / `expertBackendError`, which the component now reads (`simulation-page.component.ts:61-62`) |
| `SimulationPageComponent` | "calls demo action from demo button" | same | same |
| `RoadCanvasComponent` | "should flash the ego blinker lamps for indicator LEFT, and show none while off" | Expected `'_ngcontent-a-c2069264281_indicator-blink'` to be `'indicator-blink'` | Angular's emulated view encapsulation scopes the `@keyframes` name; the test compares the raw name |
| `RoadCanvasComponent` | same, indicator RIGHT | same | same |
| `RoadCanvasComponent` | same, indicator HAZARD | same | same |

**Why the Playwright suite was not executed:**

1. It requires the backend and the Angular dev server to be running (ports 8080 and 4200).
2. Test 16 overwrites tracked files (`screenshot_*.png`) in the repository root, which this analysis was not allowed to modify.
3. Its default browser path is macOS-specific.

Its results are therefore **not verified** here.

**Earlier reports versus this run.**

| Source | Backend | Karma | Playwright |
|---|---|---|---|
| README at HEAD | 389 | 38 | 16 |
| Earlier audit report | 79 | — | — |
| This run | 473 (218 executed, 255 skipped) | 57 executed, 5 failing | 18 present |

**Running the tests in the repository itself:**

```bash
mvn test                       # backend (writes target/); install SWI-Prolog 9.x to run the Prolog half
mvn test -Dtest=SimulationEngineE2ETest
cd frontend && npm test        # Angular unit tests (needs Chrome; set CHROME_BIN if not found)
# Browser E2E: start backend (mvn spring-boot:run) and frontend (npm start) first,
# set PLAYWRIGHT_CHROMIUM_EXECUTABLE on Windows/Linux, then:
cd frontend && npm run e2e
```

---

## 5. Exploratory Verification Probes

**Method.**
- **What:** JUnit classes written for this analysis (`ProbeTest`, `Probe2Test`, `Probe3Test`), placed only in the temporary copy of the repository and never added to the project.
- **How they ran:** with `mvn -o -B -q test -Dtest=…` on 2026-10-18 (environment as in §4).
- **How they drive the system:** each probe uses the real `SimulationEngine` with the **Drools** backend and the pseudo clock (as the server does), or a real knowledge session, and prints its observations.
- **No pass/fail:** probes contain no assertions.
- **History:** the probe numbering of the baseline analysis is kept where the same question was asked again. The baseline probes P-05 (speed radar) and P-12 (old S7) no longer apply; P-06 now covers the library scenarios.

| Probe | Set-up | Observation at `70082b4` | Conclusion |
|---|---|---|---|
| P-01 | Ego 30 km/h in RIGHT (cruise 30), 30 km/h sign 60 m ahead; 30 s clear and rain | Clear: max 30.3, final 30.0 km/h. Rain: after the sign, `MAINTAIN_SPEED 30` ("Maintain speed when compliant…") on 238 ticks; the weather ceiling (51 km/h) `LOST_TO` the compliant base; final 30.0 km/h. Before the sign the speed rose to 40.8 km/h under the weather ceiling (see P-16) | Legal limit respected (R-01 fixed) |
| P-02a/b | Ego 50 km/h; vehicle at 18 km/h 45 m / 120 m ahead in RIGHT | Overtake RIGHT → CENTER at 3.1 s / 9.9 s; back to RIGHT at 14.5 s / 21.3 s; minimum same-lane gap 26.7 m / 36.1 m; overtaking state cleared | Overtaking latch fixed (R-05) |
| P-02c | Ego LEFT 20 km/h; vehicle LEFT 15 km/h 30 m ahead | `REDUCE_SPEED 36.8` ("Reduce speed when closing front vehicle") with +0.8 m/s²: 20 → 24 km/h while closing; then held; then following at 15 km/h with a 19 m gap | `REDUCE_SPEED` still accelerates (R-02); following gap fixed (R-03) |
| P-02d | Ego LEFT 50 km/h, LEFT\|CENTER continuous; vehicle LEFT 18 (10) km/h 120 m ahead; 60 s | Minimum gap 20.3 (17.2) m; gap 29.4–31.4 m in the second half; 114 (115) decision changes in 30 s between the cruise-speed rule and "Fall back behind slow traffic…" | R-03 fixed; oscillation (R-25) |
| P-03 | Cruise 70; green light at 300 m; with and without a 90 km/h sign; 90 s | After the light: belief reset to `none`; with the sign 748/748 ticks `MAINTAIN_SPEED 90`, no decision change | Stale light fixed (R-08) |
| P-03c | Same, speed near the light | 90 → 72.9 km/h at the light (sign); 70 → 50.0 km/h (no sign); decision within 40 m: `CONTINUE 50` ("Green traffic light allows progression…") | Green light lowers speed (R-24) |
| P-03d | Cruise 70; open barrier at 150 m in the ego lane | `CONTINUE 35` ("Open barrier allows continue…") within 35 m; 50.8 km/h at the barrier | Same mechanism (R-24) |
| P-04 | 5 cycles of TTC 1.2 s at 15 m; pseudo vs realtime session clock | Pseudo: `EMERGENCY_BRAKE` (priority 70) every cycle. Realtime: 0 TTC events kept; `REDUCE_SPEED` | Time-based rules disabled with a realtime clock (A-07) |
| P-04b | S4 through the engine with the realtime clock (as the JavaFX client) vs pseudo; 60 s | Pseudo: stopped 8.9 s, moved off 10.7 s. Realtime: stopped 8.9 s and **never moved off** (`STOP`, "Stop sign requires full stop near line") | A-07 also blocks Allen-timed rules |
| P-06 | S3, S6, S7, S8, S9, S10, S11 as defined, 30 s each | S6: barrier avoided through CENTER. S8: front vehicle tracked, emergency brake (27 ticks) with the overtake's continuation vetoed. S9: lane change refused ("Block lane change when target lane occupied"). S11: front vehicle tracked, limit, rain, pedestrian. S3, S7, S10 as intended | Scenario data fixed (R-20) |
| P-07 | Ego 60 km/h; vehicle 20 m ahead doing 90 km/h | `REDUCE_SPEED 35` (trend rule) 21 times while the gap grew 28 → 45 m; ego slowed to 38.8 km/h | Trend rule measures spread (R-09) |
| P-08 | Ego 10 km/h; standing pedestrian 44 m ahead | No acceleration; `STOP` from 30.4 m; at rest 30.3 m before the pedestrian | `BRAKE`/`STOP` no longer accelerate |
| P-09 | S2 as defined, rules fired per tick, 20 s | Sign read at about 118 m (0.9 s) and kept pending; deceleration about 2 m/s²; 50 km/h at 135.7 m; limit active at the sign (141.3 m, 7.1 s). Over 200 ticks: speed-sensor rule 0, baseline 200, control 200 firings | Worked example ([EXECUTION_FLOW.md §4.1](EXECUTION_FLOW.md#41-speed-limit-sign-scenario-s2)); R-16, R-17 |
| P-10 | Standing pedestrian 110 m ahead; 50/60/65/70/90/120 km/h; clear and rain | First pedestrian decision at about 107 m (80–84 m in rain). Stopped before the pedestrian from 50 (21.6 m), 60, 65 (25.0 m), 70 (23.2 m), 90 km/h (3.1 m); from 120 km/h **reached at 74.5 km/h** | R-04 largely fixed; residual at high speed; no collision model (A-13) |
| P-11 | S15 as defined at 50, 70, 90 km/h | No lane change. 50/70: emergency then comfortable braking, at rest 10.4 m before the obstacle. 90 (obstacle appears 45 m ahead): reached at 2.3 km/h | R-06, R-07 fixed (90 km/h residual is a physical limit) |
| P-13 | No free side (CENTER\|RIGHT continuous); closed barrier 150 m ahead in RIGHT; 50/60/70/90 km/h | "Closed barrier requires stop" from about 55 m. 50: stopped 20.4 m short; 60: 6.2 m short; **70: reached at 29.7 km/h; 90: reached at 64.8 km/h** | Barrier stop without escalation (R-22) |
| P-14 | No free side; MEDIUM obstacle appears 26 m ahead at 50 km/h; with/without a standing pedestrian 32 m ahead | Without: emergency braking, stopped 10.3 m short. With: `STOP` (pedestrian, priority 60) won, comfortable braking, **obstacle reached at 27.8 km/h** | Braking strength ignored (R-11) |
| P-15 | S3 as defined | Committed to stop at 118 m; braking from 94.7 m; at rest 19.2 m before the light (9.1 s); green at 14.1 s; `RESUME 30`; move-off; cruise build-up | Worked example ([EXECUTION_FLOW.md §4.5](EXECUTION_FLOW.md#45-red-light-with-the-light-cycle-scenario-s3)) |
| P-16 | Rain, no sign; cruise 30/40/85 km/h; 20 s | Every tick `REDUCE_SPEED 50.6` ("Defuzzify weather speed ceiling"); final 50.6 km/h in all three (30 and 40 km/h cars accelerated) | Weather ceiling as target (R-23) |
| P-18 | Ego RIGHT 50 km/h; vehicle in LEFT 60 m ahead at 18 km/h | Passed at 6.8 s at 50 km/h under "Default maintain speed baseline" | Art. 36 rule covers the adjacent lane only (R-26, observation) |

---

## 6. Rules and Behaviours Covered

**Method (measured, Drools only).** On a further copy of the repository, a listener was added to every `KieSession` to record which rule fired during which test; the test identity came from an auto-registered JUnit extension. The suite result was unchanged (473 run, 0 failures, 255 skipped). Each rule was then classified:

- **Named in an executed test:** the rule name appears as a string in a test class that ran (usually an assertion on the deciding rule) — 30 rules.
- **Fired, not named:** the rule fired in at least one executed test, but no executed test refers to it by name — 81 rules. The test can still depend on its effect.
- **Never fired:** the rule did not fire in any executed test — 8 rules.

| Rule file (rules) | Named in an executed test | Fired, not named | Never fired |
|---|---|---|---|
| `01-state-updates.drl` (32) | 0 | 29 | 3 |
| `control.drl` (1) | 0 | 1 | 0 |
| `emergency.drl` (4) | 4 | 0 | 0 |
| `pedestrian.drl` (6) | 3 | 3 | 0 |
| `front-vehicle.drl` (21) | 7 | 14 | 0 |
| `speed.drl` (5) | 3 | 2 | 0 |
| `traffic-signs.drl` (6) | 3 | 3 | 0 |
| `traffic-lights.drl` (7) | 2 | 5 | 0 |
| `road-hazards.drl` (3) | 1 | 2 | 0 |
| `avoidance.drl` (10) | 1 | 8 | 1 |
| `lane-change.drl` (4) | 1 | 3 | 0 |
| `road-law.drl` (1) | 1 | 0 | 0 |
| `barrier.drl` (6) | 1 | 1 | 4 |
| `fuzzy-weather.drl` (8) | 1 | 7 | 0 |
| `temporal.drl` (1) | 1 | 0 | 0 |
| `defaults.drl` (4) | 1 | 3 | 0 |
| **Total (119)** | **30** | **81** | **8** |

**Rules that never fired in the test suite (8):**

- "Update vehicle state from speed sensor" (unreachable in engine runs, R-16);
- "Drop a pending speed limit the camera no longer sees" (tested only in the skipped Prolog suite);
- "Clear stale barrier perception";
- "Avoid obstacle in left lane by changing right";
- "Avoid closed barrier by changing left when safe", "Avoid closed barrier by changing left from centre when safe", "Avoid closed barrier by changing right when safe" (probe P-06 shows the first one working in S6);
- "Open barrier allows continue if no higher priority" (observed in probe P-03d).

Four rules are named only in Prolog test classes, which were skipped. All four fired in executed tests:

- "Maintain ongoing lane change left avoidance";
- "Move off when stopped and nothing holds the vehicle";
- "Overtake slow front vehicle via left lane";
- "Build back up to the scenario cruise speed when no sign governs the road".

The per-rule numbers are in the "Tests" fields of [BUSINESS_RULES.md](BUSINESS_RULES.md). Prolog rule coverage could not be measured.

**Behaviours covered well:**

- speed limits (pending, approach, activation at the sign, several signs, every lane);
- the stop-sign lifecycle;
- the traffic-light commitment and cycle (yellow, red, green, light behind the car);
- TTC emergency braking;
- pedestrian conflicts (crossing, standing, not in conflict, crossing in progress);
- following distance and gap reopening;
- overtaking on the left, including multi-vehicle and boxed-in traffic;
- Art. 36 behind a left-lane vehicle;
- lane-change why-not;
- pothole avoidance;
- the arbitration rules (`DecisionContextTest`);
- actuator speed tracking;
- WebSocket back-pressure, reconnection and origin check;
- reset isolation.

---

## 7. Scenario Coverage

| Scenario | Loaded by executed tests | Observation in this analysis | Status |
|---|---|---|---|
| S1 Empty safe road | many E2E tests (base for added objects) | — | Tested |
| S2 90 to 50 speed limit | `SimulationEngineE2ETest` (reset, perception) | P-09: limit pending, reached 4.3 m before the sign | Tested + probe |
| S3 Red traffic light | none (equivalents in `RedTrafficLightStopE2ETest`, `TrafficLightCycleE2ETest`) | P-06, P-15: stops 19.2 m before the light, green after 5 s | Behaviour tested by equivalents; scenario verified by probe |
| S4 Stop sign | `SimulationEngineE2ETest.stopSignFlowBringsVehicleToFullStopThenProceeds` | P-04b: also run with the realtime clock (never moves off) | Tested |
| S5 Pothole | `SimulationEngineE2ETest` | — | Tested |
| S6 Closed barrier | none (equivalent in `LaneChangeLawE2ETest`) | P-06: avoided through CENTER and back | Verified by probe only |
| S7 Pedestrian crossing | `RoadLawAndOvertakingE2ETest` (asserts braking only) | P-06: braked and stopped | Partly tested |
| S8 Critical TTC | none | P-06: emergency brake, overtake continuation vetoed | Verified by probe only |
| S9 Lane change blocked | none (equivalents in RT, `LaneChangeManeuverE2ETest`) | P-06: request blocked | Verified by probe only |
| S10 Rain low grip | `SimulationEngineE2ETest` (rain settles without hunting) | P-06: 85 → 50.6 km/h | Tested |
| S11 Complex sequential | none | P-06: all phases handled | Verified by probe only |
| S12_PRESENTATION_DEMO | `PresentationDemoE2ETest`; Playwright test 10 (not executed) | — | Tested (Drools) |
| S13_MATRIX_COURSE | `RoadMapE2ETest` (lap after lap without touching an obstacle) | — | Tested |
| S14_SLOW_VEHICLE_OVERTAKE | `RoadLawAndOvertakingE2ETest`; Playwright test 11 | P-02a (equivalent) | Tested |
| S15_CONTINUOUS_LINE_BLOCKED | `RoadLawAndOvertakingE2ETest` (50 km/h) | P-11: correct at 50/70/90 km/h | Tested at 50 km/h |

---

## 8. Edge Cases and Rule-Conflict Scenarios

**Conflicts covered by existing tests (all passing in this run):**

| Test | Conflict | Asserted outcome |
|---|---|---|
| `DecisionContextTest.ceilingsCombineByMinimum` / `ceilingsCapTheBase` | Several ceilings vs base | Lowest ceiling binds; base capped |
| `DecisionContextTest.mandatoryStopIsCappedByALowerCeiling` | Mandatory stop vs lower ceiling | Stop's action, ceiling's speed |
| `DecisionContextTest.maneuverIsVetoedOnlyWhileBrakingToStop` | Lane maneuver vs mandatory stop | Vetoed only while braking |
| `DecisionContextTest.laneTieBreaks` | Lane proposals of equal priority | `RETURN_RIGHT` > change > keep |
| RT `conflictGreenLightAndPedestrianPedestrianWins` | Green light vs pedestrian | Pedestrian |
| RT `pedestrianAt35MetersPrevailsOverRedLightApproach`, `pedestrianBetween10And20MetersStopsTheVehicleBeforeARedLight` | Pedestrian vs red-light approach | Pedestrian's stop |
| RT `conflictSpeedLimitAndPotholePotholeWins` | 90 km/h sign vs pothole | ≤ 30 km/h |
| RT `conflictAccelerationAndCriticalTtcEmergencyWins` | Acceleration request vs persistent TTC | `EMERGENCY_BRAKE` |
| RT `conflictRainAndTtcRiskConservativeAction` | Rain vs rain + low TTC | `BRAKE` |
| RT `committedToProceedAtYellowStillStopsForAPedestrian` | Yellow commitment vs pedestrian | Pedestrian |
| `RedLightHoldTimerE2ETest` (green after 5 s vs pedestrian) | Green release vs crossing pedestrian | Stays stopped |
| `FarStopNearObstacleE2ETest` | Distant red light vs near obstacle | Avoids, then stops at the line |
| `SimulationEngineE2ETest.unsafeCenterTargetBlocksRightLanePotholeManeuverAndBrakes`, `actuatorRejectsLaneChangeIntoOccupiedTargetLane` | Lane decision vs unsafe target (actuator guard) | `REDUCE_SPEED` from "Actuator safety guard" |

**Edge cases covered:**

- an isolated TTC sample does not trigger the persistent rule;
- distance samples of different vehicles are not combined;
- an obstacle is no emergency once the car has physically left its lane;
- a zero radar distance does not loop the rules;
- the seed 120 km/h limit is not a target;
- the 50 km/h limit is never exceeded (dead band);
- a red light first seen past the stop line;
- a yellow light too close to stop;
- a light already behind the ego;
- the road edge counts as an occupied side;
- the vehicle stays at rest until START;
- a scenario without a lane starts in RIGHT;
- an overtaken vehicle is not re-tracked;
- a stalled WebSocket client.

**Edge cases and conflicts not covered:**

- a mandatory stop together with an emergency-brake ceiling (R-11, probe P-14);
- a closed barrier with no free side at more than 60 km/h (R-22, P-13);
- rain with no sign below the weather ceiling (R-23, P-16);
- speed near a green light or an open barrier (R-24, P-03c/d);
- decision oscillation at window edges (R-25);
- a lead vehicle pulling away (R-09);
- a pedestrian at more than 90 km/h (R-04);
- the continuous-line obstacle above 50 km/h (P-11 shows correct behaviour);
- a lane change interrupted by a newly unsafe target lane (A-12);
- the realtime clock (A-07).

---

## 9. Emergency Behaviour

**Tested:**
- persistent and immediate TTC (RT; `SimulationEngineE2ETest.ttcFlowProducesEmergencyBrakeAndRecoversRisk`);
- the 8.5 m/s² deceleration actually applied (`emergencyDecisionAppliesEmergencyDecelerationToWorldState`);
- the pedestrian emergency within 6 m, also for a crossing pedestrian outside the ego lane (RT);
- the HIGH-obstacle emergency by physical lane (RT);
- stop-sign emergency braking, and that it stays one continuous burst (RT; `stopSignApproachBrakesSteadilyInsteadOfPulsingEmergencyBrake`);
- red light first seen too close (`TrafficLightCycleE2ETest`);
- that red lights at a normal distance need no emergency (`RedTrafficLightStopE2ETest`).

**Observed in probes but not tested:**
- obstacle emergency through "Stop before obstacle ahead while still in its lane" at 70–90 km/h (P-11);
- emergency at a standing pedestrian approached at high speed (P-10).

**Not tested, with known problems:**
- emergency braking in the JavaFX client, which does not happen (A-07);
- a closed barrier with no free side, which has no emergency escalation (R-22);
- an emergency ceiling hidden by a mandatory stop (R-11).

---

## 10. Missing-Input Scenarios

**Tested:**
- no sign seen: the car does not chase 120 km/h;
- a single TTC reading;
- no blind-spot report yet (sides occupied);
- a light behind the ego is forgotten (`TrafficLightCycleE2ETest`);
- reset clears all perception.

**Partly tested:** stale-clearing timings. Some are tested only in the skipped Prolog suite: front vehicle after missed radar readings, light, stop sign, pending limit.

**Not tested:**
- a sensor that stops reporting while an object is still there;
- the 1.5 s expiry of a lane-change request.

Sensor failure, degraded mode and an explicit "unknown" state are not implemented, so they cannot be tested ([BUSINESS_DOMAIN.md §10](BUSINESS_DOMAIN.md#10-confirmed-limitations-and-missing-features)).

---

## 11. Regression Risks

Areas where a change is likely to alter behaviour without an existing test noticing:

1. **Two knowledge bases (A-14).** Without SWI-Prolog, every Prolog and equivalence test is **skipped, not failed**, so a Drools change not mirrored in Prolog (or vice versa) passes a normal `mvn test`. This analysis is itself an example: 255 tests skipped.
2. **Arbitration** (`DecisionContext`). Five unit tests cover the algorithm, but every outcome depends on it, including exact ties decided by firing order (R-11, R-13).
3. **Actuator target semantics.** Effects of `REDUCE_SPEED`/`MAINTAIN_SPEED` targets above the current speed (R-02, R-23) are not asserted anywhere; changing the actuator will change many behaviours at once.
4. **Timing coupling.** Rule windows depend on sensor rates: immediate TTC 200 ms vs radar 100 ms; obstacle emergency 300 ms vs camera 200 ms; stale clearing at 250/400 ms vs 100/200 ms rates. Changing a rate in `SimulationConfig` can silently disable a rule.
5. **Lane-change duration.** `DecisionActuatorTest.theRulesAssumeALaneChangeNoShorterThanTheActuatorPerforms` compares `SimulationConfig.laneChangeSeconds` (3 s) with `StoppingPolicy.LANE_CHANGE_SECONDS` (3 s). The physics does not use that value: it moves at `laneChangeMetersPerSecond` (1.2 m/s, about 2.9 s per lane). The guarantee holds today, but the test would not notice a slower lateral speed.
6. **Cross-file DRL imports** (A-10). Removing wildcard imports from `01-state-updates.drl` breaks other files; `kieBaseInitializes` would catch it.
7. **Weak assertions.** Several tests accept more than one action (e.g. `redLightTriggersStop`, `pedestrianTriggersBraking`). `RoadLawAndOvertakingE2ETest`'s "Pedestrian crossing: … then resumes" asserts braking only; resumption is covered by `PedestrianConflictE2ETest`.
8. **Frontend suite red.** With 5 failing unit tests, a new UI regression is easy to miss.

---

## 12. Identified Coverage Gaps

IDs are stable; statuses refer to `70082b4`.

| ID | Gap | Related finding | Status |
|---|---|---|---|
| T-01 | Pedestrian tests only up to 60 km/h | R-04, A-13 | Partly addressed (`PedestrianConflictE2ETest` asserts the stopping position); no test above 60 km/h |
| T-02 | No test combines bad weather with a low legal limit | R-01 | Open (behaviour correct in P-01) |
| T-03 | No test follows a slow vehicle or asserts a minimum gap | R-03 | Addressed (`BoxedInTrafficE2ETest` minimum gap, no stop-and-go) |
| T-04 | All overtaking tests start the slow vehicle within 50 m | R-05 | Addressed (starts at 80 and 100 m exist) |
| T-05 | No test of behaviour after passing a traffic light | R-08 | Addressed (`TrafficLightCycleE2ETest`) |
| T-06 | No test of a second radar pass | R-10 | Obsolete |
| T-07 | Markings tested only with the ego-relative reading | R-06 | Addressed (`LaneChangeLawE2ETest`, why-not tests) |
| T-08 | No test of the continuous-line obstacle stop above 50 km/h | R-07 | Open |
| T-09 | Scenarios never loaded by a test | R-20 | Open: S3, S6, S8, S9, S11 (in executed tests) |
| T-10 | Rules without tests | — | Open: 8 never fired (§6) |
| T-11 | The rule harness differs from the production loop | A-05 | Partly addressed (cycle order now as in production; no cruise fact by default, no actuator) |
| T-12 | Weak or permissive assertions | — | Open (§11 item 7) |
| T-13 | No collision assertions; no collision model | A-13 | Partly addressed (some tests measure overlap themselves) |
| T-14 | No test of the realtime-clock configuration used by the JavaFX client | A-07 | Open |
| T-15 | Playwright not portable (macOS path) and writes into the repository root | — | Open |
| T-16 | No API-layer tests; no coverage tool | — | Partly addressed (`RealtimeSnapshotPublisherTest`); still no controller tests or coverage tool |
| T-17 | No test of stale-perception timing | R-19 | Partly addressed (light; others only in the skipped Prolog suite) |
| T-18 | Prolog and equivalence suites are skipped, not failed, without SWI-Prolog | A-14 | New |
| T-19 | Frontend unit suite has 5 failing tests (§4) | — | New |
| T-20 | New findings without tests: R-11, R-22, R-23, R-24, R-25 | R-11, R-22–R-25 | New |

---

## 13. Proposed Additional Tests (Not Implemented, Not Executed)

The tests below describe the **expected correct behaviour**. Where a probe explored the same situation, the last column states what was observed at `70082b4`.

| ID | Target rule / finding | Inputs | Expected behaviour | Observation at `70082b4` |
|---|---|---|---|---|
| PT-01 | Legal limit vs weather (R-01) | Ego 30 km/h, 30 km/h sign 60 m ahead, rain | Speed ≤ 30.1 km/h once the sign is passed | P-01: 30.0 km/h (would pass) |
| PT-02 | Pedestrian at speed (R-04) | Standing pedestrian 110 m ahead; initial 90 and 120 km/h | Car stops ≥ 2 m before the pedestrian | P-10: 90 → stopped 3.1 m before; 120 → reached at 74.5 km/h |
| PT-03 | Following distance (R-03) | Lead vehicle 18 km/h 120 m ahead; no way to overtake; 60 s | Gap never below 10 m | P-02d: minimum 20.3 m (would pass) |
| PT-04 | Overtaking latch (R-05) | Lead vehicle 18 km/h 120 m ahead; CENTER free | Overtake within 15 s | P-02b: in CENTER at 9.9 s (would pass) |
| PT-05 | Light behind the car (R-08) | 90 km/h sign, green light 300 m ahead | 15 s after the light, speed ≥ 88 km/h | P-03: 90 km/h (would pass) |
| PT-06 | Radar (R-10) | — | — | Obsolete (feature removed) |
| PT-07 | Markings (R-06) | Ego RIGHT; LEFT\|CENTER continuous, CENTER\|RIGHT dashed; obstacle in RIGHT; CENTER free | Avoidance into CENTER | Covered by `LaneChangeLawE2ETest` (passing) |
| PT-08 | Continuous-line escalation (R-07) | S15 at 70 and 90 km/h | Stops before the obstacle | P-11: 70 → 10.4 m short; 90 → reached at 2.3 km/h (physical limit) |
| PT-09 | Trend rule (R-09) | Lead vehicle 20 m ahead doing 90 km/h; ego 60 km/h | No `REDUCE_SPEED` from the trend rule | P-07: fired (would fail) |
| PT-10 | Speed semantics (R-02) | Every tick of every E2E run | Under `REDUCE_SPEED`, acceleration ≤ 0 unless the decision's speed is a base proposal | P-02c: +0.8 m/s² (would fail) |
| PT-11 | Scenario smoke tests (R-20, T-09) | Each of the 15 scenarios, 30 s | The rule family named by the scenario fires at least once | P-06: all checked scenarios would pass |
| PT-12 | Event memory (R-19) | — | — | Obsolete |
| PT-13 | Realtime clock (A-07) | S4 with `pseudoClock=false` | The car moves off after the stop | P-04b: never moves off (would fail) |
| PT-14 | Braking strength (R-11) | No free side; MEDIUM obstacle 26 m ahead at 50 km/h; standing pedestrian 32 m ahead | `EMERGENCY_BRAKE`, stop before the obstacle | P-14: reached at 27.8 km/h (would fail) |
| PT-15 | Never-fired rules (T-10) | Dedicated cases: closed barrier from RIGHT, from CENTER (left free), from LEFT; open barrier at 30 m; obstacle in LEFT; stale barrier; pending sign no longer seen | The behaviour in [BUSINESS_RULES.md](BUSINESS_RULES.md) | Not observed (P-06 shows the RIGHT case in S6) |
| PT-16 | API layer (T-16) | MockMvc: element distance 0 or 1001; unknown id; lane change `UP`; backend switch to an unknown type and to Prolog without `swipl` | HTTP 400, 404, 400, 400, 503 | Not observed |
| PT-17 | Lane change interrupted (A-12) | Lane change started; a fast vehicle enters the target lane from behind | Maneuver aborted or no entry into an unsafe gap | Not observed |
| PT-18 | Closed barrier, no free side (R-22) | CENTER\|RIGHT continuous; closed barrier 150 m ahead in RIGHT; 70 and 90 km/h | Stops before the barrier | P-13: reached at 29.7 and 64.8 km/h (would fail) |
| PT-19 | Weather ceiling (R-23) | Rain, no sign, cruise 30 km/h | Speed never above 30 km/h | P-16: 50.6 km/h (would fail) |
| PT-20 | Green light / open barrier (R-24) | Cruise 70, green light ahead | Speed at the light ≥ 69 km/h | P-03c: 50 km/h (would fail) |
| PT-21 | Oscillation (R-25) | Boxed in behind an 18 km/h vehicle, 60 s | At most a few decision changes per 10 s once following | P-02d: 114 in 30 s (would fail) |
| PT-22 | Equivalence in CI (A-14, T-18) | Run `mvn test` where SWI-Prolog is installed | 0 skipped Prolog tests, all passing | Not executed here |
| PT-23 | Frontend unit suite (T-19) | Fix the state stub and compare the keyframe name by suffix | 57/57 passing | 52/57 |
