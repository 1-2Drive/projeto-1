# 3D Road and Driving Visualization

Documents what pull request #70 (`feat/3d-road-driving-visualization` into `feat/drools-realtime-driving-expert-system`) actually delivers. It covers the Angular front end under `frontend/src/app/` and the small back-end changes listed in section 2.3. It is not general front-end documentation.

---

## 1. Overview

The flat, top-down road view of the simulation page was replaced by a **chase-camera, perspective driving scene**: the ego vehicle is seen from behind and above, the road narrows towards a horizon, and objects shrink with distance.

The change is a new *projection* of the existing simulation state. It does not change what the simulation computes:

- **The back end stays authoritative.** Speeds, lanes, decisions, sensor readings and object states come from the Spring Boot simulation and the inference engine (Drools or Prolog) exactly as before.
- **The world / road-map matrix stays authoritative.** Every object is drawn at the longitudinal position and lane the back end reports. Lanes are never inferred from pixels.
- **The front end only draws.** Nothing in the 3D view writes back into the simulation. It reads one snapshot (about every 100 ms) and draws it.

The same page also took over the dashboard layout of `codex/ui-layout-improvements` (section 8); the Speed Advisor (section 7), the Scenario Toolbox and the control bar were reworked as part of it.

---

## 2. Architecture and data flow

### 2.1 Rendering flow

```
Backend simulation state (WorldState, ego vehicle, sensors, decision)
  -> REST + stream, ApiSimulationSnapshot            (core/models/api.models.ts)
  -> SimulationPageComponent                          (simulation/simulation-page/)
  -> RoadCanvasComponent [snapshot]                   (simulation/road-canvas/road-canvas.component.ts)
       kinematicsOf(snapshot)                         road-kinematics.ts   positions, lateral offset, heading
       blendKinematics(from, to, k)                   road-kinematics.ts   slide between two snapshots
       createCamera(viewport, egoLateral)             road-projection.ts   pinhole camera from the canvas size
       buildRoadSurface / buildMarkings               road-scene.ts        asphalt, shoulders, hills, trees, lane marks
       buildSensors                                   road-scene.ts        camera / radar / blind-spot areas
       buildItems                                     road-scene.ts        every world object
       buildEgo                                       road-scene.ts        the ego vehicle
  -> screen coordinates (SVG paths and CSS transforms)
  -> road-canvas.component.html / .scss               one SVG, painted far to near
```

| File (`frontend/src/app/simulation/road-canvas/`) | Role |
|---|---|
| `road-canvas.component.{ts,html,scss}` | Component: turns the snapshot into signals (`computed`), runs the display-rate blend, owns the HUD, rain overlay and template. |
| `road-kinematics.ts` | The few continuously changing numbers (ego position, lateral offset, heading, object positions) and the blend between snapshots. |
| `road-projection.ts` | `createCamera`, `project`, ground polygons: the only place where metres become pixels. |
| `road-scene.ts` | Builds the views (`buildRoadSurface`, `buildMarkings`, `buildSensors`, `buildItems`, `buildEgo`). |
| `road-solids.ts` | Small 3D solid layer (boxes, frustums, lofts, skinned bodies), back-face culling, shading, `renderSolid`, `renderCar`, `TRAFFIC_CAR`. |
| `road-ev-model.ts` | The ego vehicle model, `EGO_CAR`. |

The blend is cosmetic: the newest snapshot is always the target, the view never extrapolates beyond it, and jumps larger than 60 m (reset, scenario load) are shown at once.

### 2.2 What did NOT change

- Drools rules (`.drl`) and the Drools knowledge base: not changed.
- Prolog rules and interpreter (`src/main/prolog/`): not changed.
- Road-map matrix semantics (cells, 5 m blocks, one object type per cell): not changed.
- Scenario semantics (`scenarios.json` and the predefined scenarios): not changed.
- Core driving logic (speed, lane, overtaking, braking and arbitration rules): not changed.

### 2.3 Back-end changes in this PR

The PR contains a small, deliberate set of back-end edits. Only the first one changes simulation behaviour.

