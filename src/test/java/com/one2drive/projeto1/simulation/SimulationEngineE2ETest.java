package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DrivingState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineE2ETest {
    /** Where a stop sign makes the vehicle stop, measured back from the sign. */
    private static final double STOP_LINE_METERS = StoppingPolicy.STOP_SIGN_OFFSET_METERS;

    @Test
    @Timeout(15)
    void speedSignFlowWorldToSensorToDroolsToVehicleResponse() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.getWorldState().getEgo().setSpeedKmh(90.0);
            engine.start();

            ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
            sign.setType(ScenarioRoadElementType.SPEED_LIMIT);
            sign.setLane(Lane.CENTER);
            sign.setPositionMeters(150.0);
            sign.setSpeedLimitKmh(50);
            engine.addRoadElementAhead(sign);

            for (int i = 0; i < 5; i++) {
                engine.tick();
            }
            List<String> earlyTimeline = engine.snapshot().timeline();
            assertFalse(earlyTimeline.stream().anyMatch(line -> line.contains("Camera read speed limit 50 km/h")));

            boolean detected = false;
            boolean ruleFired = false;
            boolean decisionReduce = false;
            SimulationSnapshot atSign = null;
            for (int i = 0; i < 200 && atSign == null; i++) {
                SimulationSnapshot s = engine.tick();
                detected = detected || s.timeline().stream().anyMatch(line -> line.contains("Camera read speed limit 50 km/h"));
                ruleFired = ruleFired || s.timeline().stream().anyMatch(line -> line.contains("Slow down in time for the speed limit ahead"));
                decisionReduce = decisionReduce || DrivingAction.REDUCE_SPEED.name().equals(s.currentDecision());
                if (s.legalLimitKmh() <= 50.0) {
                    atSign = s;
                } else {
                    assertEquals(120.0, s.legalLimitKmh(), 0.01, "a sign that is only seen ahead is not yet the limit in force");
                }
            }

            assertTrue(detected);
            assertTrue(ruleFired);
            assertTrue(decisionReduce);
            assertTrue(atSign != null, "the limit must come into force when the vehicle reaches the sign");
            assertTrue(atSign.egoSpeedKmh() <= 50.1,
                "the vehicle must be down to the limit at the sign, was at " + atSign.egoSpeedKmh() + " km/h");
            assertTrue(atSign.recommendedSpeedKmh() <= 50.1);

            for (int i = 0; i < 40; i++) {
                engine.tick();
            }
            assertTrue(engine.snapshot().egoSpeedKmh() <= 51.5);
            assertTrue(engine.snapshot().currentDecision().equals(DrivingAction.MAINTAIN_SPEED.name())
                || engine.snapshot().currentDecision().equals(DrivingAction.CONTINUE.name())
                || engine.snapshot().currentDecision().equals(DrivingAction.REDUCE_SPEED.name()));
        }
    }

    /**
     * A limit starts at its sign. Each sign is read well ahead and waits as pending: the
     * limit in force stays the previous one up to the sign, the vehicle is down to a lower
     * limit when it gets there, and a higher one lets it speed up only from the sign on.
     * The signs stand in different lanes and the vehicle changes lane on the way.
     */
    @Test
    @Timeout(30)
    void speedLimitsTakeEffectAtTheirSignsInSequence() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("LIMIT_SEQUENCE");
        scenario.setInitialSpeedKmh(60);
        scenario.setInitialLane(Lane.RIGHT);
        double[] positions = {100.0, 250.0, 400.0};
        int[] limits = {30, 50, 70};
        Lane[] lanes = {Lane.CENTER, Lane.LEFT, Lane.RIGHT};
        for (int i = 0; i < limits.length; i++) {
            ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
            sign.setType(ScenarioRoadElementType.SPEED_LIMIT);
            sign.setLane(lanes[i]);
            sign.setPositionMeters(positions[i]);
            sign.setSpeedLimitKmh(limits[i]);
            scenario.getRoadElements().add(sign);
        }

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            engine.start();
            double[] speedAtSign = {Double.NaN, Double.NaN, Double.NaN};
            double fastestAfterFifty = 0.0;
            double fastestAfterSeventy = 0.0;
            boolean laneChangeRequested = false;
            boolean changedLane = false;
            for (int i = 0; i < 700 && engine.snapshot().egoPositionMeters() < 480.0; i++) {
                SimulationSnapshot s = engine.tick();
                double position = s.egoPositionMeters();
                double speed = s.egoSpeedKmh();
                String where = "at " + position + " m, " + speed + " km/h";

                if (position < 30.0) {
                    assertTrue(speed >= 59.0, "a 30 sign read 70 m or more ahead must not slow the vehicle yet: " + where);
                }
                if (position < positions[0]) {
                    assertEquals(120.0, s.legalLimitKmh(), 0.01, "no limit is in force before the first sign: " + where);
                } else if (position < positions[1]) {
                    assertEquals(30.0, s.legalLimitKmh(), 0.01, "30 holds from its sign up to the 50 sign: " + where);
                    assertTrue(speed <= 30.6, "the vehicle keeps to 30 between the signs: " + where);
                } else if (position < positions[2]) {
                    assertEquals(50.0, s.legalLimitKmh(), 0.01, "50 replaces 30 at the 50 sign: " + where);
                    assertTrue(speed <= 50.6, "the vehicle keeps to 50 between the signs: " + where);
                    fastestAfterFifty = Math.max(fastestAfterFifty, speed);
                } else {
                    assertEquals(70.0, s.legalLimitKmh(), 0.01, "70 replaces 50 at the 70 sign: " + where);
                    fastestAfterSeventy = Math.max(fastestAfterSeventy, speed);
                }
                for (int sign = 0; sign < positions.length; sign++) {
                    if (Double.isNaN(speedAtSign[sign]) && position >= positions[sign]) {
                        speedAtSign[sign] = speed;
                    }
                }

                if (!laneChangeRequested && position > 130.0) {
                    engine.requestLaneChange(LaneChangeDirection.LEFT);
                    laneChangeRequested = true;
                }
                changedLane |= engine.getWorldState().getEgo().getLane() != Lane.RIGHT;
            }

            assertTrue(speedAtSign[0] <= 30.1, "the vehicle must be down to 30 at the 30 sign, was at " + speedAtSign[0]);
            assertTrue(speedAtSign[1] <= 30.6, "and still at 30 when it reaches the 50 sign, was at " + speedAtSign[1]);
            assertTrue(fastestAfterFifty >= 49.0, "past the 50 sign it may build up to 50, reached " + fastestAfterFifty);
            assertTrue(fastestAfterSeventy > 50.6, "past the 70 sign it may speed up again, reached " + fastestAfterSeventy);
            assertTrue(changedLane, "the vehicle changed lane on the way without losing the limit");
        }
    }

    /**
     * A speed limit is one sign in the map but holds for the whole road: the vehicle
     * reads it from any lane, once, and keeps it when it changes lane.
     */
    @Test
    @Timeout(30)
    void singleSpeedLimitSignGovernsEveryLaneAndSurvivesALaneChange() {
        for (Lane lane : List.of(Lane.LEFT, Lane.CENTER, Lane.RIGHT)) {
            ScenarioDefinition scenario = new ScenarioDefinition();
            scenario.setId("LIMIT_ALL_LANES_" + lane);
            scenario.setInitialSpeedKmh(90);
            scenario.setInitialLane(lane);
            ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
            sign.setType(ScenarioRoadElementType.SPEED_LIMIT);
            sign.setLane(Lane.CENTER);
            sign.setPositionMeters(100.0);
            sign.setSpeedLimitKmh(50);
            scenario.getRoadElements().add(sign);

            try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
                assertEquals(1, engine.getWorldState().getRoadElements().stream()
                    .filter(com.one2drive.projeto1.domain.SpeedLimitSign.class::isInstance).count(),
                    "One sign in the world stands for the limit of all three lanes");
                engine.start();
                for (int i = 0; i < 150; i++) {
                    engine.tick();
                }
                assertEquals(50.0, engine.snapshot().legalLimitKmh(), 0.01, "Ego in " + lane + " must read the limit");
                assertTrue(engine.snapshot().egoSpeedKmh() <= 51.5,
                    "Ego in " + lane + " must slow down to the limit, got " + engine.snapshot().egoSpeedKmh());

                engine.requestLaneChange(lane == Lane.LEFT ? LaneChangeDirection.RIGHT : LaneChangeDirection.LEFT);
                boolean leftItsLane = false;
                for (int i = 0; i < 80; i++) {
                    engine.tick();
                    leftItsLane |= engine.getWorldState().getEgo().getLane() != lane;
                    assertEquals(50.0, engine.snapshot().legalLimitKmh(), 0.01,
                        "The limit must hold while and after changing lane from " + lane);
                }
                assertTrue(leftItsLane, "Ego must have changed lane from " + lane);
            }
        }
    }

    /**
     * Approaching a limit from below: the sign is the target, not just a ceiling. The
     * vehicle has to build up to it, because in the web product nothing else can ever
     * speed the car up, and the recommendation must show the limit while it does.
     */
    @Test
    @Timeout(25)
    void signAheadWithHigherLimitBuildsTheVehicleUpToThatLimit() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            double startSpeed = engine.snapshot().egoSpeedKmh();
            engine.start();

            ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
            sign.setType(ScenarioRoadElementType.SPEED_LIMIT);
            sign.setLane(Lane.CENTER);
            sign.setPositionMeters(260.0);
            sign.setSpeedLimitKmh(90);
            engine.addRoadElementAhead(sign);
            // The element is queued while the simulation runs and lands in the world on
            // the next tick.
            engine.tick();

            boolean targetSeen = false;
            SimulationSnapshot snapshot = engine.snapshot();
            for (int i = 0; i < 400; i++) {
                snapshot = engine.tick();
                targetSeen = targetSeen || snapshot.recommendedSpeedKmh() >= 89.9;
                if (targetSeen && snapshot.egoSpeedKmh() >= 89.5) {
                    break;
                }
            }

            assertTrue(targetSeen, "the sign must become the recommendation, last recommendation "
                + snapshot.recommendedSpeedKmh());
            assertTrue(snapshot.egoSpeedKmh() >= 88.0,
                "the vehicle must build up to the higher limit, reached " + snapshot.egoSpeedKmh()
                    + " km/h from " + startSpeed + " km/h");
            assertTrue(snapshot.recommendedSpeedKmh() <= 90.1,
                "and never be advised above it, advised " + snapshot.recommendedSpeedKmh());
        }
    }

    /**
     * A stop sign belongs to the lane it stands in. The continuous markings keep the
     * vehicle in its lane, so each run shows one lane against one sign.
     */
    @Test
    @Timeout(60)
    void stopSignStopsOnlyTheVehicleDrivingInItsLane() {
        List<Lane> lanes = List.of(Lane.LEFT, Lane.CENTER, Lane.RIGHT);
        for (Lane signLane : lanes) {
            for (Lane egoLane : lanes) {
                ScenarioDefinition scenario = new ScenarioDefinition();
                scenario.setId("STOP_" + signLane + "_EGO_" + egoLane);
                scenario.setInitialSpeedKmh(50);
                scenario.setInitialLane(egoLane);
                scenario.setLeftMarking(com.one2drive.projeto1.domain.RoadMarkingType.CONTINUOUS);
                scenario.setRightMarking(com.one2drive.projeto1.domain.RoadMarkingType.CONTINUOUS);
                ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
                sign.setType(ScenarioRoadElementType.STOP_SIGN);
                sign.setLane(signLane);
                sign.setPositionMeters(80.0);
                scenario.getRoadElements().add(sign);

                try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
                    StopSign placed = (StopSign) engine.getWorldState().getRoadElements().stream()
                        .filter(StopSign.class::isInstance).findFirst().orElseThrow();
                    assertEquals(signLane, placed.getLane(), "The sign stands in the lane it was given");
                    engine.start();

                    boolean stopped = false;
                    double slowest = Double.MAX_VALUE;
                    for (int i = 0; i < 300 && !stopped && engine.snapshot().egoPositionMeters() < 130.0; i++) {
                        SimulationSnapshot snapshot = engine.tick();
                        slowest = Math.min(slowest, snapshot.egoSpeedKmh());
                        stopped = snapshot.egoSpeedKmh() < 0.6;
                        assertEquals(egoLane, engine.getWorldState().getEgo().getLane());
                    }
                    String where = "sign in " + signLane + ", ego in " + egoLane;
                    if (signLane == egoLane) {
                        assertTrue(stopped, "The vehicle must stop at the sign of its own lane: " + where);
                        double rest = engine.getWorldState().signedDistance(placed.getPositionMeters());
                        assertTrue(rest >= STOP_LINE_METERS - 1.0 && rest <= STOP_LINE_METERS + 4.0,
                            "and come to rest at the stop line before the sign, not on it: " + rest + " m short, " + where);
                    } else {
                        assertFalse(stopped, "A sign in another lane must not stop the vehicle: " + where);
                        assertTrue(slowest > 40.0, "and must not slow it down either, slowest " + slowest + " km/h: " + where);
                        assertFalse(engine.snapshot().detectedObjectIds().contains(placed.getId()),
                            "and is not reported as detected: " + where);
                    }
                }
            }
        }
    }

    @Test
    @Timeout(20)
    void stopSignFlowBringsVehicleToFullStopThenProceeds() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S4").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            double signPosition = engine.getWorldState().getRoadElements().stream()
                .filter(element -> element instanceof StopSign)
                .findFirst()
                .orElseThrow()
                .getPositionMeters();
            engine.start();

            boolean approached = false;
            boolean stoppedAtLine = false;
            double closestDistance = Double.MAX_VALUE;
            int stoppedTicks = 0;
            for (int i = 0; i < 500 && !stoppedAtLine; i++) {
                SimulationSnapshot snapshot = engine.tick();
                double distance = engine.getWorldState().signedDistance(signPosition);
                closestDistance = Math.min(closestDistance, distance);
                approached = approached || snapshot.timeline().stream().anyMatch(
                    line -> line.contains("Stop sign requires progressive deceleration")
                        || line.contains("Stop sign requires full stop near line"));
                if (snapshot.egoSpeedKmh() < 0.6 && distance <= STOP_LINE_METERS + 4.0) {
                    stoppedTicks++;
                    stoppedAtLine = stoppedTicks >= 10;
                } else {
                    stoppedTicks = 0;
                }
            }

            assertTrue(approached, "the stop sign must be perceived and acted on during the approach");
            assertTrue(stoppedAtLine,
                "the vehicle must come to a full stop at the stop sign, closest distance " + closestDistance);
            assertTrue(closestDistance >= STOP_LINE_METERS - 1.0 && closestDistance <= STOP_LINE_METERS + 4.0,
                "the vehicle must stop at the line instead of crossing it, closest distance " + closestDistance);

            boolean proceeded = false;
            for (int i = 0; i < 250; i++) {
                SimulationSnapshot snapshot = engine.tick();
                proceeded = proceeded || snapshot.timeline().stream()
                    .anyMatch(line -> line.contains("Proceed after stop sign condition satisfied"));
            }
            assertTrue(proceeded, "the stop condition must be released so the vehicle can move off again");
            assertTrue(engine.snapshot().egoSpeedKmh() >= 25.0,
                "the vehicle must pull away to its move-off speed after the stop, speed "
                    + engine.snapshot().egoSpeedKmh());
        }
    }

    /**
     * The approach must be owned by one rule and braked at a steady comfortable rate.
     * Recommending the braking curve used to leave the vehicle a widening gap above it,
     * which pushed the required deceleration back through the hard-braking threshold over
     * and over: the decision then alternated between EMERGENCY_BRAKE, coasting and the
     * baseline, the hazard lights flashed with every pulse, and the vehicle undershot so
     * far that it stalled short of the sign instead of stopping at the line.
     */
    @Test
    @Timeout(20)
    void stopSignApproachBrakesSteadilyInsteadOfPulsingEmergencyBrake() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.start();

            ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
            sign.setType(ScenarioRoadElementType.STOP_SIGN);
            sign.setLane(Lane.RIGHT);
            sign.setPositionMeters(60.0);
            engine.addRoadElementAhead(sign);
            // The element is queued while the simulation runs and lands in the world on the
            // next tick, so it can only be looked up after that tick.
            engine.tick();
            double signPosition = engine.getWorldState().getRoadElements().stream()
                .filter(element -> element instanceof StopSign)
                .findFirst()
                .orElseThrow()
                .getPositionMeters();

            int emergencyBursts = 0;
            int emergencyTicks = 0;
            int baselineTicks = 0;
            boolean approaching = false;
            boolean previousWasEmergency = false;
            boolean stopped = false;
            int stoppedTicks = 0;
            double closestDistance = Double.MAX_VALUE;
            double firstRestDistance = Double.NaN;

            for (int i = 0; i < 400 && !stopped; i++) {
                SimulationSnapshot snapshot = engine.tick();
                double distance = engine.getWorldState().signedDistance(signPosition);
                closestDistance = Math.min(closestDistance, distance);
                if (snapshot.egoSpeedKmh() < 0.6 && Double.isNaN(firstRestDistance)) {
                    firstRestDistance = distance;
                }

                boolean emergency = DrivingAction.EMERGENCY_BRAKE.name().equals(snapshot.currentDecision());
                boolean fromStopRule = snapshot.sourceRule() != null
                    && snapshot.sourceRule().startsWith("Stop sign");
                if (emergency) {
                    emergencyTicks++;
                    if (!previousWasEmergency) {
                        emergencyBursts++;
                    }
                } else if (approaching && "Default maintain speed baseline".equals(snapshot.sourceRule())) {
                    baselineTicks++;
                }
                approaching = approaching || fromStopRule;
                previousWasEmergency = emergency;

                if (snapshot.egoSpeedKmh() < 0.6 && distance <= STOP_LINE_METERS + 4.0) {
                    stoppedTicks++;
                    stopped = stoppedTicks >= 10;
                } else {
                    stoppedTicks = 0;
                }
            }

            assertTrue(stopped, "the vehicle must come to a full stop at the sign, closest distance "
                + closestDistance);
            assertTrue(closestDistance >= STOP_LINE_METERS - 1.0 && closestDistance <= STOP_LINE_METERS + 4.0,
                "the vehicle must stop at the line instead of undershooting or crossing it, closest distance "
                    + closestDistance);
            assertFalse(Double.isNaN(firstRestDistance), "the vehicle must come to rest at the sign");
            assertTrue(firstRestDistance >= STOP_LINE_METERS - 1.0 && firstRestDistance <= STOP_LINE_METERS + 4.0,
                "the first rest must be at the line, not a stall short of it that has to be crept away from: "
                    + firstRestDistance);
            assertTrue(emergencyBursts <= 2,
                "hard braking must be one short burst, not a pulse train: " + emergencyBursts
                    + " bursts over " + emergencyTicks + " ticks");
            assertTrue(baselineTicks < 10,
                "the stop sign must own the whole approach instead of handing the decision back to the "
                    + "baseline for " + baselineTicks + " ticks");
        }
    }

    @Test
    @Timeout(15)
    void ttcFlowProducesEmergencyBrakeAndRecoversRisk() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.getWorldState().getEgo().setSpeedKmh(95.0);

            ScenarioActorDefinition frontVehicle = new ScenarioActorDefinition();
            frontVehicle.setType(ScenarioActorType.FRONT_VEHICLE);
            frontVehicle.setLane(Lane.RIGHT);
            frontVehicle.setPositionMeters(18.0);
            frontVehicle.setSpeedKmh(0.0);
            engine.addActorAhead(frontVehicle);

            engine.start();

            boolean emergency = false;
            boolean ttcObserved = false;
            for (int i = 0; i < 80; i++) {
                SimulationSnapshot snapshot = engine.tick();
                ttcObserved = ttcObserved || snapshot.sensorValues().containsKey("TTCEvent");
                boolean emergencyDecisionInTimeline = snapshot.timeline().stream()
                    .anyMatch(line -> line.contains("DECISION EMERGENCY_BRAKE"));
                if (DrivingAction.EMERGENCY_BRAKE.name().equals(snapshot.currentDecision()) || emergencyDecisionInTimeline) {
                    emergency = true;
                    break;
                }
            }

            assertTrue(emergency);
            assertTrue(engine.snapshot().timeline().stream().anyMatch(line -> line.contains("Emergency brake when persistent TTC is critical")
                || line.contains("Emergency brake when TTC is immediately critical")));

            double speedDuringEmergency = engine.snapshot().egoSpeedKmh();
            for (int i = 0; i < 25; i++) {
                engine.tick();
            }
            double speedLater = engine.snapshot().egoSpeedKmh();
            assertTrue(speedLater < speedDuringEmergency);

            for (int i = 0; i < 55; i++) {
                engine.tick();
            }
            assertNotEquals(DrivingAction.EMERGENCY_BRAKE.name(), engine.snapshot().currentDecision());
            assertTrue(ttcObserved);
        }
    }

    @Test
    @Timeout(12)
    void pedestrianDynamicallyAddedOnlyDetectedWithinRangeAndTriggersSafetyRule() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.getWorldState().getEgo().setSpeedKmh(60.0);
            engine.start();

            for (int i = 0; i < 10; i++) {
                engine.tick();
            }

            ScenarioActorDefinition pedestrian = new ScenarioActorDefinition();
            pedestrian.setType(ScenarioActorType.PEDESTRIAN);
            pedestrian.setLane(Lane.CENTER);
            pedestrian.setPositionMeters(150.0);
            pedestrian.setSpeedKmh(2.0);
            engine.addActorAhead(pedestrian);

            for (int i = 0; i < 5; i++) {
                engine.tick();
            }
            assertFalse(engine.snapshot().timeline().stream().anyMatch(line -> line.contains("Camera read pedestrian")));

            boolean detected = false;
            boolean safetyDecision = false;
            for (int i = 0; i < 70; i++) {
                SimulationSnapshot snapshot = engine.tick();
                detected = detected || snapshot.timeline().stream().anyMatch(line -> line.contains("Camera read pedestrian"));
                safetyDecision = safetyDecision
                    || snapshot.currentDecision().equals(DrivingAction.BRAKE.name())
                    || snapshot.currentDecision().equals(DrivingAction.STOP.name())
                    || snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                if (detected && safetyDecision) {
                    break;
                }
            }

            assertTrue(detected);
            assertTrue(safetyDecision);
            assertTrue(engine.snapshot().timeline().stream().anyMatch(line -> line.contains("pedestrian")));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(12)
    void dynamicWorldMutationPotholeDuringRunTriggersReaction(ExpertBackendType backend) {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = BackendTestSupport.engine(scenario, backend)) {
            engine.start();
            for (int i = 0; i < 8; i++) {
                engine.tick();
            }

            ScenarioRoadElementDefinition pothole = new ScenarioRoadElementDefinition();
            pothole.setType(ScenarioRoadElementType.POTHOLE);
            pothole.setLane(Lane.CENTER);
            pothole.setPositionMeters(90.0);
            pothole.setSeverity(com.one2drive.projeto1.domain.HazardSeverity.HIGH);
            engine.addRoadElementAhead(pothole);

            assertTrue(engine.getWorldState().getRoadElements().stream().noneMatch(e -> e instanceof com.one2drive.projeto1.domain.Pothole));
            engine.tick();
            assertTrue(engine.getWorldState().getRoadElements().stream().anyMatch(e -> e instanceof com.one2drive.projeto1.domain.Pothole));

            boolean detected = false;
            boolean reacted = false;
            for (int i = 0; i < 40; i++) {
                SimulationSnapshot snapshot = engine.tick();
                detected = detected || snapshot.timeline().stream().anyMatch(line -> line.contains("Camera read pothole"));
                reacted = reacted || snapshot.currentDecision().equals(DrivingAction.CHANGE_LANE_LEFT.name())
                    || snapshot.currentDecision().equals(DrivingAction.CHANGE_LANE_RIGHT.name())
                    || snapshot.currentDecision().equals(DrivingAction.REDUCE_SPEED.name())
                    || snapshot.currentDecision().equals(DrivingAction.BRAKE.name());
                if (detected && reacted) {
                    break;
                }
            }

            assertTrue(detected);
            assertTrue(reacted);
        }
    }

    @Test
    @Timeout(12)
    void priorityConflictPedestrianOverridesGreenLight() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.start();

            ScenarioRoadElementDefinition green = new ScenarioRoadElementDefinition();
            green.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
            green.setLane(Lane.CENTER);
            green.setPositionMeters(45.0);
            green.setTrafficLightState(com.one2drive.projeto1.domain.TrafficLightState.GREEN);
            engine.addRoadElementAhead(green);

            ScenarioActorDefinition pedestrian = new ScenarioActorDefinition();
            pedestrian.setType(ScenarioActorType.PEDESTRIAN);
            pedestrian.setLane(Lane.CENTER);
            pedestrian.setPositionMeters(40.0);
            pedestrian.setSpeedKmh(2.0);
            engine.addActorAhead(pedestrian);

            for (int i = 0; i < 25; i++) {
                engine.tick();
            }

            SimulationSnapshot snapshot = engine.snapshot();
            assertTrue(snapshot.currentDecision().equals(DrivingAction.BRAKE.name())
                || snapshot.currentDecision().equals(DrivingAction.STOP.name())
                || snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name()));
            assertTrue(snapshot.timeline().stream().anyMatch(line -> line.contains("pedestrian") || line.contains("Pedestrian")));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(12)
    void weatherMutationDuringRunChangesRecommendationsAndVehicleBehavior(ExpertBackendType backend) {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = BackendTestSupport.engine(scenario, backend)) {
            engine.getWorldState().getEgo().setSpeedKmh(110.0);
            engine.start();
            for (int i = 0; i < 6; i++) {
                engine.tick();
            }

            double speedBefore = engine.snapshot().egoSpeedKmh();
            engine.setWeatherFactors(0.55, 0.45);
            for (int i = 0; i < 30; i++) {
                engine.tick();
            }

            SimulationSnapshot snapshot = engine.snapshot();
            assertEquals(WeatherCondition.RAIN, engine.getWorldState().getWeatherState().getCondition());
            assertTrue(snapshot.timeline().stream().anyMatch(line -> line.contains("visibility reduced to") || line.contains("road grip reduced to")));
            assertTrue(snapshot.timeline().stream().anyMatch(line -> line.contains("Defuzzify weather speed ceiling")
                || line.contains("Weather conditions (fuzzy) require a reduced speed")));
            assertTrue(snapshot.egoSpeedKmh() < speedBefore);
        }
    }

    @Test
    @Timeout(12)
    void emergencyDecisionAppliesEmergencyDecelerationToWorldState() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.getWorldState().getEgo().setSpeedKmh(95.0);

            ScenarioActorDefinition frontVehicle = new ScenarioActorDefinition();
            frontVehicle.setType(ScenarioActorType.FRONT_VEHICLE);
            frontVehicle.setLane(Lane.RIGHT);
            frontVehicle.setPositionMeters(14.0);
            frontVehicle.setSpeedKmh(0.0);
            engine.addActorAhead(frontVehicle);

            engine.start();

            boolean emergencyApplied = false;
            for (int i = 0; i < 80; i++) {
                SimulationSnapshot snapshot = engine.tick();
                boolean emergencyTimeline = snapshot.timeline().stream()
                    .anyMatch(line -> line.contains("DECISION EMERGENCY_BRAKE"));
                boolean emergencyDecision = DrivingAction.EMERGENCY_BRAKE.name().equals(snapshot.currentDecision());
                if (emergencyDecision || emergencyTimeline) {
                    double acceleration = engine.getWorldState().getEgo().getAccelerationMps2();
                    if (acceleration <= -SimulationConfig.defaultConfig().getEmergencyDecelerationMps2() + 0.001) {
                        emergencyApplied = true;
                        break;
                    }
                }
            }

            assertTrue(emergencyApplied, "Emergency brake should apply the configured emergency deceleration to the world");
        }
    }

    @Test
    @Timeout(12)
    void resetClearsPerceptionStateAndRebuildsKieSession() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition empty = library.findById("S1").orElseThrow();
        ScenarioDefinition signed = library.findById("S2").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), signed, true, d -> {
        })) {
            engine.start();
            for (int i = 0; i < 60; i++) {
                engine.tick();
            }
            boolean sawDetection = engine.getWorkingMemoryFactViews().stream()
                .anyMatch(view -> view.type().endsWith("SpeedLimit") && view.value().contains("limitKmh=50"));
            assertTrue(sawDetection, "Signed scenario should be perceived during the run");
            assertFalse(engine.getWorldState().getRoadElements().isEmpty());

            engine.reset(empty);

            assertEquals(0, engine.snapshot().simTimeMillis());
            assertEquals(0, engine.getWorldState().getRoadElements().size());
            assertTrue(engine.getWorkingMemoryFactViews().stream()
                .noneMatch(view -> view.type().endsWith("SpeedLimit") && view.value().contains("limitKmh=50")));
            assertTrue(engine.getWorldState().getActors().isEmpty());
            assertTrue(engine.getDemoEvidence().isEmpty());
        }
    }

    /**
     * A world that is not running reports the vehicle at rest, even when the scenario
     * defines an entry speed: START is what applies that speed, so a stopped
     * simulation never shows a cruising value or moves on its own.
     */
    @Test
    @Timeout(10)
    void stoppedWorldReportsRestAndStartAppliesScenarioSpeed() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            SimulationSnapshot stopped = engine.snapshot();
            assertEquals(SimulationStatus.STOPPED, stopped.status());
            assertEquals(0.0, stopped.egoSpeedKmh(), 0.001,
                "A stopped world must report the vehicle at rest");
            assertEquals(DrivingState.STOPPED, engine.getWorldState().getEgo().getDrivingState());

            engine.start();
            assertEquals(scenario.getInitialSpeedKmh(), engine.getWorldState().getEgo().getSpeedKmh(), 0.001,
                "START applies the scenario's entry speed");
            engine.tick();
            assertTrue(engine.snapshot().egoSpeedKmh() > 0.0,
                "A started simulation advances the vehicle");

            engine.reset(scenario);
            SimulationSnapshot afterReset = engine.snapshot();
            assertEquals(SimulationStatus.STOPPED, afterReset.status());
            assertEquals(0L, afterReset.simTimeMillis());
            assertEquals(0.0, afterReset.egoSpeedKmh(), 0.001,
                "A reset world reports the vehicle at rest again");
            assertEquals(DrivingState.STOPPED, engine.getWorldState().getEgo().getDrivingState());
        }
    }

    @Test
    @Timeout(10)
    void scenarioWithoutInitialLaneStartsAndResetsInRightLane() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();
        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane());
            engine.reset(scenario);
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane());

            ScenarioDefinition explicitCenter = new ScenarioDefinition();
            explicitCenter.setId("EXPLICIT_CENTER");
            explicitCenter.setInitialLane(Lane.CENTER);
            assertEquals(Lane.CENTER, com.one2drive.projeto1.scenario.ScenarioFactory
                .buildWorld(explicitCenter, SimulationConfig.defaultConfig()).getEgo().getLane());
        }
    }

    @Test
    @Timeout(20)
    void aDetectedMapHazardIsRemovedAfterItsFirstPass() {
        int[][] cells = new int[100][3];
        cells[4][Lane.RIGHT.ordinal()] = 1;
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("ONE_SHOT_HAZARD");
        scenario.setInitialSpeedKmh(90);
        scenario.setMap(cells);
        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            engine.start();
            for (int i = 0; i < 250; i++) engine.tick();
            assertTrue(engine.getWorldState().findRoadElementById(RoadMap.cellId(4, Lane.RIGHT)).isEmpty(),
                "A detected map hazard is consumed after passing, rather than returning on the next lap");
        }
    }

    @Test
    @Timeout(35)
    void redTrafficLightStopsAndHoldsThenGreenResumesAndResetClearsFact() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("RED_GREEN_LIFECYCLE");
        scenario.setInitialSpeedKmh(60);
        scenario.setInitialLane(Lane.RIGHT);
        ScenarioRoadElementDefinition red = new ScenarioRoadElementDefinition();
        red.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        red.setLane(Lane.RIGHT);
        red.setPositionMeters(90);
        red.setTrafficLightState(com.one2drive.projeto1.domain.TrafficLightState.RED);
        scenario.getRoadElements().add(red);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            engine.start();
            boolean stopped = false;
            for (int i = 0; i < 350; i++) {
                engine.tick();
                if (engine.getWorldState().getEgo().getSpeedKmh() < 0.1) {
                    stopped = true;
                    break;
                }
            }
            assertTrue(stopped, "RED must bring ego to a complete stop");
            double lightDistance = engine.getWorldState().signedDistance(
                engine.getWorldState().getRoadElements().stream().filter(TrafficLight.class::isInstance)
                    .map(TrafficLight.class::cast).findFirst().orElseThrow().getPositionMeters());
            assertTrue(lightDistance >= 2.0, "Ego must stop before the light line, distance=" + lightDistance);

            for (int i = 0; i < 20; i++) engine.tick();
            assertEquals(0.0, engine.getWorldState().getEgo().getSpeedKmh(), 0.1,
                "Ego must remain stopped while RED persists");
            assertEquals(DrivingAction.STOP, engine.getCurrentDecision().getAction());
            assertTrue(engine.getWorkingMemoryFactViews().stream()
                .anyMatch(f -> f.type().equals("TrafficLightStateFact") && f.value().contains("state=RED")),
                "RED must remain in working memory rather than stale-clearing to GREEN");

            ScenarioRoadElementDefinition green = new ScenarioRoadElementDefinition();
            green.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
            green.setLane(Lane.RIGHT);
            green.setPositionMeters(lightDistance);
            green.setTrafficLightState(com.one2drive.projeto1.domain.TrafficLightState.GREEN);
            String lightId = engine.getWorldState().getRoadElements().stream()
                .filter(TrafficLight.class::isInstance).map(TrafficLight.class::cast).findFirst().orElseThrow().getId();
            assertTrue(engine.updateRoadElementById(lightId, green).isPresent());

            boolean resumed = false;
            for (int i = 0; i < 30; i++) {
                SimulationSnapshot snapshot = engine.tick();
                if (snapshot.egoSpeedKmh() > 1.0) {
                    resumed = true;
                    break;
                }
            }
            assertTrue(resumed, "GREEN must smoothly release the stop and resume forward motion");

            engine.reset(scenario);
            assertTrue(engine.getWorkingMemoryFactViews().stream()
                .filter(f -> f.type().equals("TrafficLightStateFact"))
                .noneMatch(f -> f.value().contains("lightId='" + lightId + "'")),
                "RESET must discard the old traffic-light fact and KIE session state");
        }
    }

    @Test
    @Timeout(20)
    void potholeInRightLaneChangesLeftOnlyWhenCenterIsClear() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("RIGHT_POTHOLE_CLEAR_CENTER");
        scenario.setInitialSpeedKmh(50);
        scenario.setInitialLane(Lane.RIGHT);
        ScenarioRoadElementDefinition pothole = new ScenarioRoadElementDefinition();
        pothole.setType(ScenarioRoadElementType.POTHOLE);
        pothole.setLane(Lane.RIGHT);
        pothole.setPositionMeters(80);
        pothole.setSeverity(com.one2drive.projeto1.domain.HazardSeverity.HIGH);
        scenario.getRoadElements().add(pothole);
        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            engine.start();
            boolean changedLeft = false;
            for (int i = 0; i < 100; i++) {
                SimulationSnapshot snapshot = engine.tick();
                changedLeft |= DrivingAction.CHANGE_LANE_LEFT.name().equals(snapshot.currentDecision());
                if (engine.getWorldState().getEgo().getLane() == Lane.CENTER) break;
            }
            assertTrue(changedLeft, "RIGHT pothole with clear CENTER should start the staged left change");
            assertEquals(Lane.CENTER, engine.getWorldState().getEgo().getLane());
        }
    }

    @Test
    @Timeout(20)
    void unsafeCenterTargetBlocksRightLanePotholeManeuverAndBrakes() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("RIGHT_POTHOLE_OCCUPIED_CENTER");
        scenario.setInitialSpeedKmh(50);
        scenario.setInitialLane(Lane.RIGHT);
        ScenarioRoadElementDefinition pothole = new ScenarioRoadElementDefinition();
        pothole.setType(ScenarioRoadElementType.POTHOLE);
        pothole.setLane(Lane.RIGHT);
        pothole.setPositionMeters(80);
        pothole.setSeverity(com.one2drive.projeto1.domain.HazardSeverity.HIGH);
        scenario.getRoadElements().add(pothole);
        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            engine.getWorldState().addActor(new com.one2drive.projeto1.domain.OtherVehicle(
                engine.getWorldState().getEgo().getPositionMeters() + 5.0, Lane.CENTER, 0.0));
            assertFalse(engine.isLaneSafe(Lane.CENTER), "CENTER vehicle 5m ahead must block that lane");
            DecisionActuator actuator = new DecisionActuator(SimulationConfig.defaultConfig());
            DrivingDecision forced = new DrivingDecision();
            forced.apply(DrivingAction.CHANGE_LANE_LEFT, Double.NaN, com.one2drive.projeto1.domain.RiskLevel.LOW,
                com.one2drive.projeto1.decision.DecisionPriority.ROAD_HAZARD, "forced unsafe lane change", "test", 0L, List.of());
            actuator.applyDecision(engine.getWorldState(), forced);
            assertEquals("Actuator safety guard", forced.getSourceRule());
            assertEquals(DrivingAction.REDUCE_SPEED, forced.getAction());
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane());
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getTargetLane());
            assertEquals(com.one2drive.projeto1.domain.LaneChangeState.IDLE,
                engine.getWorldState().getEgo().getLaneChangeState());
        }
    }

    @Test
    @Timeout(10)
    void actuatorRejectsLaneChangeIntoOccupiedTargetLane() {
        SimulationConfig config = SimulationConfig.defaultConfig();
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("GLOBAL_LANE_GUARD");
        scenario.setInitialSpeedKmh(40);
        scenario.setInitialLane(Lane.RIGHT);
        try (SimulationEngine engine = new SimulationEngine(config, scenario, true, d -> { })) {
            com.one2drive.projeto1.domain.EgoVehicleState ego = engine.getWorldState().getEgo();
            ScenarioActorDefinition alongside = new ScenarioActorDefinition();
            alongside.setType(ScenarioActorType.FRONT_VEHICLE);
            alongside.setLane(Lane.CENTER);
            alongside.setPositionMeters(-2.0);
            alongside.setSpeedKmh(40);
            engine.getWorldState().addActor(com.one2drive.projeto1.scenario.ScenarioFactory.createActor(alongside));
            assertFalse(engine.isLaneSafe(Lane.CENTER), "CENTER occupied by an alongside vehicle must be unsafe");
            assertFalse(engine.isLaneSafe(Lane.LEFT), "A non-adjacent lane cannot be entered in one lane-change action");

            DecisionActuator actuator = new DecisionActuator(config);
            DrivingDecision forced = new DrivingDecision();
            forced.apply(DrivingAction.CHANGE_LANE_LEFT, Double.NaN, com.one2drive.projeto1.domain.RiskLevel.LOW,
                com.one2drive.projeto1.decision.DecisionPriority.ROAD_HAZARD, "forced unsafe lane change", "test", 0L, List.of());
            actuator.applyDecision(engine.getWorldState(), forced);
            assertEquals(Lane.RIGHT, ego.getTargetLane(), "Unsafe CENTER target is rejected, preserving current lane");
            assertEquals(com.one2drive.projeto1.domain.LaneChangeState.IDLE, ego.getLaneChangeState());
            // The refusal is recorded on the decision and the speed channel eases off; the
            // vehicle is still at its standstill here, so it simply does not accelerate.
            assertEquals(DrivingAction.KEEP_LANE, forced.getLaneAction());
            assertEquals("Actuator safety guard", forced.getSourceRule());
            assertEquals(DrivingAction.REDUCE_SPEED, forced.getAction(), "Rejected unsafe change selects safe deceleration");
            assertTrue(ego.getAccelerationMps2() <= 0.0, "Rejected unsafe change never accelerates");
        }
    }

    /**
     * A rule that only slows the vehicle down leaves it stranded at the reduced speed once
     * the road is clear again. With no sign governing the road, the scenario cruise speed
     * must be the target the vehicle builds back up to.
     */
    @Test
    @Timeout(25)
    void vehicleBuildsBackUpToScenarioCruiseSpeedAfterBrakingWithNoSign() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.start();
            engine.getWorldState().getEgo().setSpeedKmh(25.0);

            boolean cruiseTargetSeen = false;
            SimulationSnapshot snapshot = engine.snapshot();
            for (int i = 0; i < 400; i++) {
                snapshot = engine.tick();
                cruiseTargetSeen = cruiseTargetSeen || snapshot.recommendedSpeedKmh() >= 59.5;
                if (cruiseTargetSeen && snapshot.egoSpeedKmh() >= 59.0) {
                    break;
                }
            }

            assertTrue(cruiseTargetSeen,
                "the scenario cruise speed must become the recommendation, last recommendation "
                    + snapshot.recommendedSpeedKmh());
            assertTrue(snapshot.egoSpeedKmh() >= 58.0,
                "the vehicle must build back up to the cruise speed, reached "
                    + snapshot.egoSpeedKmh() + " km/h from 25 km/h");
            assertTrue(snapshot.recommendedSpeedKmh() <= 60.5,
                "and never be advised above it, advised " + snapshot.recommendedSpeedKmh());
            assertTrue(snapshot.timeline().stream()
                    .anyMatch(line -> line.contains("Build back up to the scenario cruise speed")),
                "the cruise recovery rule must be visible in the inference timeline");
        }
    }

    @Test
    @Timeout(30)
    void passedPotholeStaysInRoadItemsUntilFiftyMetresBehind() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S5").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            List<String> potholeIds = engine.getWorldState().getRoadElements().stream()
                .filter(com.one2drive.projeto1.domain.Pothole.class::isInstance)
                .map(element -> element.getId())
                .toList();
            assertEquals(List.of("r18-CENTER"), potholeIds, "S5 has a single pothole");
            String potholeId = potholeIds.get(0);
            engine.start();

            boolean sawPassed = false;
            boolean removed = false;
            for (int i = 0; i < 400 && !removed; i++) {
                engine.tick();
                var pothole = engine.getAllRoadItemsRelativeToEgo().stream()
                    .filter(item -> item.id().equals(potholeId))
                    .findFirst();
                if (sawPassed && pothole.isPresent()) {
                    assertTrue(pothole.get().positionMeters() < 0.0,
                        "A passed pothole must never reappear ahead of the car");
                }
                if (pothole.isPresent() && pothole.get().positionMeters() < 0.0) {
                    sawPassed = true;
                    assertTrue(pothole.get().positionMeters() >= -50.0,
                        "A passed pothole is rendered no further than 50 m behind the car");
                } else if (sawPassed && pothole.isEmpty()) {
                    removed = true;
                }
            }

            assertTrue(sawPassed, "The pothole must stay in the render list after the ego passes it");
            assertTrue(removed, "The pothole must leave the render list once it is more than 50 m behind");
        }
    }

    @Test
    @Timeout(30)
    void overtakenVehicleStaysInWorldUntilFiftyMetresBehind() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S14_SLOW_VEHICLE_OVERTAKE").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawBehind = false;
            boolean removed = false;
            for (int i = 0; i < 500 && !removed; i++) {
                engine.tick();
                var actors = engine.getWorldState().getActors();
                if (!actors.isEmpty()) {
                    String actorId = actors.get(0).getId();
                    double distance = engine.getWorldState().signedDistance(actors.get(0).getPositionMeters());
                    if (distance < 0.0) {
                        sawBehind = true;
                        assertTrue(distance >= -50.0, "An overtaken vehicle is kept no further than 50 m behind the car");
                        assertTrue(engine.getAllRoadItemsRelativeToEgo().stream()
                                .anyMatch(item -> item.id().equals(actorId) && item.positionMeters() < 0.0),
                            "An overtaken vehicle still in the world is drawn behind the car");
                    }
                } else if (sawBehind) {
                    removed = true;
                }
            }

            assertTrue(sawBehind, "The overtaken vehicle must remain in the world after being passed");
            assertTrue(removed, "The overtaken vehicle must be removed once it is more than 50 m behind");
        }
    }

    /**
     * Actors keep a constant speed and follow nothing. When the ego stops behind a red light
     * while the vehicle it just overtook is still in the rendered window behind it, that
     * vehicle catches up; it must leave the world before it reaches the car instead of driving
     * through it, so the radar never sees it on the bumper and the rule engine never locks.
     */
    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void stoppedEgoIsNotDrivenThroughByOvertakenVehicle() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S14_SLOW_VEHICLE_OVERTAKE").orElseThrow();
        ScenarioRoadElementDefinition red = new ScenarioRoadElementDefinition();
        red.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        red.setLane(Lane.RIGHT);
        red.setPositionMeters(30);
        red.setTrafficLightState(com.one2drive.projeto1.domain.TrafficLightState.RED);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            // Same value as SimulationEngine.CATCH_UP_GAP_METERS, about one car length. Right after
            // the overtake the vehicle legitimately sits inside this gap while it falls back, so the
            // gap is only enforced once the vehicle has been seen beyond it.
            double catchUpGap = 6.0;
            Set<String> seenBehind = new HashSet<>();
            Set<String> seenBeyondGap = new HashSet<>();
            boolean stopped = false;
            for (int i = 0; i < 400; i++) {
                if (i == 125) {
                    engine.addRoadElementAheadAndGetId(red);
                }
                engine.tick();
                stopped |= engine.getWorldState().getEgo().getSpeedKmh() < 0.1;
                for (var actor : engine.getWorldState().getActors()) {
                    double distance = engine.getWorldState().signedDistance(actor.getPositionMeters());
                    if (distance <= -catchUpGap) {
                        seenBeyondGap.add(actor.getId());
                    } else {
                        assertFalse(seenBeyondGap.contains(actor.getId()),
                            "A vehicle that fell behind the car must leave before closing back within one car length, tick "
                                + i + ", distance " + distance);
                    }
                    if (distance < 0.0) {
                        seenBehind.add(actor.getId());
                    } else if (distance > 0.0) {
                        assertFalse(seenBehind.contains(actor.getId()),
                            "A vehicle seen behind the car must never reappear ahead of it, tick " + i
                                + ", distance " + distance);
                    }
                }
            }

            assertFalse(seenBeyondGap.isEmpty(), "The ego must overtake the slow vehicle before the light");
            assertTrue(stopped, "The red light must stop the ego");
            assertTrue(engine.getWorldState().getActors().isEmpty(),
                "The overtaken vehicle catches up with the stopped ego and must have left the world");
        }
    }

    @Test
    @Timeout(30)
    void rainSpeedSettlesOnTheRecommendedSpeedWithoutHunting() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S10").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            SimulationSnapshot snapshot = null;
            int stableTicks = 0;
            int longestStreak = 0;
            boolean settled = false;
            for (int i = 0; i < 400; i++) {
                snapshot = engine.tick();
                boolean withinBand = Math.abs(snapshot.egoSpeedKmh() - snapshot.recommendedSpeedKmh()) <= 0.5;
                if (settled) {
                    assertTrue(withinBand, "tick " + i + ": speed " + snapshot.egoSpeedKmh()
                        + " left the ±0.5 km/h band around " + snapshot.recommendedSpeedKmh());
                }
                stableTicks = withinBand ? stableTicks + 1 : 0;
                longestStreak = Math.max(longestStreak, stableTicks);
                settled |= stableTicks >= 50;
            }

            assertTrue(longestStreak >= 50,
                "Once the car reaches the recommended rain speed it must stay within 0.5 km/h of it for 50 consecutive ticks");
            assertEquals(50.57, snapshot.recommendedSpeedKmh(), 0.01,
                "the fuzzy weather ceiling for rain (visibility 0.70, grip 0.65) keeps recommending 50.6 km/h, "
                    + "well below the 85 km/h entry speed");
        }
    }
}
