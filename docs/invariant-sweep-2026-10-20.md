# Invariant sweep of the driving expert system (2026-10-20)

A probe (`src/test/java/.../simulation/InvariantSweepProbeTest.java`, run by hand with
`-Dprobe.sweep=true`) drives a large set of worlds on **both engines** (Drools and Prolog)
and checks, every 100 ms tick, properties a driving expert system must never break. The
goal is to find behaviour that cannot be defended in front of someone who does not know the
rules: driving into something, passing on the right, running a red light or a stop sign,
speeding, crossing a continuous line, moving into an occupied lane, standing still for no
reason, and the two engines deciding differently from the same facts.

## Worlds driven

568 worlds per engine, 60 s each (90 s for the library):

| Group | Worlds | Contents |
|---|---|---|
| Library | 14 | S1–S14 as shipped |
| Hazards | 198 | ego lane × 50/90 km/h × barrier/pothole/obstacle × own lane at 150/60/30 m, wall across the road at 150/50 m, own lane + neighbours, staggered own/escape lane, mixed types, medium pothole, series of three; hazards appearing mid-manoeuvre in the lane being entered; hazards appearing 45 m ahead after 2 s; continuous lines |
| Traffic | 128 | ego lane × 60/100 km/h × car in each lane × 0/30/60 km/h × 40/100 m; slow car ahead plus a car in the left lane at −10/10/40/100 m and 0–100 km/h; three slow lanes; cars appearing 25–30 m ahead after 2 s; a car appearing in the lane being entered |
| Signs | 60 | red/yellow light, stop sign, 30/50 limit at 60/120/200 m × 50/90/120 km/h; cycling green; signs appearing 40 m ahead; stop sign in another lane; limit then light; light then stop; two limits |
| Pedestrians | 45 | standing/walking in own lane at 90 and 40 m, walking in each other lane, appearing 30 m ahead, with crosswalk, with a car, two pedestrians |
| Combined | 16 | hazard + car in the escape lane, light/stop + slow car, pothole + pedestrian, car + limit, car + barrier in the next lane, rain and continuous-line variants |
| Random | 120 | seeded (42): lane, 40–120 km/h, 0–3 elements of any type at 20–250 m, 0–2 cars, a pedestrian in a third of them, rain in a quarter, continuous lines in a sixth |

## Invariants checked every tick

| Check | Meaning |
|---|---|
| `HIT_OBSTACLE` / `HIT_BARRIER` | the ego is in the map cell of an obstacle or closed barrier in its physical lane |
| `POTHOLE_FAST` | over a pothole above 31.5 km/h (policy: 30) |
| `VEHICLE_COLLISION` | another vehicle within 4.5 m along the road and 2 m across |
| `PEDESTRIAN_HIT` | a pedestrian on the roadway within 2 m and 1.75 m across |
| `PASS_ON_RIGHT` | the ego passes a vehicle while to the right of its lane (Art. 36 CE) |
| `RED_LIGHT_RUN` | a red light crossed (`info:YELLOW_CROSS` for yellow) |
| `STOP_SIGN_RUN` | a stop sign of the ego's lane crossed without a standstill in the last 25 m |
| `SPEED_OVER_LIMIT` | more than 2 km/h over a passed limit for 1 s, or over 120 anywhere |
| `CONTINUOUS_LINE_CROSSED` | a lane change started across a continuous marking |
| `UNSAFE_LANE_ENTRY` / `ENTRY_INTO_HAZARD` | a lane change started with a vehicle within ±10 m, or a blocking hazard within 25 m, in the target lane |
| `STUCK_NO_REASON` | at rest 15 s with no light, stop sign, hazard, stopped vehicle or pedestrian holding it |
| `DIVERGENCE` | Drools and Prolog differ in speed or lane action on the same tick |
| `OFF_ROAD`, `ENGINE_ERROR` | lateral position outside the road; a backend exception |

## Findings before the fixes

Drools: 62 of 568 worlds violated something; Prolog: 77 (the same 62 plus 15 divergences).
No obstacle, barrier or pothole was ever hit, no red light run, no speeding, no continuous
line crossed, no lane change into an occupied lane.