| Change | File | Purpose |
|---|---|---|
| A STOP sign governs the whole road, in any lane. | `simulation/WorldState.java` (`nearestStopSign`) | See section 6. |
| Road-wide elements (speed limit, STOP sign, traffic light, crosswalk) need no lane in a request; other elements still require one. | `api/service/ApiRequestMapper.java` | The toolbox asks only for a distance. |
| The stored session records the recorded facts of each rule; the explanation "HOW" path lists the recorded STOP signs and traffic lights. | `history/SessionSnapshot.java`, `api/service/RecordedCycle.java`, `explanation/DecisionFlowchart.java` | Historical explanations (section 9). |

Back-end tests for these changes: `StopSignAllLanesE2ETest` (new), `ApiRequestMapperTest`, `SessionExplanationServiceTest`, and adjusted `CameraSensorTest`, `SimulationEngineE2ETest`, `StopSignInLaneBeingEnteredE2ETest`, `InvariantSweepProbeTest`.

---

## 3. The 3D road

### 3.1 Perspective projection

`road-projection.ts` is a pinhole camera. A point is described by the numbers the back end already uses (lateral X in metres from the centre line, distance `ahead` in metres, height Y above the road):

```
z = ahead + camera.back          x_screen = cx + focal * (X - camera.x) / z
                                 y_screen = horizonY + focal * (eye - Y) / z
                                 scale    = focal / z   (pixels per metre)
```

- The camera sits `CAMERA_BACK_METRES = 16` m behind the ego and 3 to 8.5 m above the road (`eye` is derived from the canvas shape, so the road keeps the same share of the width on 16:9 and on wide, short canvases).
- Because every point goes through the same equation, straight lanes stay straight and converge on the vanishing point (`HORIZON_FRACTION = 0.2` of the canvas height); the ego touches the road at 72% of the height.
- The view draws up to `VIEW_AHEAD_METRES = 220` m ahead and clips below a near plane.
- The camera follows half of the ego's lateral offset (`CAMERA_FOLLOW = 0.5`), so a lane change slides the whole scene.

### 3.2 Lanes

Three lanes of 3.5 m (`LANE_CENTRE_METRES`: LEFT -3.5, CENTER 0, RIGHT +3.5, mirroring `Lane.centreMeters()` of the back end), asphalt half-width 5.25 m, hard shoulders to 7.25 m. `buildMarkings` draws dashed or solid dividers from the back end's `roadMarks.leftMarking / rightMarking`, centre arrows, and distance ticks (every 40 m, labelled up to 160 m).

### 3.3 Lane changes

The ego's lateral offset and heading come from the back end (`egoVehicle.lateralPositionMeters`, `headingDegrees`). The car moves laterally and yaws (heading x 1.5, clamped to +-25 degrees), the front wheels steer, and the camera follows half of the offset. Indicators and the manoeuvre state come from the snapshot (`indicator`, `maneuver.state`).

### 3.4 Distance scaling

Everything is scaled by `focal / z`. Billboards (signs, pedestrians, radar) have a floor of 7 px per metre so they stay legible far away. Objects fade with distance (`fogOf`). Objects behind the ego more than 36 m, or beyond 220 m, are not drawn.

### 3.5 Environment

`buildRoadSurface` draws the asphalt, shoulders, edge lines and two bands of hills at the horizon. `buildMarkings` adds roadside posts, trees, bands and arrows at fixed world spacing, so they scroll with the ego position. A HUD over the road shows position, speed, acceleration, driving state, lane, indicator, steering and the radar range.

### 3.6 Sensor field of view

`buildSensors` draws, from the back end's `sensorRanges` and sensor telemetry:

- the camera field of view: a cone from the nose widening to the asphalt width, to the effective range (shortened by low visibility, with the lost part marked and the label e.g. `CAMERA 84 m (120 x 70%)`);
- the front radar beam (`FRONT RADAR FOV (n m)`);
- the left and right blind-spot pads, with the occupied state.

Objects detected by the camera or the radar get a coloured halo (blue and green) and a distance tag.

---

## 4. The ego vehicle

`road-ev-model.ts` exports `EGO_CAR`, an original modern electric fastback sedan (no make is copied, no downloaded asset is used).

