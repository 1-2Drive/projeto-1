package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.RoadMarkingType;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Invariant sweep, run by hand ({@code mvn -Dprobe.sweep=true -Dtest=InvariantSweepProbeTest#sweep test},
 * about 15 minutes with SWI-Prolog on the path): drives the scenario library, a matrix of
 * hazards, traffic, signs and pedestrians and 120 seeded random worlds on both engines and
 * checks, every tick, what a driving expert system must never do — hit something, pass on
 * the right, run a red light or a stop sign, speed, cross a continuous line, enter an
 * occupied lane, stand still for no reason — and that Drools and Prolog decide alike.
 * Results go to {@code target/invariant-sweep/} (override with {@code -Dprobe.out=...}):
 * one summary per engine, with the violations under each world, and the decision trace of
 * every violating world. Findings and verdicts: docs/invariant-sweep-2026-10-20.md.
 */
@EnabledIfSystemProperty(named = "probe.sweep", matches = "true")
class InvariantSweepProbeTest {
    private static final Path OUT = Path.of(System.getProperty("probe.out", "target/invariant-sweep"));

    enum When { START, AT_2S, ON_CHANGING }

    record El(ScenarioRoadElementType type, Lane lane, double ahead, When when, HazardSeverity sev,
              TrafficLightState light, Integer limit, boolean open) {}

    record Act(ScenarioActorType type, Lane lane, double ahead, double kmh, When when) {}

    record Case(String name, boolean demo, Lane egoLane, double kmh, boolean rain,
                RoadMarkingType leftMark, RoadMarkingType rightMark,
                List<El> els, List<Act> acts, int ticks, ScenarioDefinition library) {}

    record Violation(String kind, double t, String detail) {}

    // ---- builders -------------------------------------------------------------------------

    private static El barrier(Lane l, double d, When w) { return new El(ScenarioRoadElementType.BARRIER, l, d, w, HazardSeverity.HIGH, null, null, false); }
    private static El pothole(Lane l, double d, When w) { return new El(ScenarioRoadElementType.POTHOLE, l, d, w, HazardSeverity.HIGH, null, null, true); }
    private static El potholeMed(Lane l, double d, When w) { return new El(ScenarioRoadElementType.POTHOLE, l, d, w, HazardSeverity.MEDIUM, null, null, true); }
    private static El obstacle(Lane l, double d, When w) { return new El(ScenarioRoadElementType.STATIC_OBSTACLE, l, d, w, HazardSeverity.HIGH, null, null, true); }
    private static El light(Lane l, double d, TrafficLightState s, When w) { return new El(ScenarioRoadElementType.TRAFFIC_LIGHT, l, d, w, null, s, null, true); }
    private static El stop(Lane l, double d, When w) { return new El(ScenarioRoadElementType.STOP_SIGN, l, d, w, null, null, null, true); }
    private static El limit(Lane l, double d, int kmh, When w) { return new El(ScenarioRoadElementType.SPEED_LIMIT, l, d, w, null, null, kmh, true); }
    private static El crosswalk(Lane l, double d, When w) { return new El(ScenarioRoadElementType.CROSSWALK, l, d, w, null, null, null, true); }
    private static Act car(Lane l, double d, double kmh, When w) { return new Act(ScenarioActorType.FRONT_VEHICLE, l, d, kmh, w); }
    private static Act ped(Lane l, double d, double kmh, When w) { return new Act(ScenarioActorType.PEDESTRIAN, l, d, kmh, w); }

    private static Case c(String name, boolean demo, Lane lane, double kmh, List<El> els, List<Act> acts) {
        return new Case(name, demo, lane, kmh, false, RoadMarkingType.DASHED, RoadMarkingType.DASHED, els, acts, 600, null);
    }

    private static Case rain(Case base) {
        return new Case(base.name() + " +rain", base.demo(), base.egoLane(), base.kmh(), true, base.leftMark(), base.rightMark(), base.els(), base.acts(), base.ticks(), null);
    }

    private static Case continuous(Case base) {
        return new Case(base.name() + " +contLine", base.demo(), base.egoLane(), base.kmh(), base.rain(), RoadMarkingType.CONTINUOUS, RoadMarkingType.CONTINUOUS, base.els(), base.acts(), base.ticks(), null);
    }

    private static final List<El> NOE = List.of();
    private static final List<Act> NOA = List.of();
    private static final Lane[] LANES = {Lane.LEFT, Lane.CENTER, Lane.RIGHT};

    static List<Case> cases() {
        List<Case> cs = new ArrayList<>();

        // A. scenario library
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        for (ScenarioDefinition s : library.getScenarios()) {
            cs.add(new Case("LIB " + s.getId(), false, null, 0, false, null, null, NOE, NOA, 900, s));
        }

        // B. hazard matrix: ego lane x speed x hazard x placement (demo flag keeps the ego in its lane)
        for (Lane lane : LANES) {
            for (double kmh : new double[] {50, 90}) {
                for (String hz : new String[] {"barrier", "pothole", "obstacle"}) {
                    for (double d : new double[] {150, 60, 30}) {
                        cs.add(c(String.format("HZ ego%s%.0f %s %s@%.0f", lane.name().charAt(0), kmh, hz, lane.name().charAt(0), d),
                            lane != Lane.RIGHT, lane, kmh, List.of(hazard(hz, lane, d)), NOA));
                    }
                    // wall across all lanes
                    cs.add(c(String.format("HZ ego%s%.0f %s wall@150", lane.name().charAt(0), kmh, hz), lane != Lane.RIGHT, lane, kmh,
                        List.of(hazard(hz, Lane.LEFT, 150), hazard(hz, Lane.CENTER, 150), hazard(hz, Lane.RIGHT, 150)), NOA));
                    cs.add(c(String.format("HZ ego%s%.0f %s wall@50", lane.name().charAt(0), kmh, hz), lane != Lane.RIGHT, lane, kmh,
                        List.of(hazard(hz, Lane.LEFT, 50), hazard(hz, Lane.CENTER, 50), hazard(hz, Lane.RIGHT, 50)), NOA));
                    // own lane + both neighbours at different distances (forces a choice)
                    cs.add(c(String.format("HZ ego%s%.0f %s own@120 others@80", lane.name().charAt(0), kmh, hz), lane != Lane.RIGHT, lane, kmh,
                        List.of(hazard(hz, lane, 120), hazard(hz, lane.left() == lane ? lane.right() : lane.left(), 80),
                            hazard(hz, lane.right() == lane ? lane.left() : lane.right(), 80)), NOA));
                    // staggered: own lane at 100, escape lane at 140 (escape then hazard)
                    Lane esc = lane == Lane.RIGHT ? Lane.CENTER : lane.right();
                    cs.add(c(String.format("HZ ego%s%.0f %s own@100 escape%s@140", lane.name().charAt(0), kmh, hz, esc.name().charAt(0)), lane != Lane.RIGHT, lane, kmh,
                        List.of(hazard(hz, lane, 100), hazard(hz, esc, 140)), NOA));
                }
                // mixed types in the three lanes
                cs.add(c(String.format("HZ ego%s%.0f mixed L=barrier C=obstacle R=pothole@120", lane.name().charAt(0), kmh), lane != Lane.RIGHT, lane, kmh,
                    List.of(barrier(Lane.LEFT, 120, When.START), obstacle(Lane.CENTER, 120, When.START), pothole(Lane.RIGHT, 120, When.START)), NOA));
                cs.add(c(String.format("HZ ego%s%.0f medium pothole own@100", lane.name().charAt(0), kmh), lane != Lane.RIGHT, lane, kmh,
                    List.of(potholeMed(lane, 100, When.START)), NOA));
                // series of hazards in own lane
                cs.add(c(String.format("HZ ego%s%.0f obstacle own@100 + barrier own@160 + pothole own@220", lane.name().charAt(0), kmh), lane != Lane.RIGHT, lane, kmh,
                    List.of(obstacle(lane, 100, When.START), barrier(lane, 160, When.START), pothole(lane, 220, When.START)), NOA));
            }
        }
        // mid-manoeuvre placements: avoidance from RIGHT to CENTER, then a hazard appears in CENTER
        for (String hz : new String[] {"barrier", "pothole", "obstacle"}) {
            for (double d : new double[] {40, 70}) {
                cs.add(c(String.format("HZ egoR60 barrier R@150, on changing %s C@%.0f", hz, d), false, Lane.RIGHT, 60,
                    List.of(barrier(Lane.RIGHT, 150, When.START), hazardWhen(hz, Lane.CENTER, d, When.ON_CHANGING)), NOA));
            }
        }
        // hazard appearing late (2 s in) in own lane at short range
        for (Lane lane : LANES) {
            for (String hz : new String[] {"barrier", "obstacle"}) {
                cs.add(c(String.format("HZ ego%s70 %s appears@45 at 2s", lane.name().charAt(0), hz), lane != Lane.RIGHT, lane, 70,
                    List.of(hazardWhen(hz, lane, 45, When.AT_2S)), NOA));
            }
        }
        // continuous line variants: hazard in own lane, no way round
        for (Lane lane : LANES) {
            cs.add(continuous(c(String.format("HZ ego%s60 obstacle own@120", lane.name().charAt(0)), lane != Lane.RIGHT, lane, 60, List.of(obstacle(lane, 120, When.START)), NOA)));
            cs.add(continuous(c(String.format("HZ ego%s60 barrier own@120", lane.name().charAt(0)), lane != Lane.RIGHT, lane, 60, List.of(barrier(lane, 120, When.START)), NOA)));
        }

        // C. traffic: ego lane x speed x car lane x car speed x distance
        for (Lane lane : LANES) {
            for (double kmh : new double[] {60, 100}) {
                for (Lane carLane : LANES) {
                    for (double carKmh : new double[] {0, 30, 60}) {
                        for (double d : new double[] {40, 100}) {
                            cs.add(c(String.format("TR ego%s%.0f car%s%.0f@%.0f", lane.name().charAt(0), kmh, carLane.name().charAt(0), carKmh, d),
                                false, lane, kmh, NOE, List.of(car(carLane, d, carKmh, When.START))));
                        }
                    }
                }
            }
        }
        // two cars: slow ahead + car in the left lane (boxed in / overtaking gap)
        for (double leftKmh : new double[] {0, 30, 60, 100}) {
            for (double leftD : new double[] {-10, 10, 40, 100}) {
                cs.add(c(String.format("TR egoR90 carR30@60 carL%.0f@%.0f", leftKmh, leftD), false, Lane.RIGHT, 90, NOE,
                    List.of(car(Lane.RIGHT, 60, 30, When.START), car(Lane.LEFT, leftD, leftKmh, When.START))));
                cs.add(c(String.format("TR egoC90 carC30@60 carL%.0f@%.0f", leftKmh, leftD), false, Lane.CENTER, 90, NOE,
                    List.of(car(Lane.CENTER, 60, 30, When.START), car(Lane.LEFT, leftD, leftKmh, When.START))));
            }
        }
        // three lanes with slow traffic
        cs.add(c("TR egoR100 cars L40@80 C40@60 R40@40", false, Lane.RIGHT, 100, NOE,
            List.of(car(Lane.LEFT, 80, 40, When.START), car(Lane.CENTER, 60, 40, When.START), car(Lane.RIGHT, 40, 40, When.START))));
        cs.add(c("TR egoC100 cars L20@50 C40@70 R40@30", false, Lane.CENTER, 100, NOE,
            List.of(car(Lane.LEFT, 50, 20, When.START), car(Lane.CENTER, 70, 40, When.START), car(Lane.RIGHT, 30, 40, When.START))));
        // car appears close at 2 s (cut-in like)
        for (Lane lane : LANES) {
            cs.add(c(String.format("TR ego%s90 car own 0km/h appears@30 at 2s", lane.name().charAt(0)), false, lane, 90, NOE, List.of(car(lane, 30, 0, When.AT_2S))));
            cs.add(c(String.format("TR ego%s90 car own 30km/h appears@25 at 2s", lane.name().charAt(0)), false, lane, 90, NOE, List.of(car(lane, 25, 30, When.AT_2S))));
        }
        // car in the lane being entered during an overtake
        cs.add(c("TR egoR90 carR30@60, on changing carC50@15", false, Lane.RIGHT, 90, NOE,
            List.of(car(Lane.RIGHT, 60, 30, When.START), car(Lane.CENTER, 15, 50, When.ON_CHANGING))));
        cs.add(c("TR egoR90 carR30@60, on changing carC0@25", false, Lane.RIGHT, 90, NOE,
            List.of(car(Lane.RIGHT, 60, 30, When.START), car(Lane.CENTER, 25, 0, When.ON_CHANGING))));

        // D. signs and lights
        for (double kmh : new double[] {50, 90, 120}) {
            for (double d : new double[] {60, 120, 200}) {
                cs.add(c(String.format("SG egoR%.0f RED@%.0f", kmh, d), false, Lane.RIGHT, kmh, List.of(light(Lane.RIGHT, d, TrafficLightState.RED, When.START)), NOA));
                cs.add(c(String.format("SG egoR%.0f YELLOW@%.0f", kmh, d), false, Lane.RIGHT, kmh, List.of(light(Lane.RIGHT, d, TrafficLightState.YELLOW, When.START)), NOA));
                cs.add(c(String.format("SG egoR%.0f STOP@%.0f", kmh, d), false, Lane.RIGHT, kmh, List.of(stop(Lane.RIGHT, d, When.START)), NOA));
                cs.add(c(String.format("SG egoR%.0f LIMIT30@%.0f", kmh, d), false, Lane.RIGHT, kmh, List.of(limit(Lane.RIGHT, d, 30, When.START)), NOA));
                cs.add(c(String.format("SG egoR%.0f LIMIT50@%.0f", kmh, d), false, Lane.RIGHT, kmh, List.of(limit(Lane.RIGHT, d, 50, When.START)), NOA));
            }
            cs.add(c(String.format("SG egoR%.0f GREEN@100 (cycling)", kmh), false, Lane.RIGHT, kmh, List.of(light(Lane.RIGHT, 100, TrafficLightState.GREEN, When.START)), NOA));
            cs.add(c(String.format("SG egoR%.0f RED appears@40 at 2s", kmh), false, Lane.RIGHT, kmh, List.of(light(Lane.RIGHT, 40, TrafficLightState.RED, When.AT_2S)), NOA));
            cs.add(c(String.format("SG egoR%.0f STOP appears@40 at 2s", kmh), false, Lane.RIGHT, kmh, List.of(stop(Lane.RIGHT, 40, When.AT_2S)), NOA));
            cs.add(c(String.format("SG egoC%.0f STOP in R lane@100 (not ours)", kmh), true, Lane.CENTER, kmh, List.of(stop(Lane.RIGHT, 100, When.START)), NOA));
            cs.add(c(String.format("SG egoR%.0f LIMIT50@80 then RED@160", kmh), false, Lane.RIGHT, kmh,
                List.of(limit(Lane.RIGHT, 80, 50, When.START), light(Lane.RIGHT, 160, TrafficLightState.RED, When.START)), NOA));
            cs.add(c(String.format("SG egoR%.0f RED@100 then STOP@160", kmh), false, Lane.RIGHT, kmh,
                List.of(light(Lane.RIGHT, 100, TrafficLightState.RED, When.START), stop(Lane.RIGHT, 160, When.START)), NOA));
            cs.add(c(String.format("SG egoR%.0f LIMIT30@100 LIMIT90@200", kmh), false, Lane.RIGHT, kmh,
                List.of(limit(Lane.RIGHT, 100, 30, When.START), limit(Lane.RIGHT, 200, 90, When.START)), NOA));
        }

        // E. pedestrians
        for (Lane lane : LANES) {
            for (double kmh : new double[] {50, 90}) {
                cs.add(c(String.format("PD ego%s%.0f ped own standing@90", lane.name().charAt(0), kmh), lane != Lane.RIGHT, lane, kmh, NOE, List.of(ped(lane, 90, 0, When.START))));
                cs.add(c(String.format("PD ego%s%.0f ped own walking@90", lane.name().charAt(0), kmh), lane != Lane.RIGHT, lane, kmh, NOE, List.of(ped(lane, 90, 3, When.START))));
                cs.add(c(String.format("PD ego%s%.0f ped own walking@40", lane.name().charAt(0), kmh), lane != Lane.RIGHT, lane, kmh, NOE, List.of(ped(lane, 40, 3, When.START))));
                for (Lane pl : LANES) {
                    if (pl == lane) continue;
                    cs.add(c(String.format("PD ego%s%.0f ped %s walking@80", lane.name().charAt(0), kmh, pl.name().charAt(0)), lane != Lane.RIGHT, lane, kmh, NOE, List.of(ped(pl, 80, 4, When.START))));
                }
                cs.add(c(String.format("PD ego%s%.0f ped own appears@30 at 2s", lane.name().charAt(0), kmh), lane != Lane.RIGHT, lane, kmh, NOE, List.of(ped(lane, 30, 3, When.AT_2S))));
            }
        }
        cs.add(c("PD egoR60 crosswalk@100 + ped R walking@100", false, Lane.RIGHT, 60, List.of(crosswalk(Lane.RIGHT, 100, When.START)), List.of(ped(Lane.RIGHT, 100, 3, When.START))));
        cs.add(c("PD egoR60 ped R@80 + car R30@40", false, Lane.RIGHT, 60, NOE, List.of(ped(Lane.RIGHT, 80, 3, When.START), car(Lane.RIGHT, 40, 30, When.START))));
        cs.add(c("PD egoR60 two peds L@70 R@110", false, Lane.RIGHT, 60, NOE, List.of(ped(Lane.LEFT, 70, 4, When.START), ped(Lane.RIGHT, 110, 3, When.START))));

        // F. combined
        cs.add(c("CB egoR90 barrier R@100 + carC30@20", false, Lane.RIGHT, 90, List.of(barrier(Lane.RIGHT, 100, When.START)), List.of(car(Lane.CENTER, 20, 30, When.START))));
        cs.add(c("CB egoR90 obstacle R@100 + carC0@60", false, Lane.RIGHT, 90, List.of(obstacle(Lane.RIGHT, 100, When.START)), List.of(car(Lane.CENTER, 60, 0, When.START))));
        cs.add(c("CB egoC90 obstacle C@100 + carL30@30 + carR30@30", true, Lane.CENTER, 90, List.of(obstacle(Lane.CENTER, 100, When.START)),
            List.of(car(Lane.LEFT, 30, 30, When.START), car(Lane.RIGHT, 30, 30, When.START))));
        cs.add(c("CB egoR90 RED@120 + carR30@50", false, Lane.RIGHT, 90, List.of(light(Lane.RIGHT, 120, TrafficLightState.RED, When.START)), List.of(car(Lane.RIGHT, 50, 30, When.START))));
        cs.add(c("CB egoR90 RED@120 + carR0@80", false, Lane.RIGHT, 90, List.of(light(Lane.RIGHT, 120, TrafficLightState.RED, When.START)), List.of(car(Lane.RIGHT, 80, 0, When.START))));
        cs.add(c("CB egoR90 STOP@120 + carR20@60", false, Lane.RIGHT, 90, List.of(stop(Lane.RIGHT, 120, When.START)), List.of(car(Lane.RIGHT, 60, 20, When.START))));
        cs.add(c("CB egoR60 pothole R@100 + ped R@150", false, Lane.RIGHT, 60, List.of(pothole(Lane.RIGHT, 100, When.START)), List.of(ped(Lane.RIGHT, 150, 3, When.START))));
        cs.add(c("CB egoR90 carR30@60 + LIMIT50@120", false, Lane.RIGHT, 90, List.of(limit(Lane.RIGHT, 120, 50, When.START)), List.of(car(Lane.RIGHT, 60, 30, When.START))));
        cs.add(c("CB egoR90 carR30@60 + barrier C@100", false, Lane.RIGHT, 90, List.of(barrier(Lane.CENTER, 100, When.START)), List.of(car(Lane.RIGHT, 60, 30, When.START))));
        cs.add(c("CB egoR90 carR30@60 + obstacle C@140 + barrier L@140", false, Lane.RIGHT, 90,
            List.of(obstacle(Lane.CENTER, 140, When.START), barrier(Lane.LEFT, 140, When.START)), List.of(car(Lane.RIGHT, 60, 30, When.START))));
        cs.add(c("CB egoC90 carL30@40 + obstacle C@120", true, Lane.CENTER, 90, List.of(obstacle(Lane.CENTER, 120, When.START)), List.of(car(Lane.LEFT, 40, 30, When.START))));
        cs.add(rain(c("CB egoR90 carR30@60", false, Lane.RIGHT, 90, NOE, List.of(car(Lane.RIGHT, 60, 30, When.START)))));
        cs.add(rain(c("CB egoR90 RED@120", false, Lane.RIGHT, 90, List.of(light(Lane.RIGHT, 120, TrafficLightState.RED, When.START)), NOA)));
        cs.add(rain(c("CB egoR90 obstacle R@100", false, Lane.RIGHT, 90, List.of(obstacle(Lane.RIGHT, 100, When.START)), NOA)));
        cs.add(rain(c("CB egoR90 ped R@90", false, Lane.RIGHT, 90, NOE, List.of(ped(Lane.RIGHT, 90, 3, When.START)))));
        cs.add(continuous(c("CB egoR90 carR20@60", false, Lane.RIGHT, 90, NOE, List.of(car(Lane.RIGHT, 60, 20, When.START)))));
        cs.add(continuous(c("CB egoC90 carC20@60", true, Lane.CENTER, 90, NOE, List.of(car(Lane.CENTER, 60, 20, When.START)))));

        // G. seeded random worlds
        Random rnd = new Random(42);
        for (int i = 0; i < 120; i++) {
            Lane lane = LANES[rnd.nextInt(3)];
            double kmh = 40 + rnd.nextInt(9) * 10;
            List<El> els = new ArrayList<>();
            List<Act> acts = new ArrayList<>();
            int ne = rnd.nextInt(4);
            for (int k = 0; k < ne; k++) {
                int kind = rnd.nextInt(7);
                Lane l = LANES[rnd.nextInt(3)];
                double d = 20 + rnd.nextInt(24) * 10;
                When w = rnd.nextInt(5) == 0 ? When.AT_2S : When.START;
                els.add(switch (kind) {
                    case 0 -> barrier(l, d, w);
                    case 1 -> rnd.nextBoolean() ? pothole(l, d, w) : potholeMed(l, d, w);
                    case 2 -> obstacle(l, d, w);
                    case 3 -> light(l, d, TrafficLightState.values()[rnd.nextInt(3)], w);
                    case 4 -> stop(l, d, w);
                    case 5 -> limit(l, d, new int[] {30, 50, 70, 90}[rnd.nextInt(4)], w);
                    default -> crosswalk(l, d, w);
                });
            }
            int na = rnd.nextInt(3);
            for (int k = 0; k < na; k++) {
                Lane l = LANES[rnd.nextInt(3)];
                double d = 15 + rnd.nextInt(14) * 10;
                acts.add(car(l, d, rnd.nextInt(8) * 15, rnd.nextInt(5) == 0 ? When.AT_2S : When.START));
            }
            if (rnd.nextInt(3) == 0) {
                acts.add(ped(LANES[rnd.nextInt(3)], 30 + rnd.nextInt(15) * 10, rnd.nextInt(3) == 0 ? 0 : 3, When.START));
            }
            boolean rainy = rnd.nextInt(4) == 0;
            boolean cont = rnd.nextInt(6) == 0;
            String name = String.format("RND%03d ego%s%.0f", i, lane.name().charAt(0), kmh);
            Case base = c(name, rnd.nextBoolean(), lane, kmh, els, acts);
            if (rainy) base = rain(base);
            if (cont) base = continuous(base);
            cs.add(base);
        }
        return cs;
    }

    private static El hazard(String hz, Lane lane, double d) { return hazardWhen(hz, lane, d, When.START); }

    private static El hazardWhen(String hz, Lane lane, double d, When w) {
        return switch (hz) {
            case "barrier" -> barrier(lane, d, w);
            case "pothole" -> pothole(lane, d, w);
            default -> obstacle(lane, d, w);
        };
    }

    private static ScenarioRoadElementDefinition element(El el) {
        ScenarioRoadElementDefinition d = new ScenarioRoadElementDefinition();
        d.setType(el.type());
        d.setLane(el.lane());
        d.setPositionMeters(el.ahead());
        if (el.sev() != null) d.setSeverity(el.sev());
        if (el.light() != null) d.setTrafficLightState(el.light());
        if (el.limit() != null) d.setSpeedLimitKmh(el.limit());
        if (el.type() == ScenarioRoadElementType.BARRIER) d.setBarrierOpen(el.open());
        return d;
    }

    private static ScenarioActorDefinition actor(Act a) {
        ScenarioActorDefinition d = new ScenarioActorDefinition();
        d.setType(a.type());
        d.setLane(a.lane());
        d.setPositionMeters(a.ahead());
        d.setSpeedKmh(a.kmh());
        return d;
    }

    // ---- run ------------------------------------------------------------------------------

    private static final Map<String, List<String>> DROOLS_SEQUENCES = new HashMap<>();

    @Test
    void sweep() throws IOException {
        Files.createDirectories(OUT);
        List<Case> cases = cases();
        for (ExpertBackendType backend : ExpertBackendType.values()) {
            StringBuilder summary = new StringBuilder();
            StringBuilder traces = new StringBuilder();
            Map<String, Integer> kinds = new TreeMap<>();
            int violating = 0;
            int skipped = 0;
            long t0 = System.currentTimeMillis();
            for (Case cs : cases) {
                Result r;
                try {
                    r = run(cs, backend);
                } catch (org.opentest4j.TestAbortedException e) {
                    skipped++;
                    continue;
                } catch (RuntimeException e) {
                    r = new Result(List.of(new Violation("EXCEPTION", 0, e.toString())), "", "", List.of());
                }
                if (backend == ExpertBackendType.DROOLS) {
                    DROOLS_SEQUENCES.put(cs.name(), r.sequence());
                } else if (DROOLS_SEQUENCES.containsKey(cs.name())) {
                    List<String> a = DROOLS_SEQUENCES.get(cs.name());
                    List<String> b = r.sequence();
                    int diff = 0;
                    int first = -1;
                    for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                        if (!a.get(i).equals(b.get(i))) {
                            diff++;
                            if (first < 0) first = i;
                        }
                    }
                    if (diff > 0) {
                        r.violations().add(new Violation("DIVERGENCE", first / 10.0,
                            String.format("%d ticks differ; first drools=%s prolog=%s", diff, a.get(first), b.get(first))));
                    }
                }
                List<Violation> hard = r.violations().stream().filter(v -> !v.kind().startsWith("info:")).toList();
                summary.append(String.format("%-60s %s%n", cs.name(), r.metrics()));
                if (!hard.isEmpty()) {
                    violating++;
                    for (Violation v : hard) {
                        kinds.merge(v.kind(), 1, Integer::sum);
                        summary.append(String.format("    !! %-22s t=%5.1f %s%n", v.kind(), v.t(), v.detail()));
                    }
                    traces.append("=== ").append(cs.name()).append('\n').append(r.trace()).append('\n');
                }
                for (Violation v : r.violations()) {
                    if (v.kind().startsWith("info:")) {
                        summary.append(String.format("       %-22s t=%5.1f %s%n", v.kind(), v.t(), v.detail()));
                    }
                }
            }
            String head = String.format("ENGINE %s  cases=%d violating=%d skipped=%d kinds=%s  (%.0f s)%n%n",
                backend, cases.size(), violating, skipped, kinds, (System.currentTimeMillis() - t0) / 1000.0);
            Files.writeString(OUT.resolve("invariants-" + backend.name().toLowerCase() + ".log"), head + summary);
            Files.writeString(OUT.resolve("invariants-traces-" + backend.name().toLowerCase() + ".log"), traces.toString());
            System.out.println(head);
        }
    }

    /** Tick-by-tick facts of one case on both engines, for the divergence analysis. */
    @Test
    void diagnose() throws IOException {
        String wanted = System.getProperty("probe.case", "CB egoR90 carR30@60 + barrier C@100");
        double from = Double.parseDouble(System.getProperty("probe.from", "2.6"));
        double to = Double.parseDouble(System.getProperty("probe.to", "5.0"));
        Case cs = cases().stream().filter(c -> c.name().equals(wanted)).findFirst().orElseThrow();
        StringBuilder out = new StringBuilder();
        for (ExpertBackendType backend : ExpertBackendType.values()) {
            out.append("##### ").append(backend).append('\n');
            ScenarioDefinition s = new ScenarioDefinition();
            s.setId(cs.demo() ? "S12_PRESENTATION_DEMO" : "PROBE");
            s.setName(cs.name());
            s.setInitialSpeedKmh(cs.kmh());
            s.setInitialLane(cs.egoLane());
            s.setInitialWeather(cs.rain() ? WeatherCondition.RAIN : WeatherCondition.CLEAR);
            s.setLeftMarking(cs.leftMark());
            s.setRightMarking(cs.rightMark());
            try (SimulationEngine engine = BackendTestSupport.engine(s, backend)) {
                engine.start();
                EgoVehicleState ego = engine.getWorldState().getEgo();
                for (int i = 0; i < 5; i++) engine.tick();
                for (El el : cs.els()) if (el.when() == When.START) engine.addRoadElementAhead(element(el));
                for (Act a : cs.acts()) if (a.when() == When.START) engine.addActorAhead(actor(a));
                for (int i = 0; i < 120; i++) {
                    engine.tick();
                    double t = i / 10.0;
                    if (t < from || t > to) continue;
                    var d = engine.getCurrentDecision();
                    out.append(String.format("t=%.1f pos=%.1f lane=%s/%s lcs=%s kmh=%.1f settled=%d %s(%s)/%s(%s)%n", t, ego.getPositionMeters(),
                        ego.getLane(), Lane.fromLateralPosition(ego.getLateralPositionMeters()), ego.getLaneChangeState(), ego.getSpeedKmh(),
                        ego.getLaneSettledAtMillis(), d.getSpeedAction(), d.getSpeedSourceRule(), d.getLaneAction(), d.getLaneSourceRule()));
                    for (var p : d.getProposals()) {
                        out.append("    proposal ").append(p.getSourceRule()).append(" -> ").append(p.getAction()).append(" @").append(p.getPriority())
                            .append(' ').append(p.getOutcome()).append(' ').append(p.getDetail()).append('\n');
                    }
                    for (var f : engine.getWorkingMemoryFactViews()) {
                        if (f.type().matches("SafeLaneSide|LaneOccupancyState|BarrierStateFact|VehicleStateFact|LaneChangeCheck|LeftLaneVehicleState|RightLaneVehicleState|FrontVehicleState")) {
                            out.append("    ").append(f.type()).append(": ").append(f.value()).append('\n');
                        }
                    }
                }
            } catch (org.opentest4j.TestAbortedException e) {
                out.append("SKIPPED\n");
            }
        }
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("diagnose.log"), out.toString());
    }

    record Result(List<Violation> violations, String metrics, String trace, List<String> sequence) {}

    private static Result run(Case cs, ExpertBackendType backend) {
        ScenarioDefinition s = cs.library();
        if (s == null) {
            s = new ScenarioDefinition();
            s.setId(cs.demo() ? "S12_PRESENTATION_DEMO" : "PROBE");
            s.setName(cs.name());
            s.setInitialSpeedKmh(cs.kmh());
            s.setInitialLane(cs.egoLane());
            s.setInitialWeather(cs.rain() ? WeatherCondition.RAIN : WeatherCondition.CLEAR);
            s.setLeftMarking(cs.leftMark());
            s.setRightMarking(cs.rightMark());
        }
        List<Violation> out = new ArrayList<>();
        List<String> seq = new ArrayList<>();
        StringBuilder trace = new StringBuilder();
        try (SimulationEngine engine = BackendTestSupport.engine(s, backend)) {
            engine.start();
            WorldState world = engine.getWorldState();
            EgoVehicleState ego = world.getEgo();
            RoadMap map = world.getRoadMap();
            if (cs.library() == null) {
                for (int i = 0; i < 5; i++) engine.tick();
                for (El el : cs.els()) if (el.when() == When.START) engine.addRoadElementAhead(element(el));
                for (Act a : cs.acts()) if (a.when() == When.START) engine.addActorAhead(actor(a));
            }

            Map<String, Double> prevVehD = new HashMap<>();
            Map<String, Double> prevElD = new HashMap<>();
            Map<String, Boolean> hitReported = new HashMap<>();
            double currentLimit = Double.POSITIVE_INFINITY;
            int limitSinceTick = -1;
            int overTicks = 0;
            double maxOver = 0;
            double lastRestPos = Double.NaN;
            int restTicks = 0;
            boolean stuckReported = false;
            boolean changingPlaced = false;
            int emergency = 0;
            int rejections = 0;
            double minGap = Double.POSITIVE_INFINITY;
            double potholeMaxKmh = 0;
            int laneChanges = 0;
            int lines = 0;
            DrivingAction prevSpeed = null;
            DrivingAction prevLaneAct = null;
            Lane prevPhysical = Lane.fromLateralPosition(ego.getLateralPositionMeters());
            LaneChangeState prevLcs = ego.getLaneChangeState();
            String error = null;
            int ticks = cs.ticks();

            for (int i = 0; i < ticks; i++) {
                double t = i / 10.0;
                if (cs.library() == null) {
                    if (i == 20) {
                        for (El el : cs.els()) if (el.when() == When.AT_2S) engine.addRoadElementAhead(element(el));
                        for (Act a : cs.acts()) if (a.when() == When.AT_2S) engine.addActorAhead(actor(a));
                    }
                    if (!changingPlaced && ego.getLaneChangeState() == LaneChangeState.CHANGING) {
                        changingPlaced = true;
                        for (El el : cs.els()) if (el.when() == When.ON_CHANGING) engine.addRoadElementAhead(element(el));
                        for (Act a : cs.acts()) if (a.when() == When.ON_CHANGING) engine.addActorAhead(actor(a));
                    }
                }
                engine.tick();
                if (engine.getBackendError() != null) {
                    error = engine.getBackendError();
                    out.add(new Violation("ENGINE_ERROR", t, error));
                    break;
                }
                var d = engine.getCurrentDecision();
                seq.add(d.getSpeedAction() + "/" + d.getLaneAction());
                double egoLat = ego.getLateralPositionMeters();
                Lane physical = Lane.fromLateralPosition(egoLat);
                double kmh = ego.getSpeedKmh();
                double pos = ego.getPositionMeters();
                int egoRow = map.rowAt(pos);
                if (d.getSpeedAction() == DrivingAction.EMERGENCY_BRAKE) emergency++;
                if ("Actuator safety guard".equals(d.getLaneSourceRule())) rejections++;
                if (Math.abs(egoLat) > 5.25) out.add(new Violation("OFF_ROAD", t, String.format("lateral=%.2f", egoLat)));
                if (kmh > 122) { overTicks++; maxOver = Math.max(maxOver, kmh - 120); }

                // lane change start: marking and target-lane occupancy
                LaneChangeState lcs = ego.getLaneChangeState();
                if (lcs == LaneChangeState.CHANGING && prevLcs != LaneChangeState.CHANGING) {
                    laneChanges++;
                    Lane origin = physical;
                    Lane target = ego.getTargetLane();
                    if (origin != target && world.getMarkingBetween(origin, target) == RoadMarkingType.CONTINUOUS) {
                        out.add(new Violation("CONTINUOUS_LINE_CROSSED", t, origin + "->" + target));
                    }
                    for (DynamicActor a : world.getActors()) {
                        if (!a.isActive() || !(a instanceof OtherVehicle v)) continue;
                        double dist = world.vehicleSignedDistance(a);
                        if (v.getLane() == target && dist > -10 && dist < 10) {
                            out.add(new Violation("UNSAFE_LANE_ENTRY", t, String.format("%s->%s vehicle in target lane at %.1f m (%.0f km/h), ego %.0f km/h",
                                origin, target, dist, v.getSpeedMps() * 3.6, kmh)));
                        }
                    }
                    for (RoadElement el : world.getRoadElements()) {
                        if (!el.isActive() || el.getLane() != target) continue;
                        boolean blocking = el instanceof StaticObstacle || (el instanceof Barrier b && !b.isOpen());
                        if (!blocking) continue;
                        double dist = world.signedDistance(el.getPositionMeters());
                        if (dist > -2 && dist < 25) {
                            out.add(new Violation("ENTRY_INTO_HAZARD", t, String.format("%s->%s %s at %.1f m, ego %.0f km/h",
                                origin, target, el.getClass().getSimpleName(), dist, kmh)));
                        }
                    }
                }

                // static hazards under the ego
                for (RoadElement el : world.getRoadElements()) {
                    if (!el.isActive()) continue;
                    Lane elLane = el.getLane();
                    double dist = world.signedDistance(el.getPositionMeters());
                    boolean sameCell = map.rowAt(el.getPositionMeters()) == egoRow && (elLane == physical || elLane == Lane.ALL);
                    String key = el.getId();
                    if (el instanceof StaticObstacle && sameCell && !hitReported.containsKey(key)) {
                        hitReported.put(key, true);
                        out.add(new Violation("HIT_OBSTACLE", t, String.format("lane %s at %.0f km/h", physical, kmh)));
                    } else if (el instanceof Barrier b && !b.isOpen() && sameCell && !hitReported.containsKey(key)) {
                        hitReported.put(key, true);
                        out.add(new Violation("HIT_BARRIER", t, String.format("lane %s at %.0f km/h", physical, kmh)));
                    } else if (el instanceof Pothole && sameCell) {
                        potholeMaxKmh = Math.max(potholeMaxKmh, kmh);
                        if (kmh > 31.5 && !hitReported.containsKey(key)) {
                            hitReported.put(key, true);
                            out.add(new Violation("POTHOLE_FAST", t, String.format("lane %s at %.0f km/h", physical, kmh)));
                        }
                    }
                    // crossings of lights, stop signs, limits
                    Double prev = prevElD.get(key);
                    boolean crossed = prev != null && prev > 0 && dist <= 0;
                    if (crossed && el instanceof TrafficLight light) {
                        if (light.getState() == TrafficLightState.RED) {
                            out.add(new Violation("RED_LIGHT_RUN", t, String.format("at %.0f km/h", kmh)));
                        } else if (light.getState() == TrafficLightState.YELLOW) {
                            out.add(new Violation("info:YELLOW_CROSS", t, String.format("at %.0f km/h", kmh)));
                        }
                    }
                    if (crossed && el instanceof StopSign && (elLane == physical || elLane == Lane.ALL)) {
                        double sinceRest = Double.isNaN(lastRestPos) ? Double.POSITIVE_INFINITY : pos - lastRestPos;
                        if (sinceRest > 25) {
                            out.add(new Violation("STOP_SIGN_RUN", t, String.format("at %.0f km/h, last rest %.0f m before", kmh, sinceRest)));
                        }
                    }
                    if (crossed && el instanceof SpeedLimitSign sl) {
                        currentLimit = sl.getLimitKmh();
                        limitSinceTick = i;
                        overTicks = 0;
                        maxOver = 0;
                    }
                    prevElD.put(key, dist);
                }
                if (Double.isFinite(currentLimit) && i - limitSinceTick > 5) {
                    if (kmh > currentLimit + 2) {
                        overTicks++;
                        maxOver = Math.max(maxOver, kmh - currentLimit);
                        if (overTicks == 10) {
                            out.add(new Violation("SPEED_OVER_LIMIT", t, String.format("limit %.0f, %.0f km/h (%.0f m past the sign)",
                                currentLimit, kmh, (i - limitSinceTick) * kmh / 36.0)));
                        }
                    }
                }

                // actors
                for (DynamicActor a : world.getActors()) {
                    if (!a.isActive()) continue;
                    double dist = world.vehicleSignedDistance(a);
                    if (a instanceof OtherVehicle v) {
                        double latDiff = egoLat - v.getLane().centreMeters();
                        if (Math.abs(latDiff) < 2.0 && dist > 0) minGap = Math.min(minGap, dist);
                        String key = a.getId();
                        if (Math.abs(latDiff) < 2.0 && Math.abs(dist) < 4.5 && !hitReported.containsKey(key)) {
                            hitReported.put(key, true);
                            out.add(new Violation("VEHICLE_COLLISION", t, String.format("car lane %s %.0f km/h, ego %s/%s %.0f km/h, d=%.1f",
                                v.getLane(), v.getSpeedMps() * 3.6, ego.getLane(), physical, kmh, dist)));
                        }
                        Double prev = prevVehD.get(key);
                        if (prev != null && prev > 0 && dist <= 0 && latDiff > 1.0) {
                            out.add(new Violation("PASS_ON_RIGHT", t, String.format("car lane %s %.0f km/h, ego lat %.1f (%s) %.0f km/h",
                                v.getLane(), v.getSpeedMps() * 3.6, egoLat, physical, kmh)));
                        }
                        prevVehD.put(key, dist);
                    } else if (a instanceof Pedestrian p) {
                        String key = a.getId();
                        if (p.isInRoadway() && Math.abs(dist) < 2.0 && Math.abs(p.getLateralPositionMeters() - egoLat) < 1.75 && !hitReported.containsKey(key)) {
                            hitReported.put(key, true);
                            out.add(new Violation("PEDESTRIAN_HIT", t, String.format("ped lat %.1f, ego lat %.1f %.0f km/h", p.getLateralPositionMeters(), egoLat, kmh)));
                        }
                    }
                }

                // rest / stuck
                if (kmh < 0.5) {
                    lastRestPos = pos;
                    restTicks++;
                    if (restTicks >= 150 && !stuckReported) {
                        String reason = holdReason(world, physical);
                        if (reason == null) {
                            stuckReported = true;
                            out.add(new Violation("STUCK_NO_REASON", t, String.format("at rest %d s, decision %s/%s (%s)", restTicks / 10, d.getSpeedAction(), d.getLaneAction(), d.getSpeedSourceRule())));
                        }
                    }
                } else {
                    restTicks = 0;
                }

                boolean change = d.getSpeedAction() != prevSpeed || d.getLaneAction() != prevLaneAct || physical != prevPhysical || lcs != prevLcs;
                if (change && lines < 80) {
                    lines++;
                    trace.append(String.format("  t=%5.1f pos=%6.1f lane=%s/%s lcs=%-9s kmh=%5.1f %s(%s)/%s(%s) %s%n",
                        t, pos, ego.getLane(), physical, lcs, kmh, d.getSpeedAction(), d.getSpeedSourceRule(), d.getLaneAction(), d.getLaneSourceRule(), nearby(world)));
                }
                prevSpeed = d.getSpeedAction();
                prevLaneAct = d.getLaneAction();
                prevPhysical = physical;
                prevLcs = lcs;
            }
            String metrics = String.format("emerg=%d rej=%d laneChanges=%d minGap=%.1f potholeMax=%.0f end=%.0fkm/h@%.0fm %s",
                emergency, rejections, laneChanges, minGap, potholeMaxKmh, ego.getSpeedKmh(), ego.getPositionMeters(), ego.getLane());
            return new Result(out, metrics, trace.toString(), seq);
        }
    }

    /** Something that legitimately holds the vehicle at rest, or null. */
    private static String holdReason(WorldState world, Lane physical) {
        for (RoadElement el : world.getRoadElements()) {
            if (!el.isActive()) continue;
            double dist = world.signedDistance(el.getPositionMeters());
            if (el instanceof TrafficLight l && l.getState() != TrafficLightState.GREEN && dist > -2 && dist <= 30) return "light";
            if (el instanceof StopSign && (el.getLane() == physical || el.getLane() == Lane.ALL) && dist > -2 && dist <= 25) return "stop sign";
            boolean blocking = el instanceof StaticObstacle || (el instanceof Barrier b && !b.isOpen());
            if (blocking && (el.getLane() == physical || el.getLane() == Lane.ALL) && dist > -2 && dist <= 30) return "hazard";
        }
        for (DynamicActor a : world.getActors()) {
            if (!a.isActive()) continue;
            double dist = world.vehicleSignedDistance(a);
            if (a instanceof Pedestrian p && p.isInRoadway() && dist > -5 && dist <= 45) return "pedestrian";
            if (a instanceof OtherVehicle v && Math.abs(world.getEgo().getLateralPositionMeters() - v.getLane().centreMeters()) < 2.0
                && dist > 0 && dist <= 30 && v.getSpeedMps() < 0.5) return "stopped vehicle";
        }
        return null;
    }

    private static String nearby(WorldState world) {
        StringBuilder sb = new StringBuilder();
        for (RoadElement el : world.getRoadElements()) {
            if (!el.isActive()) continue;
            double dist = world.signedDistance(el.getPositionMeters());
            if (dist < -10 || dist > 200) continue;
            sb.append(el.getClass().getSimpleName().charAt(0)).append(el.getLane().name().charAt(0)).append(String.format("%.0f ", dist));
        }
        for (DynamicActor a : world.getActors()) {
            if (!a.isActive()) continue;
            double dist = world.vehicleSignedDistance(a);
            if (dist < -20 || dist > 200) continue;
            sb.append(a instanceof Pedestrian ? "ped" : "car").append(a.getLane().name().charAt(0)).append(String.format("%.0f@%.0f ", dist, a.getSpeedMps() * 3.6));
        }
        return sb.toString().trim();
    }
}