| Kind | Worlds | Verdict |
|---|---|---|
| `PEDESTRIAN_HIT` | 21 | **Defect.** At 90 km/h and above a pedestrian seen 80–90 m ahead was reached at 30–50 km/h: the approach brakes along a comfortable curve and the mandatory stop is executed at the comfortable 2.8 m/s², and nothing escalated before the 6 m emergency distance. At 50–60 km/h every pedestrian case passed. (One hit was the pedestrian walking into a stopped ego: not a violation.) |
| `STOP_SIGN_RUN` | 2 | **Defect.** The camera reported the stop sign of the *committed* lane. A vehicle changing into a lane with a stop sign only saw it once the manoeuvre had settled, too late to stop, and at 120 km/h drove through it. |
| `DIVERGENCE` | 15 | **Defect (equivalence).** The derived `SafeLaneSide` fact lagged one tick behind the `safeToChangeLane` query it was computed from, so Drools' "no safe side" barrier stop held the vehicle (a rung-60 stop that also vetoes the lane change) for 0.3–2.2 s where Prolog, proving the goal live, changed lane. |
| left-lane entry | 1 | **Defect.** "Overtake slow front vehicle via left lane" did not prove `roomToFollow`: with a stopped car 100 m ahead in the left lane the ego moved out at 89 km/h and had to emergency-brake behind it (no collision, 13 m). |
| `PASS_ON_RIGHT` | 34 | 29 are a **stationary** vehicle (0 km/h) in a lane to the left: the Art. 36 rules only apply to vehicles moving at 1.4 m/s or more, by design (a stopped vehicle is not being overtaken). The other 5 are cars placed 10–20 m ahead with 40–60 km/h of closing speed in the first second: physically unavoidable. |
| `VEHICLE_COLLISION` | 8 | 6 are a stopped car 30–40 m ahead at 90–100 km/h (stopping needs 48 m at 8.5 m/s²), 1 a car appearing 13 m ahead at 38 km/h slower during a lane change (needs 6.6 m/s² from the first tick): physics-limited placements. 1 is a 90 km/h car **behind** the ego driving into it: the other vehicles brake for pedestrians but not for the ego (simulator gap, not a rule). |
| `STUCK_NO_REASON` | 1 | boxed in by two stopped cars and a crossing: defensible. |

## Fixes

1. `emergency.drl` / `driving_rules.pl`: "Emergency brake for pedestrian when comfortable
   braking no longer stops in time" (879): EMERGENCY_BRAKE once v²/2(d−6) exceeds the
   comfortable deceleration ×1.1, the escalation the obstacle and barrier rules already make.
2. `WorldState.nearestStopSign`: the sign of the **path lane** (target lane while a change
   runs), as the hazards already were.
3. `front-vehicle.drl` / `driving_rules.pl`: the overtake rule proves `roomToFollow(LEFT)`.
4. `01-state-updates.drl`: "Derive the sides that are safe to change lane to" spells out the
   four sub-goals instead of calling the query, so the logical fact follows the facts in the
   same tick (the query stays the explained goal for the avoidance rules and the why-not).

Tests: `PedestrianAtSpeedE2ETest`, `StopSignInLaneBeingEnteredE2ETest`,
`OvertakeWaitsForRoomInLeftLaneE2ETest` (both engines) and unit tests in
`DrivingExpertSystemRulesTest` / `PrologRulesTest`.

## Findings after the fixes

Same 568 worlds, both engines: 45 worlds with a finding, **identical on Drools and Prolog**
(no divergence left), no stop sign run, no pedestrian reached that could have been stopped for.

| Kind | Worlds | What is left |
|---|---|---|
| `PASS_ON_RIGHT` | 32 | stationary vehicles in a lane to the left (by design) and cars placed 10–20 m ahead with 40–60 km/h of closing speed in the first second (physics) |
| `VEHICLE_COLLISION` | 7 | a stopped car 30–40 m ahead at 90–100 km/h (6), a car appearing 13 m ahead in the lane being entered (1): physics |
| `PEDESTRIAN_HIT` | 6 | pedestrians placed 25–30 m ahead at 90 km/h (3), 47 m at 100 km/h, 67 m at 120 km/h, 28 m at 90 km/h: hard braking from the first tick, still physically unavoidable |
| `STUCK_NO_REASON` | 1 | boxed in by two stopped cars (defensible) |

## Open points (not fixed here)

- Other vehicles do not brake for the ego (they do for pedestrians): a faster car behind
  drives into a stopped or slowed ego. A simulator gap, not a rule.
- The stop-sign approach rides the comfortable braking curve with no margin, so a camera
  reading up to 200 ms old tips it into single-tick hard-braking bursts during the approach
  (`StopSignInLaneBeingEnteredE2ETest` shows it). The same hysteresis the red-light and the
  pedestrian approaches got would settle it.
- The Art. 36 rule's hard-braking threshold compares the ground gained while braking with
  the gap **plus** 5 m: the ego may end up to 5 m past the point of a vehicle it closes in on
  during a lane change. Worth revisiting with a vehicle length in the world model.
