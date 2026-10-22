# Business Domain

**Audience:** readers who are not necessarily programmers — students, presenters, evaluators and domain experts — who want to understand what the system does, which situations it handles, how it decides, and where it falls short. Developers will also find it a useful introduction.

**Related documents:** [BUSINESS_RULES.md](BUSINESS_RULES.md) gives the rule-by-rule technical catalogue; [GLOSSARY.md](GLOSSARY.md) defines the terms used here.

---

## 1. Overview

The project is an **expert system that drives a simulated car**. An expert system is a program in which the knowledge of a human expert is written down as explicit "if … then …" rules, so that every decision can be traced back to the rule that produced it. Here the expert knowledge is about safe driving:

- speed limits;
- keeping a safe distance and overtaking correctly;
- stopping for pedestrians, red lights and stop signs;
- avoiding potholes, obstacles and closed barriers;
- keeping to the right;
- driving more carefully in the rain.

The car, the road, the other road users and the weather are all **simulated**. The car "sees" the world through simulated sensors: a camera, a radar, side sensors, a weather sensor and its speedometer. Ten times per second:

1. the rules look at what the sensors report;
2. they choose what to do with the **speed** ("reduce to 50 km/h", "emergency brake" …) and with the **lane** ("change to the left lane", "keep the lane" …);
3. the simulated car carries this out.

A web page shows the road, the decisions and the reasoning behind them, including why the other options were not chosen.

The same knowledge exists in two versions, which can be swapped while the program runs:

- the **Drools** rule engine (Java), used by default;
- a **Prolog** rule engine (SWI-Prolog), written by the project team.

This documentation was requested on the assumption of a Prolog system. In fact both engines exist, and Drools is the default.

The project was developed as academic work for the Knowledge Engineering course of the Master's in Artificial Intelligence Engineering at ISEP (Porto), according to the metadata of the preliminary report in `Relatorio_Previo_ENGCIA/`. The README states that it is inspired by the perception–decision loop of driver-assistance systems and does not replicate any commercial product. It is a teaching prototype, not software for a real vehicle. Several rules cite articles of the Portuguese Highway Code (*Código da Estrada*, "CE").

---

## 2. The Problem

A vehicle that drives itself, or assists a driver, must keep answering the question *"what should I do now?"* while the situation changes: a speed-limit sign appears, the car ahead slows down, a pedestrian steps onto the road, it starts raining. Three difficulties make this hard:

1. **Several things can matter at once.** A green light says "go", but a pedestrian on the crossing says "stop"; a slow car ahead invites an overtake while a red light ahead forbids it. The system needs a clear way to combine these concerns.
2. **Some notions are vague.** "Poor visibility" or "slippery road" have no sharp boundary.
3. **Decisions must be explainable.** It is not enough that the car stops; one must see *why* it stopped (which observation, which rule), and *why not* something else (why it did not change lane, why another rule lost).

A rule-based expert system addresses all three:

- each rule captures one piece of driving knowledge;
- rules carry an explicit priority;
- vague notions are handled with fuzzy logic;
- the system records every proposal, which one won, and why the others lost.

---

## 3. Objectives and Functional Scope

### 3.1 Stated objectives

The README states the following goals:

1. Demonstrate a continuous loop: the world changes → sensors perceive → rules decide → the car reacts → the world changes again.
2. Keep all driving policy in the knowledge base, not in the user interface. Verified: the web interface contains no driving logic.
3. Make the reasoning visible: a timeline of events, the facts the system currently believes, the rules that fired, the evidence, and the "why not".
4. Offer two interchangeable rule engines (Drools and SWI-Prolog) that decide alike.

The draft preliminary report (Portuguese) lists five case studies:

- speed adaptation in rain;
- frontal obstacle and collision risk;
- keeping a safe distance;
- unintentional lane departure;
- a pedestrian in the vehicle's path.

It also plans "why" and "why-not" explanations, a hierarchy of actions with conflict resolution, and the handling of uncertainty. Most chapters of that report are still empty headings; one chapter (sensors) contains real content. Section 10 compares these intentions with what is implemented.

### 3.2 What the system handles today (verified in the code)