- **Smooth body.** One skinned surface (`skin()` in `road-solids.ts`) through about 45 cross-sections of 22 vertices each. Width, floor, deck and roof come from smooth profile curves (cubic Hermite), so the surface flows instead of being assembled from boxes. Each face gets its own diffuse lighting and a specular highlight; smooth faces are outlined in their own colour, so the facets blend.
- **Roof and glass.** A fastback roofline falls into a short duck-tail. A black glass roof runs into the rear window; its border stays body paint.
- **Tail.** A slim full-width light bar with a centre piece, bumper band, number plate and diffuser.
- **Wheels.** 16-sided tyres with a flat aero cover (rim) and a hub, in wheel arches cut into the flanks. Wheels stay under the body at every yaw.
- **Indicators.** Rear lamp, flank repeater and door-mirror lamp on each side, in stable lamp slots (0, 1, 2). They flash from the snapshot's indicator state (`egoVehicle.indicatorState` or the manoeuvre's indicator flags); hazards flash both sides.
- **Brake lights.** The tail bar and a glow light up when the car brakes (driving state `BRAKING`, a deceleration beyond 0.8 m/s2, or the manoeuvre's braking flag).
- **Shadow.** A soft 28-sided shadow under the car.
- **Steering and yaw.** `buildEgo` yaws the whole car with the back end's heading and steers the front wheels (+-12 degrees plus 30% of the yaw).
- **Perspective.** It is rendered by the same `renderCar` and camera as the traffic, so it scales and is placed like every other object (rear view, lane position from the real lateral offset).
- **Label.** The visible "EGO" label was removed. `data-testid="road-ego"` and `aria-label="Ego vehicle"` stay for accessibility and tests.

Other vehicles use a simpler, steel-toned model (`TRAFFIC_CAR`), so the ego is clearly distinct.

---

## 5. Other road objects

`kindOf()` maps the back end's object type to one of ten kinds; `buildItems()` places each from its reported lane and distance.

| Object | Rendering |
|---|---|
| Other vehicles | `TRAFFIC_CAR` solid, shadow, three body tones by id; halo and tag when detected. |
| Pedestrians | Upright figure with swinging legs, walking along the lateral position the back end reports (clamped to the sidewalks at +-5.5 m); drawn on its crosswalk. |
| Crosswalks | Painted bars across the three lanes (flat). |
| Potholes | Dark concentric rings on the asphalt in the pothole's lane (flat). |
| Obstacles | Orange crate with hazard stripes (3D solid). |
| Barriers | Red and white arm on posts; open or closed from the reported state. |
| Traffic lights | One gantry across the road with a signal head per lane; the lit lamp (red, yellow, green) and its glow follow the back end's state. |
| Speed-limit signs | Limit painted in all three lanes at the same distance, plus a roadside sign on the right shoulder. |
| STOP sign | Roadside sign plus a stop line across the road (section 8). |
| Radar | The roadside speed-enforcement radar: a post on the right shoulder (fixed infrastructure; not to be confused with the ego's front radar field of view). |
| Rain | A rain overlay (two layers of drops), a `RAIN · VIS n% · GRIP n%` chip, wet asphalt and a tail-light reflection under the car, from `environment.weatherCondition` or the rain sensor. |

Visual distinction:

- **Actors** (vehicles, pedestrians): solid, lit, moving models with shadows.
- **Hazards** (potholes, obstacles, barriers): ground or low objects in warning colours (dark rings, orange, red and white).
- **Infrastructure** (signs, traffic lights, crosswalks, radar): fixed objects on the verge, on a gantry, or painted on the road.

Detected objects get a halo; undetected objects get a distance plate only within 70 m, so a busy road stays readable.

---

## 6. STOP sign

The STOP sign is road-wide infrastructure, like a speed limit or a traffic light.

- **Rendering.** An upright sign on a pole on the right verge, outside the lanes (`buildItems`, case `stopSign`, in `road-scene.ts`), with one stop line across all three lanes. It is drawn once, whatever cell the back end stores it in.
- **Placement UI.** The toolbox (`scenario/toolbox/`) asks only for a distance. There is no lane selector for a STOP sign (the same holds for speed limits and traffic lights).
- **One object, all lanes.** The request needs no lane (`ApiRequestMapper.laneOf`); the road map stores it in its centre cell. The cell only fixes where along the road the sign stands.
- **Behaviour.** `WorldState.nearestStopSign` previously filtered the sign by the ego's current lane, so a sign stored in another lane was ignored. It now returns the nearest active STOP sign ahead whatever lane the vehicle drives in. The Drools and Prolog rules are untouched; they receive the sign through the same sensor and working-memory path as before.
- **Evidence.** `StopSignAllLanesE2ETest` stops from the LEFT, CENTER and RIGHT lanes on both rule engines; the Playwright test `one stop sign, placed with no lane, stops the vehicle whether it drives in the left, centre or right lane` does the same through the UI.

---

## 7. Speed Advisor

`telemetry/speed-advisor/`.

- **Why the card was stretched.** The page grid forced the card to the height of the Road Map / Scenario Toolbox tabs beside it (335 px at 1440 wide, 439 px at 1920) for about 230 px of content. The speed number was pinned to the bottom of its stretched cell, so it drifted below the gauge, and the legal-limit and recommended markers were rotated around the middle of that stretched box.
- **Fix.** The card now sizes itself to its content: no `height: 100%` on the host, `align-content: start` on the card, and `.telemetry-pair > app-speed-advisor { align-self: start }` in the page grid (`simulation-page.component.scss`).
- **Gauge and speed.** The dial and the speed number share one grid cell, so the number always sits inside the arc. The legal-limit and recommended markers are drawn inside the SVG on the arc's own circle (legal limit outside the arc, recommended inside) through `pinX` / `pinY` helpers.
- **Legend.** Legal limit, recommended and delta are three aligned columns, label above value, never overlapping or wrapping; the action badge (e.g. `EMERGENCY_BRAKE`, `CHANGE_LANE_RIGHT`) is not clipped.
- **Responsive.** The dial grows with the card (up to 22 rem; the readout font grows in a `@container` query from 19 rem). Measured card height: 281 px at 1920 x 1080, 247 px at 1600 x 900, 237 px at 1440 x 900, 1366 x 768 and 1280 x 720, and constant across driving states.

---

## 8. UI and layout integration

### 8.1 Integration method

`codex/ui-layout-improvements` was merged with a real `git merge` (commit `6fe3105`, two parents) into the 3D branch and its conflicts were **resolved by hand**, not accepted wholesale. The 3D road renderer was kept; the layout of the dashboard around it was taken, and what the layout branch had dropped but still had users was kept (demo controls and progress, the Situation panel, the telemetry HUD on the road, lane placement buttons, the session history button). Later commits adjusted the result (`b638bc2`, `1982fdf`).

The layout branch also deleted 13 stray `screenshot_*.png` files from the repository root; they are gone from this PR as well.

### 8.2 What the page looks like

- **Header controls** (`simulation-page.component.html`): START / PAUSE / STOP / RESET, then the Rule engine selector, then the automated demo controls. STOP saves the trip and opens the explanation dialog.
- **Rule engine selector.** A button group (Drools / Prolog) in the control bar; `aria-pressed` marks the active engine; switching reloads the scenario with an empty working memory.
- **Status row.** Backend, engine (`ACTIVE` / `ERROR`), Stream (`LIVE`), simulation status and clock.
- **Flow stages.** `app-flow-stage` below the controls shows the active pipeline stage.
- **Layout.** The road fills the large upper-left cell. The evidence tabs (Inference Timeline, Working Memory, Explanation, World State, Decision, Situation) sit on the right. Below: Speed Advisor and Sensors side by side, and the Road Map / Scenario Toolbox tabs.
- **Scenario Toolbox.** One row of sections (Road signs, Traffic control, Actors, Road markings, Hazards, Environment), a one-row list of prebuilt scenarios, no clipped labels. Placement forms ask only the fields an element needs (no lane for road-wide elements). Container queries (`@container toolbox`) adapt it to its width.
- **Responsive breakpoints** (`simulation-page.component.scss`): from 1101 px wide a two-column dashboard (road and a `--right-rail`); up to 1600 px the right rail is narrower; up to 1100 px the page stacks in one column; below 820 px high the page scrolls instead of squeezing the road map and telemetry.

---

## 9. WHY / WHY-NOT / HOW (regression evidence)

The historical explanation feature was audited to make sure the UI changes did not break it. This is regression evidence; the feature itself is documented elsewhere.

- STOP persists the session; the explanation dialog (`trip-questions-dialog`) then opens and answers questions about that trip. A dialog closed before its trip loaded was fixed.
- WHY, WHY NOT and HOW are rebuilt from the recorded snapshots of the trip and selected by timestamp (the decision in force at that time).
- The evidence is dynamic. The canned "proposal conditions" text was removed; the recorded facts of each rule are shown, and the HOW path shows the world as recorded in that cycle (STOP signs, traffic lights).
- No answer is scenario-specific or hard-coded. Back-end test `differentTripsGiveDifferentAnswersBuiltFromTheirOwnRecordedEvidence` and the Playwright test `WHY / WHY NOT / HOW are rebuilt from each trip` prove that a stop sign, a pothole and a red light never share an answer.

---

## 10. Responsiveness

Verified at 1920 x 1080, 1600 x 900, 1440 x 900, 1366 x 768 and 1280 x 720:

- **Road.** Fills its cell without overlapping the lower workspace; no horizontal overflow; the car keeps its proportions.
- **Scenario Toolbox.** No clipped labels; no inner scrolling needed.
- **Speed Advisor.** Constant height; no overlap; gauge inside the card.
- **Dialogs.** The explanation dialog stays inside the viewport (1248 x 677 px at 1280 x 720).
- **Shorter viewports.** On 1366 x 768 and 1280 x 720 (below 820 px high) the page scrolls vertically; this is intended.

---

## 11. Testing

Final run on the PR head (2026-10-22), before merge:

| Suite | Command | Result |
|---|---|---|
| Backend (JUnit 5) | `mvn -o test` | 598 run, 0 failures, 0 errors, 2 skipped (the opt-in `InvariantSweepProbeTest`) |
| Frontend (Karma / Jasmine) | `npm test -- --watch=false --browsers=ChromeHeadless` | 169 of 169 passed |
| Browser E2E (Playwright) | `npx playwright test` | 24 of 24 passed |
| Production build | `npm run build` | Passes (initial bundle 576.61 kB; see section 12) |

Live check against the running application (Drools engine, 1440 x 900 unless noted): backend `CONNECTED`, `Drools ACTIVE`, stream `LIVE`; the 3D road loads and the ego renders (no label); scenarios S4 (STOP sign: stops, then proceeds after the hold), S5 (pothole), S3 (red traffic light and gantry), S7 (pedestrian on the crosswalk), S8 (radar target, emergency braking, indicators), S14 (overtaking with indicators and lane change), S10 (rain overlay and chip), plus a STOP sign placed from the toolbox while driving in the LEFT lane; the STOP button saved the trip and the WHY, WHY NOT and HOW views answered from the recorded stop (rule `Stop sign requires full stop near line`, HOW node `V07` = YES). No browser console errors and no back-end errors (start-up Drools warnings only).

Regression areas covered: 3D ego (`road-solids.spec.ts`, `road-canvas.component.spec.ts`), road projection (`road-projection.spec.ts`, `road-kinematics.spec.ts`), STOP in all lanes (back-end `StopSignAllLanesE2ETest`, e2e stops from every lane), road objects, indicators and braking, responsive UI (`simulation-page.component.spec.ts`, e2e viewport tests), WHY / WHY-NOT / HOW (back-end `SessionExplanationServiceTest`, e2e per-trip explanations) and the end-to-end scenarios (`frontend/e2e/simulation.spec.ts`). Counts per file are in [TESTING.md](TESTING.md).

---

## 12. Known non-blocking notes

- **Bundle budget.** The production build warns that the initial bundle (576.61 kB) exceeds the 512 kB budget. It is a warning, as it was on the base branch; the build passes.
- **Scrolling on shorter screens.** Below about 820 px of height (1366 x 768, 1280 x 720) the page scrolls vertically instead of squeezing the road map and telemetry. This is intended.
- **Blank space under the Speed Advisor on tall screens.** The card is content-sized and top-aligned, so the grid cell under it stays empty (roughly 100 px at 1440 x 900).
- **Roof edge.** At 3x zoom the left roof edge of the ego shows a faint sawtooth; it is not visible at normal size.
