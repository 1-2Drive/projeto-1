# Glossary

**Audience:** all readers. Technical terms used in the documentation, with examples from this project. Terms are in alphabetical order.

The project has two inference engines: **Drools** (Java, the default) and **SWI-Prolog** (the project's own rule interpreter written in Prolog). Where a term comes from logic programming, the entry says how it applies to each engine.

---

**Accumulate** — A Drools construct that computes a value (count, minimum, maximum …) over a set of matching facts or events. *Example:* "Emergency brake when persistent TTC is critical" counts the `TTCEvent`s below 2.5 s in the last 2 s and fires if there are at least three.

**Action (driving action)** — The kind of behaviour a decision asks for, from the enum `DrivingAction`. Each action belongs to a channel:
- **speed (longitudinal):** `CONTINUE`, `MAINTAIN_SPEED`, `ACCELERATE`, `REDUCE_SPEED`, `BRAKE`, `EMERGENCY_BRAKE`, `STOP`, `WAIT` (never used), `RESUME`;
- **lane (lateral):** `KEEP_LANE`, `CHANGE_LANE_LEFT`, `CHANGE_LANE_RIGHT`, `RETURN_RIGHT`.

**Activation** — A rule whose conditions are currently satisfied by a particular combination of facts, waiting on the agenda to fire. The Drools timeline records activations as `RULE_ACTIVATED`; the Prolog engine stores them as `activation/5` facts.

**Actuator** — The component that turns the decision into physical behaviour of the simulated car (acceleration, braking, turn indicator, lane-change start): `DecisionActuator`. It can refuse a lane change that the world shows to be unsafe or illegal ("Actuator safety guard"); see [ARCHITECTURE.md](ARCHITECTURE.md#92-audit-findings), A-02.

**Agenda** — The list of activations waiting to fire. In Drools it is ordered by agenda group and salience; in the Prolog engine by precedence, then rule order, then match order.

**Agenda group (phase)** — A Drools mechanism that groups rules so that only the group with the focus can fire. This project uses three, run in order every cycle: `perception`, `decision`, `fallback`. The order is set by the rule "Control: perceive, then decide, then fall back".

**Allen's interval relations (temporal operators)** — Relations between events in time, such as *after*, *before*, *coincides*. Drools CEP provides them as operators. *Examples:* the stop sign is satisfied when a speed reading comes `after[1200ms]` the moment the car stopped; a low TTC counts in rain only if a rain reading `coincides[400ms]` with it.

**Arbitration (decision arbitration)** — How this project turns all the proposals of one cycle into a decision. It is implemented in Java by `DecisionContext`, the same for both engines:
- **Speed:** mandatory stops and emergencies (priority 60–70) override; otherwise the lowest of the speed ceilings (priority 50, 40, 20) wins over the base (priority 30 or 10).
- **Lane:** the highest priority wins.
- **Veto:** braking for a stop can veto a lane change.

See [ARCHITECTURE.md §5](ARCHITECTURE.md#5-action-selection-and-priority-resolution).

**Backend (inference backend)** — One of the two interchangeable inference engines behind the interface `ExpertSystemBackend`: `DroolsBackend` or `PrologBackend`. Selected with `expert.backend` or switched at runtime with `POST /api/simulation/backend/{drools|prolog}`.

**Backtracking** — In Prolog, the search mechanism that undoes variable bindings and tries alternative clauses when a goal fails.
- *Prolog engine:* its matching enumerates alternative matches with `findall/3` (a form of backtracking), and `prove/1` tries the clauses of a helper predicate in turn.
- *Drools:* nothing is undone or retried; every rule whose conditions hold is activated.

**Backward chaining** — Goal-driven inference: to prove a goal, prove the sub-goals it depends on, down to known facts. Used here for one goal, "is it safe to change lane to this side?":
- in Drools, the query `safeToChangeLane` and its sub-goal queries (`lane-safety.drl`);
- in Prolog, `safe_to_change_lane/1` proved by `prove/1` (`engine.pl`).

Both are called from forward-chaining rules.

**Baseline decision** — The decision taken when no specific rule applies: "Default maintain speed baseline" proposes `MAINTAIN_SPEED` at the current speed with the lowest priority (10). If no speed rule proposes at all, `DecisionContext` keeps the current speed ("Default base decision"); the lane default is `KEEP_LANE`.

**Belief** — See *state fact*. What the expert system currently assumes about one topic (e.g. the nearest pedestrian), as opposed to the ground truth of the simulated world.

**Blind-spot sensor** — The simulated side sensor (`BlindSpotSensor`) that reports, every 200 ms and for each side, whether the adjacent lane can be entered. The road edge and a continuous line count as "occupied".

**Boxed in** — The situation where the car follows a slow vehicle outside the right lane and cannot overtake (every lane slow, or the left side closed). Recorded as `BoxedInFact`; after 3 s the car gives up and returns right.

**Braking curve** — The highest speed from which the car can still stop, or match another speed, within a given distance at a given deceleration: v = √(v₀² + 2·a·d). Used by the following-distance, pedestrian, stop-sign, obstacle and speed-limit rules (a = 2.0 m/s² "anticipation" or 2.8 m/s² "comfortable").

**CEP (Complex Event Processing)** — Reasoning over time-stamped events and their temporal relations, such as "three readings within two seconds". Drools CEP is enabled by `eventProcessingMode="stream"` in `kmodule.xml`. Seven rules use sliding windows or Allen operators.

**Ceiling (speed ceiling)** — A speed proposal of priority 50, 40 or 20 that means "no faster than …". All ceilings bind at once, so the lowest one wins.

**Channel** — One of the two independent parts of a decision: the longitudinal channel (speed) and the lateral channel (lane), `DecisionChannel`.

**Comfortable deceleration** — 2.8 m/s², the braking rate the actuator uses for `REDUCE_SPEED`, `BRAKE` and `STOP` (`SimulationConfig`, `StoppingPolicy`).

**Commitment (traffic light)** — The decision, taken once per light and per yellow/red phase, to stop or to drive on (`TrafficLightCommitment`: `NONE`, `COMMITTED_TO_STOP`, `COMMITTED_TO_PROCEED`). It keeps the car from swinging between stopping and driving on.

**Continuous line / dashed line** — Road markings between lanes (`RoadMarkingType`). A continuous line may not be crossed. The project stores one marking for the LEFT|CENTER boundary and one for the CENTER|RIGHT boundary; the query `noContinuousLine` maps each lane change to the line it really crosses.

**Crosswalk** — A road element painted across the road (`Crosswalk`). The camera reports it, but no rule reacts to it; pedestrians are handled on their own.

**Cruise speed** — The speed a scenario starts with (`initialSpeedKmh`), stored as `CruiseTargetFact`. The car returns to it after slowing down when no speed-limit sign governs the road.

**Decision** — The result of one cycle, stored in `DrivingDecision`. It contains:
- a speed decision (action, recommended speed, risk level, priority, reason, rule, evidence);
- a lane decision (action, priority, reason, rule, evidence);
- every proposal with its outcome;
- the why-not lane checks.

**Decision context** — `DecisionContext`, the per-cycle object that collects all proposals and arbitrates them. It is cleared at the start of each tick.

**Decision priority** — The importance level of a proposal (`DecisionPriority`): 70 emergency safety, 60 legal mandatory stop, 50 collision avoidance, 40 road hazard, 30 maneuver safety, 20 speed compliance, 10 comfort progress.

**Dependency index** — Meta-knowledge in the Prolog engine (`rule_reads/2`): for each fact type, the rules that read it, derived automatically from the rules. After a rule fires, only the rules that read a newly added fact type are matched again.

**DRL (Drools Rule Language)** — The language of the rule files in `src/main/resources/rules/*.drl`. A rule has a `when` part (conditions) and a `then` part (Java-like actions).

**Drools** — An open-source rule engine for Java (version 8.44.0.Final here), part of the KIE project. It is the default inference engine of this expert system.

**Edge case** — A situation at the boundary of a rule's conditions, or a rare combination, where behaviour may be surprising. *Examples in this project:*
- a following gap that sits exactly at a window boundary (R-25);
- a pedestrian stop and an obstacle emergency in the same cycle (R-11).

**Ego vehicle** — The car controlled by the expert system (`EgoVehicleState`), as opposed to other vehicles.

**Emergency deceleration** — 8.5 m/s², applied for `EMERGENCY_BRAKE` (`SimulationConfig`).

**Entry point (stream)** — A named input channel of a Drools session. This project uses six: `VEHICLE_STREAM`, `PERCEPTION_STREAM`, `ROAD_STREAM`, `TRAFFIC_STREAM`, `ENVIRONMENT_STREAM`, `COMMAND_STREAM` (`EventRouter`).

**Event** — A time-stamped observation, e.g. `SpeedLimitDetectedEvent` (the camera read a 50 km/h sign at 118 m).
- In Drools, events are declared with `@role(event)`, `@timestamp` and `@expires` in `00-declarations.drl` and removed automatically when they expire.
- The Prolog backend receives the same events as JSON.

**Evidence** — The values a rule attaches to its proposal to justify it, e.g. `"speed=90.0 km/h, limit=50 km/h"`. In Prolog, the evidence lists the working-memory facts the rule matched and the rules that concluded them.

**Expert system** — A program that solves problems in a specialised domain by applying explicitly represented expert knowledge, typically as rules, with an inference engine and an explanation facility. Here the domain is driving decisions for a simulated car.

**Explanation trace / timeline** — The chronological record of perception changes, rule activations and firings, fact changes, decisions, actuator changes and maneuvers (`ExplanationTrace`, at most 500 entries), shown in the user interface.

**Fact** — An object in working memory that rules can match.
- *Drools examples:* `VehicleStateFact` (current speed, lane …), `CurrentSpeedLimit` (50 km/h from sign `r28-CENTER`), `DrivingDecision`.
- *Prolog examples:* terms such as `ego_speed(90)`, `traffic_light(Id, red, 85.0)`, `proposal(stop, 0, high, legal_mandatory_stop, Reason)`.

**Forward chaining** — Data-driven inference: when facts are inserted or changed, every rule whose conditions now hold is activated and fires; its actions may add or change facts and activate further rules. Both engines work this way for the decision rules. *Example:* a camera event creates a pending speed limit, which activates "Slow down in time for the speed limit ahead".

**Fuzzy logic** — Reasoning with categories that hold *to a degree* (0 to 1) instead of true/false. The weather controller (`fuzzy-weather.drl`, `FuzzySupport`) has four steps:
1. **fuzzification:** visibility and grip become degrees of "poor", "reduced", "fair" and "good";
2. **inference:** the degrees are combined with min/max;
3. **composition:** the conclusions are joined;
4. **defuzzification:** the centroid of the result gives one speed ceiling.

**Ground truth (world truth)** — The actual state of the simulated world (`WorldState`): where every object really is. The expert system is intended to know only what sensors report, but some inputs are filled directly from ground truth ([ARCHITECTURE.md](ARCHITECTURE.md#92-audit-findings), A-01).

**Inference engine** — The component that applies rules to facts to derive conclusions:
- the Drools engine running a stateful `KieSession`;
- or the Prolog engine in `engine.pl` running inside a `swipl` process.

**Keep-right rule** — "Enforce keep right road law when right lane is safe", which returns the car to the rightmost lane (Portuguese Highway Code, Art. 13).

**KieSession (knowledge session)** — A Drools session holding the working memory and evaluating the rules. This project uses one stateful session per simulation, rebuilt on every reset and backend switch (`KnowledgeSession`).

**Knowledge base** — The body of expert knowledge:
- Drools: 119 rules and 6 queries in `src/main/resources/rules/`, compiled into `drivingKbase`;
- Prolog: 107 rules in `src/main/prolog/driving_rules.pl`, plus the perception state code in `driving_state.pl`.

**Lane / lane change / maneuver** — The road has three lanes (LEFT −3.5 m, CENTER 0 m, RIGHT +3.5 m from the centre line). A lane change (maneuver) moves the car sideways at 1.2 m/s (about 2.9 s per lane) through the states `IDLE` → `REQUESTED` → `CHANGING` → `COMPLETED` → `IDLE`. It cannot be cancelled.

**Lane occupancy** — Whether the lane on one side of the car may be entered (`LaneOccupancyState`, from the blind-spot sensor). Lanes start as "occupied" until sensed.

**Legal speed limit** — The limit of the last speed-limit sign passed (`CurrentSpeedLimit`). A sign read ahead is first kept as a *pending* limit (`PendingSpeedLimit`) until the car reaches it. Before any sign, a placeholder of 120 km/h with source `"default"` applies; it is never used as a target.

**Live query** — A Drools query that keeps notifying a listener when its results change. The session opens one on "Current Driving Decision".

**Meta-knowledge** — Knowledge about the rules themselves rather than about driving. In the Prolog engine:
- the dependency index (which rules read which facts);
- the control knowledge (rule precedence, rule order, refraction).

In Drools, the control rule that orders the agenda groups plays a similar role.

**Move-off** — Restarting a stopped car once nothing holds it, handled by `MoveOffState` and "Move off when stopped and nothing holds the vehicle".

**no-loop** — A Drools rule attribute that stops a rule from re-activating itself through its own fact update. Used by all perception rules.

**Outcome (proposal outcome)** — What the arbitration did with a proposal (`DecisionProposal.Outcome`):

| Outcome | Meaning |
|---|---|
| `WON` | It became the decision of its channel |
| `LOST_TO` | Another rule of the same channel won |
| `VETOED_BY` | A lane change cancelled by braking for a stop |
| `CAPPED` | A ceiling that set the speed of a stop decision |
| `REJECTED_BY` | The actuator refused the lane change |

**Pattern matching** — How rule conditions are evaluated against facts:
- In Drools, each pattern (e.g. `PedestrianStateFact(distanceMeters <= 6.0)`) is matched against objects by field constraints, and variables (`$distance`) are bound to field values.
- In the Prolog engine, patterns are Prolog terms matched against working-memory facts by unification.

**Pending speed limit** — See *legal speed limit*.

**Perception** — What the sensors report about the world, as events. It is limited by range and rate; for example, the camera sees signs up to 120 m, or 84 m in rain.

**Phreak** — The matching algorithm of Drools 6 and later (successor of Rete). It evaluates rule conditions lazily and fires activations in agenda-group and salience order.

**Precedence** — In the Prolog engine, the number that orders rule firing (higher first). It equals the salience of the matching Drools rule.

**Predicate** — In Prolog, a named relation defined by facts and clauses. *Examples in this project:* `safe_to_change_lane/1`, `room_to_follow/1`, `prove_goal/2`, `why_not/2`. Driving rules themselves are stored as `rule/4` facts, so a "rule" in the Prolog engine is data interpreted by `engine.pl`. In Drools, the nearest equivalents are named rules, their condition patterns, and queries.

**Probe (exploratory probe)** — A small program written during this documentation analysis and run on a copy of the repository to observe behaviour in a controlled situation (P-01 to P-18). Probes are not part of the project's test suite ([TESTING.md §5](TESTING.md#5-exploratory-verification-probes)).

**Proposal** — A candidate action submitted by a rule (Drools: `droolsSupport.proposeDecision(...)`; Prolog: a `proposal/5` fact). All proposals of a cycle are kept and arbitrated per channel.

**Pseudo clock / realtime clock** — Drools session clocks.
- *Pseudo clock* (used by the server and the tests): the program advances time explicitly by 100 ms per tick, so event timestamps and time windows agree.
- *Realtime clock* (used by the JavaFX client): time is the computer's clock, which does not match the simulation timestamps (A-07).

The Prolog backend has no session clock: each request carries the simulation time.

**Query** — A named Drools pattern that returns matching facts on request or proves a goal. Here: "Current Driving Decision" and the five lane-safety queries (`safeToChangeLane`, `maneuverIdle`, `noContinuousLine`, `sideClear`, `roomToFollow`).

**Recommended speed** — The speed attached to a speed decision.
- Below priority 60, the actuator steers the car toward it: it can accelerate as well as brake, except under `BRAKE`, `STOP`, `WAIT` and `EMERGENCY_BRAKE`, which never raise the speed.
- At priority 60–70, the action decides how hard to brake.
- Lane decisions have no recommended speed (`NaN`; `no_target` in Prolog).

**Refraction** — In a production system, the principle that the same rule instantiation fires only once. The Prolog engine records fired instantiations (`fired/2`) for the current cycle; Drools has equivalent behaviour built in, plus `no-loop`.

**Risk condition** — A situation that requires the system to act for safety: a critical TTC, a pedestrian in the path or crossing, an obstacle in the lane, a red light, a closed barrier, reduced visibility or grip, exceeding the speed limit. Each is the condition part of one or more rules.

**Risk level** — The label attached to a decision (`RiskLevel`): LOW, MODERATE, HIGH, CRITICAL. It is informative and does not affect arbitration.

**Road element** — A fixed object on the road (`RoadElement` subclasses): speed-limit sign, stop sign, traffic light, crosswalk, pothole, static obstacle, barrier.

**Road map (matrix)** — The road layout as a grid of 5 m rows × 3 lanes with legend codes (e.g. `9` = 50 km/h sign); the road loops after the last row (`RoadMap`, `MapLegend`).

**Room to follow** — The lane-change sub-goal `roomToFollow`: after a 3 s lane change, the car must still be able to slow to the speed of the vehicle ahead in the new lane and stay 12 m behind it, braking at 2.0 m/s² (`StoppingPolicy.roomToFollow`).

**Rule** — A unit of knowledge in the form "when these conditions hold, then do this".
- *Drools:* written in DRL. Example: "Stop for pedestrian in crossing zone" — when a pedestrian in the path or crossing is 6–32 m away, propose `STOP`.
- *Prolog:* a `rule(Id, Precedence, Conditions, Actions)` term.

**Rule conflict** — A situation where several rules propose incompatible actions at the same time, e.g. "continue on green" versus "stop for pedestrian". Resolved by *arbitration*. Some conflicts are resolved incorrectly; see [BUSINESS_RULES.md §17](BUSINESS_RULES.md#17-consistency-audit).

**Safety action** — An action whose purpose is to reduce risk: `EMERGENCY_BRAKE`, `BRAKE`, `STOP`, `REDUCE_SPEED`, `KEEP_LANE`, or a lane change that avoids a hazard.

**Salience** — A Drools rule attribute that orders firing within an agenda group (higher fires first). Since the decision is arbitrated afterwards, salience affects the outcome only in exact ties and for rules that change state that other rules read.

**Scenario / timed action** — A predefined driving situation in `scenarios.json` (road map, objects, actors, weather, starting speed and lane). A timed action is an event scheduled at a simulation time, such as "add a pedestrian at 47 s".

**Sensor** — A simulated measuring device (`Sensor` implementations): speed sensor, front radar, camera, blind-spot sensor, environment sensor. Each has a range and an update rate.

**Sliding window** — A Drools CEP construct that limits a pattern to recent events, e.g. `over window:time(2s)` (events of the last 2 seconds). This project uses only time windows. The Prolog backend keeps the equivalent windows itself (`driving_state.pl`).

**Snapshot / WebSocket** — A snapshot is the full state sent to the user interface (`ApiSimulationSnapshot`). The WebSocket at `/api/ws/simulation` pushes one as a JSON frame on connect, every 100 ms tick and after each command; it is one way, backend to frontend.

**Stale perception** — A belief that is no longer confirmed by the sensors. Beliefs are reset after 250 ms (vehicles), 400 ms (traffic lights), 1 s (pedestrians, hazards, pending signs) or 1.5 s (stop signs) without a report. The speed limit in force is never reset.

**State fact** — A long-lived, mutable fact holding the current belief about one topic (e.g. `PedestrianStateFact`, `FrontVehicleState`), updated by the perception rules in `01-state-updates.drl`.

**Stop line** — Where the car must stop: 15 m before a stop sign, 18 m before a traffic light (`StoppingPolicy`).

**Sub-goal** — A condition that must be proved to prove a goal. `safeToChangeLane` has four: `maneuverIdle`, `noContinuousLine`, `sideClear`, `roomToFollow`. The why-not answer names the sub-goals that fail.

**SWI-Prolog** — A free Prolog system. This project runs it as an external process (`swipl`, version 9.x) for the Prolog backend; it must be installed separately and was not installed during this analysis.

**Test coverage** — The extent to which tests exercise the code or rules. No line or branch coverage tool is configured. In this analysis, rule-firing coverage was measured for the Drools rules; see [TESTING.md §6](TESTING.md#6-rules-and-behaviours-covered).

**Tick** — One step of the simulation loop: 100 ms of simulation time, in which physics, sensing, inference and actuation run once (`SimulationEngine.tick()`).

**Time headway (THW)** — Distance to the vehicle ahead divided by the car's own speed (seconds). Recommended by the draft report (3 s, 4–5 s in rain or fog). **Not implemented**; the following rules use distances and braking curves instead.

**Time to line crossing (TLC)** — The time until the car would cross a lane marking; proposed in the draft report for lane-departure detection. **Not implemented.**

**Time-to-collision (TTC)** — Distance to the vehicle ahead divided by the closing speed (seconds); infinite when not closing. Computed by the front radar for the nearest vehicle in the car's lane.

**Traceability** — The ability to link a requirement or scenario to the rules that implement it and the tests that check it. See [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md).

**Truth maintenance (logical insertion)** — A Drools feature (`insertLogical`): a fact inserted this way is retracted automatically when the conditions that justified it no longer hold. Used for the fuzzy memberships and conclusions.

**Unification** — In Prolog, the operation that makes two terms identical by binding variables.
- *Prolog engine:* it matches rule patterns against working-memory facts by unification.
- *Drools:* variables are bound by field pattern matching (see *pattern matching*).

**Vehicle state** — The car's current condition. In the world it is `EgoVehicleState`: speed, position, lane, lateral position, lane-change state, driving state, indicator. In the rules it is `VehicleStateFact` (Drools) or the `vehicle_input` of each Prolog request: a copy updated every tick, together with the road markings and lane-safety flags.

**Veto** — The arbitration step that cancels a lane change when the speed decision is a higher-priority braking action for a mandatory stop or an emergency.

**Why / why-not explanation** — "Why" shows the reason, rule and evidence of each decision. "Why not" shows why the other proposals did not decide (their outcomes) and, for lane changes, which sub-goal of `safeToChangeLane` fails on each side.

**Working memory** — The set of facts (and, in Drools, events) currently held by the engine, shown in the interface (`WorkingMemoryInspector` for Drools; the `facts` of each reply for Prolog).

**World mutation** — A change to the simulated world requested by the operator through the API (add or remove an object or road user, paint a map cell, change the weather). Mutations are applied at the start of the next tick.