| Topic | Implemented behaviour |
|---|---|
| Speed limits | Reads speed-limit signs ahead; slows down in time to be at the new limit when it reaches the sign; the limit applies from the sign onwards; builds up to a higher limit; returns to the scenario's cruising speed when no sign applies |
| Vehicle ahead | Keeps a safe gap (enough room to brake to the other vehicle's speed, with at least 12 m left); follows a slower vehicle at its speed; brakes when too close |
| Overtaking | Overtakes slower vehicles on the left when the left lane is clear and clearer than its own; never passes a slower vehicle in the lane to its left, or two lanes to its left, on its right; does not overtake just before a pedestrian or a red/yellow light; returns to the right one lane at a time; gives up an impossible overtake when every lane is slow |
| Collision risk | Emergency braking when the time-to-collision (TTC) with the vehicle ahead is critical |
| Pedestrians | Starts braking as soon as a pedestrian in its path, or one crossing the road, is seen; stops before it; waits while the crossing is occupied; emergency-brakes when comfortable braking can no longer stop in time, and at very short distance |
| Stop signs | Brakes progressively, stops at the stop line, waits 1.2 s, then proceeds |
| Traffic lights | Decides once at yellow whether it can still stop comfortably; stops for red at a stop line before the light; waits; moves off on green. The lights cycle by themselves |
| Potholes and obstacles | Changes lane around them when a side lane is proved safe and there is still room to leave the lane before the hazard; otherwise slows down to cross a pothole (braking hard for a severe one if needed) or stops 10 m before an obstacle. Only a hazard in the car's own path counts |
| Closed barriers | Changes lane around a closed barrier when possible, otherwise brakes in time to stop 10 m before it (hard if needed); passes an open barrier |
| Weather | Lowers the speed in rain, low visibility or low road grip, using fuzzy logic |
| Lane changes | Every lane change (requested or decided by the rules) needs: no other maneuver running, no continuous line to cross, the side clear, and room to follow the vehicle ahead in the new lane |
| Keep right | Returns to the rightmost lane when it is safe (Art. 13 CE) |
| Moving off | Restarts a stopped car once nothing holds it any more |
| Explanation | Timeline of perceptions, rules, decisions and maneuvers; for each decision, the reason, the rule, the evidence; every proposal with its outcome ("won", "lost to …", "vetoed by …"); and the answer to "why not change lane?" for each side |
| Two engines | The operator can switch between Drools and Prolog; if the engine fails, the simulation pauses with the error instead of driving on |

---

## 4. Main Use Cases

The main user is the **operator** of the web interface (a student, a presenter or an evaluator). A secondary user is the **developer** who adds rules or scenarios.

| ID | Use case | What the operator does | What the system does |
|---|---|---|---|
| UC-1 | Run a predefined scenario | Chooses one of 14 scenarios in the Scenario Library and presses START | Builds the road, places objects, starts the car at the scenario's speed, and drives it while deciding ten times per second |
| UC-2 | Change the world while driving | Adds a sign, light, stop sign, crosswalk, pothole, obstacle, barrier, vehicle or pedestrian from the toolbox, or paints the road map | Inserts the object at the next 0.1 s step; the car notices it only when a sensor can see it, then reacts. A vehicle added this way needs a speed of at least 6 km/h (`ApiRequestMapper.MIN_VEHICLE_SPEED_KMH`); scenarios and tests may still place slower or stopped vehicles |
| UC-3 | Change the weather | Selects rain, low visibility or low grip | The weather sensor reports the change; the fuzzy weather rules lower the speed; the camera sees less far |
| UC-4 | Request a lane change | Sends a lane-change request (REST API, a scripted scenario action, or the legacy desktop client; the web page no longer has buttons for it) | The rules check that the change is safe; the car changes lane only if it is |
| UC-5 | Inspect the reasoning | Opens the decision panel, timeline, working-memory and explanation views | Shows the speed and lane decisions, the rules that produced them, the evidence, every proposal with its outcome, the "why not change lane" answer, and the facts the system believes |
| UC-6 | Control the simulation | Uses START, PAUSE, RESUME, STOP and RESET | Starts, freezes or continues time; RESET returns to the empty-road scenario |
| UC-7 | Switch the rule engine | Chooses "Drools" or "Prolog" in the control bar | Reloads the current scenario with the other engine (empty memory); if Prolog is not installed, keeps Drools and shows the error |
| UC-8 | Run the presentation demo | Presses RUN DEMO (scenario `S12_PRESENTATION_DEMO`) | Plays a scripted 90-second sequence: speed sign, rain, slower vehicle, pedestrian, clear weather |
| UC-9 | Extend the knowledge (developer) | Edits the rule files (Drools and, to keep them equivalent, Prolog) or the scenario file | New rules and scenarios are loaded at the next start |

### 4.1 Predefined scenarios

Behaviour marked "verified" was observed by running the scenario during the previous documentation analysis, or is checked by an existing automated test ([TESTING.md §7](TESTING.md#7-scenario-coverage)). All runs used the Drools engine.

| ID | Name | What it is meant to show | Observation |
|---|---|---|---|
| S1 | Empty safe road | Cruising at 60 km/h; the default scenario after RESET | Used by many tests |
| S2 | 90 to 50 speed limit | Slowing from 90 to 50 km/h for a 50 sign | Verified: sign read at about 118 m; 50 km/h reached 4.3 m before the sign |
| S3 | Red traffic light | Stopping before a red light, moving off on green | Verified: stops 19.2 m before the light (just before the stop line); the light turns green 5 s later; the car moves off |
| S4 | Stop sign | Full stop, short wait, proceed | Verified by test |
| S5 | Pothole | Avoiding a pothole by changing lane | Verified by test |
| S6 | Closed barrier | Avoiding a closed barrier | Verified: the car moves to the centre lane, passes, and returns right |
| S7 | Pedestrian crossing | Braking for a crossing pedestrian | Verified (test and run) |
| S8 | Critical TTC | Emergency braking behind a much slower vehicle | Verified: emergency braking, then the car overtakes |
| S9 | Lane change blocked | A requested lane change refused because the lane is occupied | Verified: the request is refused |
| S10 | Rain low grip | Driving slower in rain | Verified: from 85 to about 51 km/h |
| S11 | Complex sequential scenario | Sign, rain, slower vehicle, pedestrian in sequence | Verified: every phase handled |
| S12_PRESENTATION_DEMO | Presentation demo | The classroom demonstration | Verified by test (Drools) |
| S13_MATRIX_COURSE | Matrix obstacle course | A slalom through obstacles that repeats each lap | Verified by test |
| S14_SLOW_VEHICLE_OVERTAKE | Slow vehicle overtake | Overtaking on the left and returning right | Verified (test and run) |
| S15_CONTINUOUS_LINE_BLOCKED | Continuous line prohibits avoidance | Stopping before an obstacle because a continuous line forbids moving to the centre lane | **Set aside:** commented out in `scenarios.json`, so it is no longer offered. Its situation is covered by `RoadLawAndOvertakingE2ETest`. When it ran, it stopped 10.4 m before the obstacle from 50 and 70 km/h |

The scenarios were renumbered after an earlier version of the project; the speed-radar scenario was removed together with the speed-radar feature.

---

## 5. Domain Concepts

Short definitions are given here; [GLOSSARY.md](GLOSSARY.md) has the full list.

| Concept | Meaning in this system |
|---|---|
| **Ego vehicle** | The car controlled by the expert system |
| **Lanes** | Three lanes: LEFT, CENTER and RIGHT. The car normally drives in RIGHT (keep-right rule) and uses CENTER or LEFT to overtake or avoid hazards |
| **Road** | A loop divided into 5 m blocks (500 m by default). After the last block the car starts again at the first. Fixed objects are placed in a grid of blocks × lanes (the road map) |
| **Road elements** | Fixed objects on the road: speed-limit sign, stop sign, traffic light, crosswalk, pothole and static obstacle (severity LOW, MEDIUM or HIGH), barrier (open or closed). Speed-limit signs, traffic lights, crosswalks and stop signs apply to the whole road, whatever lane they were written to |
| **Stop line** | Where the car must stop: 15 m before a stop sign, 18 m before a traffic light |
| **Road markings** | The lines between lanes: dashed (may be crossed) or continuous (may not be crossed). One line separates LEFT and CENTER, another separates CENTER and RIGHT |
| **Other road users** | Other vehicles (their own speed, in one lane; they stop for a pedestrian ahead and then speed back up) and pedestrians (standing on the road, crossing it, or finished crossing) |
| **Weather** | Clear or rain, plus two factors between 0.2 and 1.0: visibility and road grip. "Low visibility" and "low grip" are represented as rain with reduced factors |
| **Sensors** | Speedometer; front radar (the nearest vehicle ahead in the car's lane and in each neighbouring lane, up to 130 m); camera (signs, lights, crosswalks, pedestrians, potholes, obstacles, barriers, up to 120 m, less in poor visibility); blind-spot sensors (whether each side lane can be entered); environment sensor (weather) |
| **Perception vs. reality** | The simulated world knows where everything is; the expert system is meant to know only what the sensors report. An object becomes "known" when a sensor sees it, and is forgotten shortly after the sensor stops reporting it (0.25 s for vehicles, 0.4 s for lights, 1 s for pedestrians, hazards and signs read ahead, 1.5 s for stop signs, except a stop still owed at the line), with exceptions noted in §10 |
| **Time-to-collision (TTC)** | The number of seconds until the car would hit the vehicle ahead if both kept their speeds: distance ÷ closing speed |
| **Legal speed limit** | The limit of the last sign passed. A sign read ahead is "pending" until the car reaches it. Before any sign, a placeholder of 120 km/h applies, which is never used as a target |
| **Cruise speed** | The speed a scenario starts with; the car returns to it after slowing down when no sign governs the road |
| **Decision** | What the system chooses in each 0.1 s step: a **speed decision** (action, recommended speed, risk, priority, reason, rule, evidence) and a **lane decision** (keep or change lane, with its reason and rule) |
| **Proposal** | What one rule suggests in one step. All proposals are kept with their outcome |
| **Priority** | The importance of a proposal (seven levels, from comfort up to emergency); see §8.11 |
| **Speed ceiling** | A proposal that says "no faster than …". All ceilings apply at once, so the lowest wins |
| **Risk level** | LOW, MODERATE, HIGH or CRITICAL; informative only. It does not affect which proposal wins |
| **Maneuver** | A lane change. It is carried out progressively over about 3 seconds, with the turn indicator on, and cannot be cancelled once started |
| **Working memory** | The set of facts the system currently believes (current speed, limit, nearest pedestrian, lane occupancy …), visible in the interface |
| **Fuzzy logic** | A way of reasoning with vague categories ("reduced visibility" to some degree) instead of sharp thresholds |

---

## 6. Inputs and Evaluated Conditions

### 6.1 Inputs

| Source | Information | Timing |
|---|---|---|
| Scenario | Road map, objects, other road users, weather, starting speed and lane, line markings, scripted events | When a scenario is loaded |
| Operator | New objects or road users, map painting, weather changes, lane-change requests, START/PAUSE/RESET, engine switch | Applied at the next 0.1 s step |
| Speedometer | Current speed | Every 0.1 s |
| Front radar | Distance, closing speed and TTC of the nearest vehicle ahead in the same lane (≤ 130 m); distance and closing speed of the nearest vehicle ahead in the lane to the left, to the right and two lanes to the left | Every 0.1 s |
| Camera | Nearest speed-limit sign, traffic light, stop sign (road-wide, any lane), crosswalk, pothole, obstacle, barrier (≤ 120 m × visibility; path lane preferred); the most relevant pedestrian ahead and whether it is in the path or crossing | Every 0.2 s |
| Blind-spot sensors | For each side: can the car move into that lane? No when it is the road edge, there is a continuous line, a hazard or pedestrian, or a vehicle too close or closing in | Every 0.2 s |
| Environment sensor | Weather condition, visibility, grip | Every 0.4 s |

### 6.2 Conditions the rules evaluate

| Situation | Conditions (thresholds as coded) |
|---|---|
| Collision with vehicle ahead | TTC below 2.5 s in at least 3 readings within 2 s, or a single TTC below 1.3 s |
| Following distance | Above the speed from which braking at 2.0 m/s² still matches the other vehicle's speed 12 m behind it; within 12 m of a moving vehicle; within 14 m of a stopped one; follow at its speed between 12 and 20 m (up to 40 m on low grip) |
| Overtaking | Slower vehicle ahead, left lane clear and its next vehicle at least 20 m further than the one to overtake; no pedestrian conflict and no red/yellow light within 125 m; no continuous line; room to follow in the new lane (checked when overtaking a vehicle the car is closing in on) |
| Slower vehicle in the left lane | Never draw level with it (a vehicle moving at 1.4 m/s or more, and at least 1.4 m/s slower than the car): slow down to follow it 35 m behind, braking hard if comfortable braking would carry the car past it; then move in behind it and overtake on the left. The same holds, without the move in behind it, for a slower vehicle two lanes to the left |
| Pedestrian in path or crossing | Beyond 32 m: brake along a braking curve that ends 18 m before the pedestrian; 6–32 m: stop; beyond 6 m, emergency brake as soon as comfortable braking (2.8 m/s², 10 % margin) can no longer stop 6 m short of it; within 6 m: emergency brake; stay stopped while the crossing is occupied |
| Speed limit | A sign ahead is pending; slow down so as to reach the new limit 8 m before the sign; the limit applies from the sign; above the limit in force by more than 0.1 km/h → reduce |
| Stop sign | Up to 125 m before the stop line: brake along a comfortable stopping curve (emergency brake if comfortable braking no longer suffices); within 4 m, or past the line: stop (emergency brake if needed); stopped 1.2 s: proceed at 25 km/h |
| Traffic light | Yellow: commit once — stop if a comfortable stop still fits, otherwise drive on. Red: brake from the distance a comfortable stop needs (reaction 0.5 s + braking 2.8 m/s² + 18 m); hold at the stop line; green while stopped: resume |
| Pothole | In the car's path within 50 m, a side proved safe and room to leave the lane before it: change lane (prefer right). Otherwise, from the distance a comfortable slow-down to 30 km/h needs (at least 40 m): slow down along a curve to 30 km/h at the pothole, 30 km/h within 40 m, 20 km/h under 15 m; a severe (HIGH) pothole is braked for hard when comfortable braking no longer gets the car down to 30 km/h by it |
| Obstacle | In the car's lane within 50 m, a side proved safe and room to leave the lane before it: change lane (prefer left); within the comfortable stopping distance (at least 60 m) while it is in the car's path: hold or brake so as to stop 10 m before it, emergency brake if needed; HIGH severity within 8 m: emergency brake |
| Closed barrier | In the car's lane within 55 m: change lane if a side is proved safe and there is room to leave the lane before it. While it is in the car's path, within the comfortable stopping distance (at least 55 m): hold or brake so as to stop 10 m before it, emergency brake if needed. With no safe side: mandatory stop, emergency brake if comfortable braking no longer stops in time |
| Weather (fuzzy) | Visibility and grip each belong partly to "poor", "reduced", "fair" and "good"; their combination gives a speed ceiling (examples in §8.9) |
| Rain and collision risk | Rain together with TTC below 3 s twice within 2 s → brake to 15 km/h |
| Lane change (any) | No maneuver running, no continuous line to cross, the side clear, room to follow the vehicle ahead in that lane |
| Keep right | Not in the right lane, right lane clear 80 m ahead, nothing to attend to, at least 4 s into the run, not within 5 s of a lane change the driver asked for (not during the presentation demo) |
| Moving off | Car (almost) stopped and nothing holds it any more |

---

## 7. Outputs and Decisions

### 7.1 The decision

Each 0.1 s step produces one decision with two parts, both shown in the interface:

| Part | Fields |
|---|---|
| Speed decision | Action, recommended speed, risk level, priority, reason, rule, evidence |
| Lane decision | Keep lane or change lane, its priority, reason and rule |
| Proposals | Every rule's suggestion in this step, with its outcome: won; lost to another rule; vetoed by an emergency or mandatory stop; capped by a lower speed ceiling; refused by the vehicle controller |
| Why not change lane | For the left and the right side: allowed, or which condition fails (maneuver running, continuous line, side occupied, no room to follow) |

The "headline" action shown in large type is the lane change while one is being made, and the speed action otherwise.

### 7.2 Actions and how the car reacts

| Action | Typical reason | Car's reaction |
|---|---|---|
| `MAINTAIN_SPEED` | Nothing special, speed compliant, or "do not speed up" | Holds or moves toward the recommended speed |
| `CONTINUE` | Moving off, proceeding after a stop sign, green light, open barrier | Moves toward the recommended speed |
| `ACCELERATE` / `RESUME` | Driver request / red light turned green | Accelerates at up to 1.6 m/s² |
| `REDUCE_SPEED` | Speed limit, weather, hazard, slower vehicle | Brakes comfortably (2.8 m/s²) toward the recommended speed |
| `BRAKE` | Pedestrian, vehicle close, rain with collision risk | Brakes comfortably; never speeds up |
| `STOP` | Red light, stop sign, closed barrier, pedestrian | Brakes comfortably to a standstill |
| `EMERGENCY_BRAKE` | Imminent collision, or no room left for a comfortable stop | Brakes hard (8.5 m/s²), hazard lights on |
| `CHANGE_LANE_LEFT` / `CHANGE_LANE_RIGHT` / `RETURN_RIGHT` | Overtaking, avoiding a hazard, driver request, keep-right | Indicator on, moves sideways into the next lane over about 3 s |
| `KEEP_LANE` | A requested lane change is not safe; overtaking is pointless | Stays in lane |
| `WAIT` | (defined but never used by any rule) | — |

**Note.** Below the "mandatory stop" priority, the recommended speed acts as a *target*. A `REDUCE_SPEED` decision whose target is *above* the current speed therefore makes the car speed up. This is a confirmed defect (R-02, R-23); `BRAKE` and `STOP` no longer do this.

### 7.3 Explanations

- **Timeline:** perceptions ("Speed limit detected 50 km/h"), rule activations and firings, decisions, actuator changes, and maneuver start and end, each with its simulation time.
- **Decision panel:** the speed and lane decisions with their rules; a list of all proposals with outcome badges; the "why not change lane" answer for each side; the active engine.
- **Working memory:** every fact currently believed, with its source.
- **Explanation view:** "why" — the reason, the rule and the evidence of the current decision. Fuzzy weather decisions list the memberships, the conclusions and the resulting speed.

---

## 8. The Driving Rules in Plain Language

The precise conditions of the 123 rules are in [BUSINESS_RULES.md](BUSINESS_RULES.md). In plain language:

### 8.1 Emergencies

The car brakes as hard as it can when:

- the radar keeps reporting that the car would hit the vehicle ahead within 2.5 seconds, or reports less than 1.3 seconds even once;
- a pedestrian in its path or crossing is within 6 m, or further away but too close to stop 6 m short of it by braking comfortably;
- a high-severity obstacle is within 8 m in the lane the car is actually in.

### 8.2 Pedestrians

A pedestrian matters when it is in the car's path (its lane, or less than 3.5 m to the side), or when it is crossing the road, in whatever lane. For such a pedestrian, the car:

- starts braking far ahead, following a braking curve that would bring it to a stop about 18 m before the pedestrian, and never speeds up towards it;
- stops when the pedestrian is 6–32 m away;
- brakes as hard as possible when it can no longer stop comfortably 6 m short of the pedestrian (for example a pedestrian first seen 90 m ahead at 90 km/h), and always within 6 m;
- stays stopped while the pedestrian is still crossing, even after it has left the car's lane.

Stopping for a pedestrian counts as a mandatory stop, like a red light. A pedestrian waiting at the side who is not crossing is watched but does not slow the car.

### 8.3 Vehicles ahead, overtaking and keeping right

- **Following.** The car keeps enough distance to brake to the speed of the vehicle ahead and still be 12 m behind it. Behind a vehicle it cannot overtake, it follows at that vehicle's speed. When too close, it brakes to slightly below that vehicle's speed so that the gap reopens.
- **Overtaking.** If the vehicle ahead is slower and the left lane is clear (and clearer than the car's own lane), the car overtakes on the left with the indicator on. Once the overtaken vehicle is behind and the right lane is clear 80 m ahead, it returns to the right, one lane at a time.
- **When not to overtake.**
  - A slower vehicle in the lane to the left is never passed on its right: the car slows down behind it, then moves in behind it and overtakes it on the left. A slower vehicle two lanes to the left is not passed on its right either: the car stays behind it.
  - The car does not overtake right before a pedestrian or a red/yellow light.
  - If every lane is slow, it gives up the overtake after 3 s and returns right instead of following forever in the overtaking lane.

### 8.4 Speed limits and cruising speed

- **A new sign.** When the camera reads a sign ahead, the limit does not change yet. If the new limit is lower, the car slows down just in time to reach it 8 m before the sign. From the sign onwards the new limit is in force.
- **No sign yet.** When no sign has been seen, the car returns to the scenario's cruising speed after any slow-down.
- **Speed limits always apply.** No other rule can make the car drive faster than the limit in force.

### 8.5 Stop signs

- The car brakes smoothly so as to stop at the stop line (15 m before the sign); if it is too fast to stop comfortably, it brakes hard.
- It must stand still for 1.2 seconds, then proceeds at low speed.
- A stop sign governs the whole road: the car obeys it whichever lane it drives in, and a lane change on the approach does not release it.

### 8.6 Traffic lights

- **Yellow:** the car decides once. If it can still stop comfortably before the stop line, it stops; if it is too close, it drives on and does not brake across the light. The decision is not revised when the light turns red.
- **Red:** the car starts braking as soon as the distance needed for a comfortable stop is reached, stops at the stop line 18 m before the light, and waits. If the red light is first seen too close, it brakes hard.
- **Green:** a stopped car resumes. Near a green light the car also slows down to 50 km/h (R-24).
- **The cycle:** a yellow light turns red after 3 seconds, and a red light turns green 5 seconds after the car has stopped at it.

### 8.7 Potholes, obstacles and barriers

- **Hazard in the car's lane, side lane safe:** the car changes lane. Potholes prefer the right; obstacles and barriers prefer the left.
- **Only the car's path counts:** a hazard in another lane does not slow the car. The path is the car's lane, or the lane it is moving into during a lane change.
- **Too close to go round:** a lane change only counts as a way past when it takes the car out of the lane before the hazard; closer than that the car brakes for it.
- **No safe side lane:** for a pothole, it slows down (to 30 km/h at the pothole, or 20 km/h when very close), braking hard for a severe pothole if comfortable braking would not be enough. For an obstacle or a closed barrier, it brakes early enough to stop 10 m before it, braking hard if needed. With no safe side, a closed barrier is also a mandatory stop.
- **Open barrier:** passed; the car slows to 35 km/h near it (R-24).

### 8.8 Lane changes

Every lane change, whether decided by the rules or requested by the operator, is made only if all four conditions hold:

1. no other lane change is in progress;
2. no continuous line has to be crossed;
3. the side sensor reports the lane clear;
4. there is room to follow the vehicle ahead in that lane after the change.

The decision panel shows which condition fails when a lane change is not possible. A lane change cannot be cancelled once started.

### 8.9 Weather

Visibility and grip are judged with fuzzy categories ("poor", "reduced", "fair", "good"). The rules combine them into a speed ceiling:

| Weather (visibility / grip) | Speed ceiling |
|---|---|
| Clear, 1.0 / 1.0 | none |
| Rain, good visibility and grip (1.0 / 1.0, also 0.9 / 0.9) | 65 km/h |
| Rain, 0.8 / 0.8 | about 61 km/h |
| Rain as set by the "rain" button (0.7 / 0.65) | about 51 km/h |
| Visibility 0.5 or grip 0.5 | 40 km/h |
| Visibility 0.3 | 25 km/h |

The ceiling never lifts the car above the legal limit. On a road without signs, though, it can raise a slower car up to the ceiling (R-23). In rain, combined with a repeatedly low time-to-collision, the car brakes to 15 km/h.

### 8.10 When nothing applies, and moving off

If no rule has anything to say, the car keeps its speed and lane. If the car has stopped and nothing holds it any more (the light turned green, the pedestrian left, a lane opened), it moves off gently up to 30 km/h, or the speed limit if lower.

### 8.11 How the rules are combined (conflict resolution)

Many rules can apply at the same moment. Each one *proposes*; speed and lane are then decided separately:

1. **Mandatory stops and emergencies win the speed decision.** If any rule of level 60 or 70 applies, the highest level wins (and, among equals, the slowest).
2. **Otherwise, all speed ceilings apply together.** Rules of levels 50, 40 and 20 say "no faster than …", and the lowest of them wins. A rule of level 30 (moving off, resuming, proceeding) or 10 (cruising, comfort) gives the speed when no ceiling is lower.
3. **Lane changes are decided apart.** The highest level wins; at equal level "return right" beats "change lane", which beats "keep lane".
4. **Braking for a stop or an emergency cancels a lane change that has not started**, unless the lane change itself comes from an equal or higher level.

| Level | Name | Covers |
|---|---|---|
| 70 | Emergency safety | Imminent collision; a pedestrian too close for a comfortable stop |
| 60 | Legal mandatory stop | Red/yellow light, stop sign, closed barrier stop, pedestrian |
| 50 | Collision avoidance | Vehicle ahead, obstacle, closed barrier in the path, rain with collision risk; avoiding obstacles and barriers |
| 40 | Road hazard | Potholes, weather |
| 30 | Maneuver safety | Overtaking, returning right, requested lane changes, moving off, proceeding |
| 20 | Speed compliance | Speed limit, limit ahead, cruising speed, slower vehicle on the left (one or two lanes over) |
| 10 | Comfort progress | Default, compliant cruising, green light, open barrier, acceleration request |

**Example** (verified by a test): a green light 20 m ahead (level 10, "continue at 50 km/h") and a pedestrian 14 m ahead in the path (level 60, "stop") → the car stops.

The risk level shown with a decision does not influence the choice.

### 8.12 Emergency behaviour

Emergency proposals (level 70) always win. The car then brakes at 8.5 m/s² with hazard lights on, and a lane change that has not started yet is cancelled. When the danger is gone (for example the radar no longer reports a critical TTC), the next most important rule takes over. Several rules of lower levels (stop sign, red light, obstacle, closed barrier, severe pothole, slower vehicle on the left) also switch to an emergency brake when comfortable braking no longer fits.

---

## 9. Practical Examples

All examples were observed by running the real system with the Drools engine: either the project's own tests (passing in the previous documentation analysis) or exploratory runs made during it ([TESTING.md §5](TESTING.md#5-exploratory-verification-probes)).

### Example 1 — Speed-limit sign (scenario S2)
- **Situation:** the car drives at 90 km/h in the right lane; a 50 km/h sign stands 140 m ahead.
- **What happens:**
  1. At 0.9 s the camera reads the sign at about 118 m. The sign is noted as *pending*: the limit is still the old one.
  2. "Slow down in time for the speed limit ahead" keeps the speed until braking becomes necessary, then lowers it gradually (about 2 m/s²).
  3. The car reaches 50 km/h 4.3 m before the sign (6.7 s).
  4. At the sign (7.1 s), "Activate the pending speed limit at its sign" makes 50 km/h the limit in force, and "Maintain speed when compliant with detected limit" keeps the car at 50 km/h.
- **Justification shown:** "Speed limit 50 km/h ahead; slowing to reach it at the sign", with the remaining distance and the current speed.

### Example 2 — Red light and the light cycle (scenario S3)
- **Situation:** the car drives at 70 km/h; a red light is 120 m ahead.
- **What happens:**
  1. The car commits to stop.
  2. At about 95 m it starts braking comfortably.
  3. It stops 19.2 m before the light, just before the stop line.
  4. Five seconds later the light turns green: "Green traffic light releases red-light hold", then "Move off when stopped and nothing holds the vehicle".
  5. The car builds up again to 70 km/h.

### Example 3 — Conflict: green light and pedestrian (test `conflictGreenLightAndPedestrianPedestrianWins`)
- **Inputs:** green light 20 m ahead; pedestrian in the path 14 m ahead.
- **Proposals:** "Green traffic light allows progression when no higher hazard" (level 10) and "Stop for pedestrian in crossing zone" (level 60).
- **Decision:** `STOP`. The green-light proposal is shown as "lost to Stop for pedestrian in crossing zone".

### Example 4 — Collision risk (test `ttcCriticalTriggersEmergency`)
- **Inputs:** three radar readings within 0.3 s with TTC 2.3, 2.1 and 1.9 s.
- **Rule:** "Emergency brake when persistent TTC is critical".
- **Decision:** `EMERGENCY_BRAKE`, risk CRITICAL.
- **Counter-example (`isolatedTtcSampleDoesNotTriggerPersistentRule`):** a single reading of 2.2 s does not trigger it.

### Example 5 — Overtaking and returning (scenario S14)
- **Situation:** the car drives at 50 km/h in the right lane; a vehicle doing 18 km/h is 45 m ahead; the centre lane is free.
- **What happens:**
  1. From the first step, "Overtake slow front vehicle via left lane" decides the lane while the speed rules keep a safe distance.
  2. The car is in the centre lane at 3.1 s.
  3. Once the slow vehicle is behind and the right lane is clear, "Return to right lane after overtaking slow vehicle" brings the car back; it is in the right lane at 14.5 s.

### Example 6 — Why not change lane? (test class `LaneChangeWhyNotE2ETest`)
- **Situation:** the car is in the right lane at 50 km/h; a vehicle doing 20 km/h is 45 m ahead in the centre lane.
- **What the panel shows for the left side:** the side sensor reports the lane clear, but "vehicle ahead in the CENTER lane too close to follow safely". The lane change is therefore not allowed.

### Example 7 — Former defect, addressed in the rules: closed barrier at speed with no way around (probe P-13)
- **Situation:** a closed barrier in the car's lane, and a continuous line on the only side.
- **What happened in the probe (earlier version):** the stop was decided only 55 m before the barrier and used comfortable braking.
  - From 50 or 60 km/h the car stopped in time.
  - From 70 km/h it reached the barrier at about 30 km/h.
  - From 90 km/h it reached it at about 65 km/h.
- **Now:** "Stop before closed barrier while still in its lane" brakes from the distance a comfortable stop needs at the current speed, and both barrier stop rules brake hard when comfortable braking no longer fits. The probe has not been re-run on this version.
- **See:** R-22.

### Example 8 — Known defect: rain on a road without signs (probe P-16)
- **Situation:** the car cruises at 30 km/h and it starts to rain.
- **What happens:** the weather rule's "no faster than 51 km/h" becomes the only proposal, and the car **speeds up** to about 51 km/h. On a road with a 30 km/h sign, the limit is respected.
- **See:** R-23.

---

## 10. Confirmed Limitations and Missing Features

### 10.1 Implemented versus intended

Intentions come from the draft preliminary report (`Relatorio_Previo_ENGCIA/seccoes/sensores.tex` and chapter headings), the conceptual flowchart `diagrams/first_diagram.md` (both in Portuguese, both drafts) and the README. They are **inferred** intentions, not an approved specification. The detailed mapping, with the same status wording, is in [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md).

| Intended capability | Status (requirement ID) | Notes |
|---|---|---|
| Respect the posted speed limit | Implemented and tested (REQ-01) | The limit is now respected in rain too |
| Speed adaptation in rain, low visibility, low grip | Implemented and tested (REQ-05, REQ-06, REQ-07) | Fuzzy logic. On roads without signs it can raise a slower car (R-23). Grip does not change braking performance in the physics |
| Emergency braking on critical time-to-collision | Implemented and tested (REQ-10) | Fixed TTC thresholds (2.5 s and 1.3 s). Does not work in the JavaFX client (A-07) |
| Criticality from braking distance, reaction time and grip | **Partially implemented** (REQ-11) | Stopping distances with reaction time are used for lights, obstacles, barriers, potholes, stop signs and pedestrians; grip does not affect them |
| Frontal obstacle in the path: swerve or brake | Implemented and tested (REQ-15) | An emergency brake can be hidden by a simultaneous pedestrian stop (R-11) |
| Keeping a safe distance (time gap of 3 s, 4–5 s in rain or fog) | **Partially implemented** (REQ-08, REQ-09) | Distance-based (braking curve with a 12 m gap), larger on low grip; no time-gap rule |
| Unintentional lane departure (time to line crossing) | Not implemented (REQ-30) | The car only moves sideways when a lane change is decided |
| Pedestrian in the path | Implemented and tested (REQ-13) | Stops from up to 90 km/h on both engines, braking hard when a comfortable stop no longer fits (R-04, now addressed in the rules; `PedestrianAtSpeedE2ETest`). The earlier probe had the car reach the pedestrian from 120 km/h; nothing above 90 km/h is tested and that probe has not been re-run |
| Road users who may enter the path; cyclists | **Partially implemented** (REQ-14) | Crossing pedestrians are anticipated in any lane; waiting ones are only watched; no cyclists |
| Overtaking with checks for oncoming traffic, visibility and cancellation | **Partially implemented** (REQ-26) | Space, legality and room to follow are checked; no oncoming traffic in the model; a started lane change cannot be cancelled |
| Intersections, right of way, giving way | Not implemented (REQ-24) | Only traffic lights and stop signs exist |
| Emergency vehicles | Not implemented (REQ-25) | — |
| Driver state (alcohol, fatigue, medication, seat belts, distraction) | Not implemented (REQ-45) | — |
| Vehicle state (faults, fuel or energy, tyre pressure) | Not implemented (REQ-46) | — |
| Sensor failure, degraded mode, controlled stop on failure | Not implemented (REQ-43) | Sensors never fail. Only a failure of the rule engine is handled (the simulation pauses) |
| Sensor, time and confidence recorded for each perceived fact | **Partially implemented** (REQ-37) | Sensor and time are recorded; there is no confidence value |
| Confirmation by a second reading when not critical | **Partially implemented** (REQ-41) | Only the time-to-collision rules distinguish one reading from several |
| Camera + radar fusion; LiDAR as tie-breaker | Not implemented (REQ-42) | — |
| "Unknown" state when a sensor gives no data | **Partially implemented** (REQ-40) | Side lanes count as occupied until first sensed; elsewhere no data means "nothing there" |
| Speed limited so the car can stop within the sensors' effective range | **Partially implemented** (REQ-44) | The camera sees less far in poor visibility and the weather rules lower the speed, but no rule ties the speed to the visible distance |
| Driver warning before emergency braking | Not implemented (REQ-12) | — |
| "Why" explanation | Implemented and tested (REQ-34) | Reason, rule, evidence and timeline |
| "Why-not" explanation | Implemented and tested (REQ-35) | Every proposal's outcome; "why not change lane" per side |
| Handling of uncertainty | **Partially implemented** (REQ-36) | Vague weather categories use fuzzy logic; uncertain perception is not represented |
| Global risk modes (normal / alert / safe mode) | **Partially implemented** (REQ-47) | Each decision carries a risk level, but it drives no mode |
| Two interchangeable engines that decide alike | Implemented and tested (REQ-58) | `PrologDroolsEquivalenceTest` and the Prolog runs of the dual-engine E2E tests pass in the latest run (SWI-Prolog installed); equivalence is shown for the tested inputs only, and without SWI-Prolog those tests are skipped. That every Drools driving rule has a Prolog rule of the same name is checked by `RuleNameParityTest`, which reads the sources |

### 10.2 Confirmed behavioural defects

All of these were demonstrated by running the system (Drools engine). Details and recommended corrections are in [BUSINESS_RULES.md §17](BUSINESS_RULES.md#17-consistency-audit). Several defects found in the earlier version (speed limit overridden in rain, gap lost behind slow vehicles, overtaking that stopped working, misread road markings, stale traffic-light memory, scenarios that showed nothing) are **fixed**. Since that analysis the rules also address the closed barrier at speed (R-22), the pedestrian at very high speed (R-04), the pothole in another lane (R-15) and a slower vehicle two lanes to the left (R-26); those fixes were checked against the rule code and the tests written for them, not by re-running the probes.

- **"Reduce speed" that speeds up.** A `REDUCE_SPEED` decision whose target is above the current speed makes the car accelerate (R-02). In rain on a road without signs this raises a slow car to the weather ceiling (R-23).
- **Two hazards at once.** A stop for a pedestrian can hide an emergency brake needed for a nearer obstacle (R-11).
- **False "approaching" alarm.** A vehicle pulling away can trigger a slow-down meant for an approaching one (R-09).
- **"Allows progression" that slows down.** Near a green light the car slows to 50 km/h, and near an open barrier to 35 km/h (R-24).
- **Hesitant following.** Behind slow traffic, at the edge of a following distance, the decision can switch many times per second between speeding up and slowing down (R-25).
- **The desktop client cannot pass a stop sign.** With the JavaFX client's clock, the car never moves off after a stop sign, and emergency braking on TTC does not work (A-07).

### 10.3 Simplifications of the model (by design)

- **Road and vehicles:** a single straight, looping road with three lanes. Vehicles are points without length. There is **no collision detection**: an object the car reaches simply disappears.
- **Physics and weather:** constant accelerations and decelerations; weather does not affect braking distance; only clear and rain weather.
- **Traffic and lights:** other vehicles keep their speed and lane, except that they stop for a pedestrian ahead and then speed back up. A red light turns green only after the car has waited 5 s at it, so the light cycle reacts to the car.
- **Crosswalks:** they are drawn and seen by the camera, but no rule reacts to a crosswalk itself; only pedestrians matter.
- **Users and sessions:** one simulation shared by all browser windows. Completed sessions are saved as JSON files (`SessionHistoryStore`, by default under `~/.projeto-1/sessions`) and listed by `/api/history/sessions`.
- **Prolog engine:** it needs SWI-Prolog installed. Without it, only Drools can be used, and the Prolog tests are skipped.
