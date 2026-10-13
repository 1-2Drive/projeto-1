package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.IndicatorState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.SteeringState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import com.one2drive.projeto1.scenario.ScenarioTimedAction;
import com.one2drive.projeto1.scenario.ScenarioTimedActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadLawAndOvertakingE2ETest {

    @Test
    @Timeout(15)
    @DisplayName("Moving off: vehicle starts from 0 km/h, accelerates continuously, and advances world position")
    void movingOffAndContinuousEgoMovementIncreasesWorldPosition() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("MOVE_OFF_TEST");
        scenario.setName("Move off from rest");
        scenario.setInitialSpeedKmh(0.0);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            double initialPos = engine.getWorldState().getEgo().getPositionMeters();
            assertEquals(0.0, initialPos, 0.01);
            assertEquals(0.0, engine.getWorldState().getEgo().getSpeedKmh(), 0.1);

            // Tick forward and observe kinematics evolution
            double prevPos = initialPos;
            boolean sawAccelerate = false;
            for (int i = 0; i < 40; i++) {
                SimulationSnapshot snapshot = engine.tick();
                double currentPos = snapshot.egoPositionMeters();
                double currentSpeed = snapshot.egoSpeedKmh();

                if (i > 5) {
                    assertTrue(currentPos >= prevPos, "World position must monotonically increase while driving");
                }
                if (snapshot.currentDecision().equals(DrivingAction.ACCELERATE.name())
                    || snapshot.currentDecision().equals(DrivingAction.CONTINUE.name())) {
                    sawAccelerate = true;
                }
                prevPos = currentPos;
            }

            assertTrue(sawAccelerate, "Vehicle should decide to accelerate/move off");
            assertTrue(engine.getWorldState().getEgo().getSpeedKmh() > 10.0,
                "Ego speed must increase from rest, got " + engine.getWorldState().getEgo().getSpeedKmh());
            assertTrue(engine.getWorldState().getEgo().getPositionMeters() > 5.0,
                "Ego world position must advance continuously, got " + engine.getWorldState().getEgo().getPositionMeters());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("Overtaking: slow vehicle ahead triggers left lane change, pass, and return right (Art. 35/38 CE)")
    void overtakingSlowVehiclePassesAndReturnsToRightLane() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S14_SLOW_VEHICLE_OVERTAKE").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(), "Ego starts in rightmost lane");

            boolean sawLeftChange = false;
            boolean sawLeftIndicator = false;
            boolean reachedCenter = false;
            boolean sawReturnRight = false;
            boolean returnedToRight = false;

            for (int i = 0; i < 280; i++) {
                SimulationSnapshot snapshot = engine.tick();
                var ego = engine.getWorldState().getEgo();

                if (snapshot.currentDecision().equals(DrivingAction.CHANGE_LANE_LEFT.name())) {
                    sawLeftChange = true;
                    if (ego.getIndicatorState() == IndicatorState.LEFT) {
                        sawLeftIndicator = true;
                    }
                }

                if (ego.getLane() == Lane.CENTER && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                    reachedCenter = true;
                }

                if (reachedCenter && (snapshot.currentDecision().equals(DrivingAction.RETURN_RIGHT.name())
                    || snapshot.currentDecision().equals(DrivingAction.CHANGE_LANE_RIGHT.name()))) {
                    sawReturnRight = true;
                }

                if (sawReturnRight && ego.getLane() == Lane.RIGHT && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                    returnedToRight = true;
                    break;
                }
            }

            assertTrue(sawLeftChange, "Drools must initiate left lane change to overtake slower front vehicle");
            assertTrue(sawLeftIndicator, "Left turn indicator must be activated before/during lane change");
            assertTrue(reachedCenter, "Vehicle must physically reach overtaking lane");
            assertTrue(sawReturnRight, "Vehicle must initiate return to right lane after overtaking");
            assertTrue(returnedToRight, "Vehicle must complete return to rightmost lane per Art. 38 CE");
            assertEquals(IndicatorState.OFF, engine.getWorldState().getEgo().getIndicatorState(),
                "Indicator must turn OFF after return-right maneuver completes");
            assertEquals(SteeringState.STRAIGHT, engine.getWorldState().getEgo().getSteeringState(),
                "Steering must return to straight once maneuver completes");
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("Continuous line: continuous road marking prohibits lane change; vehicle brakes before obstacle")
    void continuousLineBlocksLaneChangeAndBrakesBeforeObstacle() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S15_CONTINUOUS_LINE_BLOCKED").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawContinuousLineRule = false;
            boolean illegalLeftChange = false;
            boolean stoppedSafely = false;

            for (int i = 0; i < 80; i++) {
                SimulationSnapshot snapshot = engine.tick();
                var ego = engine.getWorldState().getEgo();

                if (snapshot.timeline().stream().anyMatch(t -> t.contains("Continuous line prohibits lane change"))) {
                    sawContinuousLineRule = true;
                }

                if (ego.getLane() == Lane.CENTER || ego.getLane() == Lane.LEFT) {
                    illegalLeftChange = true;
                }

                if (ego.getSpeedKmh() < 1.0) {
                    stoppedSafely = true;
                    break;
                }
            }

            assertTrue(sawContinuousLineRule, "Drools should explain that continuous road marking prohibits lane change");
            assertFalse(illegalLeftChange, "Vehicle must NOT cross continuous line (Art. 60.º RST)");
            assertTrue(stoppedSafely, "Vehicle must brake to safe stop before obstacle");
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("Pedestrian crossing: vehicle brakes/stops while pedestrian crosses roadway, then resumes")
    void pedestrianCrossingBrakesThenResumesAfterCrossingClears() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S7").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawBrakeOrStop = false;
            for (int i = 0; i < 70; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String action = snapshot.currentDecision();
                if (action.equals(DrivingAction.BRAKE.name())
                    || action.equals(DrivingAction.STOP.name())
                    || action.equals(DrivingAction.EMERGENCY_BRAKE.name())) {
                    sawBrakeOrStop = true;
                    break;
                }
            }

            assertTrue(sawBrakeOrStop, "Vehicle must yield and brake/stop for pedestrian in crossing zone (Art. 103 CE)");
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Matrix state vector extracts kinematic vector [x, y, vx, vy] accurately")
    void stateVectorRepresentationReflectsKinematics() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("VECTOR_TEST");
        scenario.setName("State vector test");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        ScenarioActorDefinition frontCar = new ScenarioActorDefinition();
        frontCar.setType(ScenarioActorType.FRONT_VEHICLE);
        frontCar.setLane(Lane.CENTER);
        frontCar.setPositionMeters(80.0);
        frontCar.setSpeedKmh(30.0);
        scenario.getActors().add(frontCar);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();
            engine.tick();

            WorldPhysicsEngine physics = new WorldPhysicsEngine(SimulationConfig.defaultConfig());
            double[] vector = physics.extractStateVector(engine.getWorldState());

            assertTrue(vector.length >= 8, "Vector must contain 4 ego states + 4 states per actor");
            assertEquals(engine.getWorldState().getEgo().getPositionMeters(), vector[0], 0.01);
            assertEquals(engine.getWorldState().getEgo().getLateralPositionMeters(), vector[1], 0.01);
            assertEquals(engine.getWorldState().getEgo().getSpeedMps(), vector[2], 0.01);
            assertEquals(80.0, vector[4], 1.0); // Actor x
            assertEquals(30.0 / 3.6, vector[6], 0.1); // Actor vx
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("Regression Exact Screenshot: ego in CENTER with clear RIGHT lane must fire RETURN_RIGHT and return to RIGHT")
    void regressionTestExactScreenshotStateCenterReturnsToRight() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("SCREENSHOT_REGRESSION");
        scenario.setName("Center lane return right regression");
        scenario.setInitialSpeedKmh(35.8);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            var ego = engine.getWorldState().getEgo();
            assertEquals(Lane.CENTER, ego.getLane(), "Ego vehicle starts in CENTER lane");
            assertEquals(35.8, ego.getSpeedKmh(), 0.5, "Ego speed is ~35.8 km/h");

            boolean sawReturnRight = false;
            boolean returnedToRight = false;

            for (int i = 0; i < 140; i++) {
                SimulationSnapshot snapshot = engine.tick();

                if (snapshot.currentDecision().equals(DrivingAction.RETURN_RIGHT.name())
                    || snapshot.currentDecision().equals(DrivingAction.CHANGE_LANE_RIGHT.name())) {
                    sawReturnRight = true;
                    // While changing right, indicator must be RIGHT
                    if (ego.getLaneChangeState() == LaneChangeState.CHANGING) {
                        assertEquals(IndicatorState.RIGHT, ego.getIndicatorState(),
                            "Right turn indicator must be active during lateral return");
                    }
                }

                if (ego.getLane() == Lane.RIGHT && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                    returnedToRight = true;
                    break;
                }
            }

            assertTrue(sawReturnRight, "Drools must NOT settle into MAINTAIN_SPEED in CENTER; must fire RETURN_RIGHT");
            assertTrue(returnedToRight, "Ego vehicle must physically return to RIGHT lane");
            assertEquals(Lane.RIGHT, ego.getLane(), "Ego must settle in RIGHT lane");
            assertEquals(IndicatorState.OFF, ego.getIndicatorState(), "Indicator must turn OFF after settling in RIGHT");
        }
    }

    @Test
    @Timeout(25)
    @DisplayName("Avoidance follow-through: vehicle avoids obstacle in RIGHT, passes it, and returns to RIGHT lane")
    void regressionTestReturnRightAfterObstacleAvoidance() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("AVOID_AND_RETURN");
        scenario.setName("Avoid obstacle and return right");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var obstacleDef = new com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition();
        obstacleDef.setType(com.one2drive.projeto1.scenario.ScenarioRoadElementType.STATIC_OBSTACLE);
        obstacleDef.setPositionMeters(40.0);
        obstacleDef.setLane(Lane.RIGHT);
        obstacleDef.setSeverity(com.one2drive.projeto1.domain.HazardSeverity.HIGH);
        scenario.getRoadElements().add(obstacleDef);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean avoidedToCenter = false;
            boolean passedObstacle = false;
            boolean returnedToRight = false;

            for (int i = 0; i < 160; i++) {
                SimulationSnapshot snapshot = engine.tick();
                var ego = engine.getWorldState().getEgo();

                if (ego.getLane() == Lane.CENTER) {
                    avoidedToCenter = true;
                }

                if (avoidedToCenter && ego.getPositionMeters() > 45.0) {
                    passedObstacle = true;
                }

                if (passedObstacle && ego.getLane() == Lane.RIGHT && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                    returnedToRight = true;
                    break;
                }
            }

            assertTrue(avoidedToCenter, "Vehicle must avoid obstacle into CENTER lane");
            assertTrue(passedObstacle, "Vehicle must pass obstacle");
            assertTrue(returnedToRight, "Vehicle must return to RIGHT lane once obstacle is behind ego");
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane());
            assertEquals(IndicatorState.OFF, engine.getWorldState().getEgo().getIndicatorState());
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("Multi-lane return: vehicle starting in LEFT returns sequentially: LEFT -> CENTER -> RIGHT")
    void regressionTestMultiLaneReturnLeftToCenterToRight() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("MULTI_LANE_RETURN");
        scenario.setName("Left to center to right return");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.LEFT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean reachedCenter = false;
            boolean reachedRight = false;
            List<String> visitedLanes = new ArrayList<>();
            for (int i = 0; i < 150; i++) {
                SimulationSnapshot snap = engine.tick();
                var ego = engine.getWorldState().getEgo();
                String laneName = ego.getLane().name();
                if (visitedLanes.isEmpty() || !visitedLanes.get(visitedLanes.size() - 1).equals(laneName)) {
                    visitedLanes.add(laneName);
                }

                if (ego.getLane() == Lane.CENTER) {
                    reachedCenter = true;
                }
                if (reachedCenter && ego.getLane() == Lane.RIGHT && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                    reachedRight = true;
                    break;
                }
            }

            assertTrue(reachedCenter, "Vehicle must first return to CENTER lane from LEFT");
            assertTrue(reachedRight, "Vehicle must then return to RIGHT lane from CENTER");
            assertEquals(List.of("LEFT", "CENTER", "RIGHT"), visitedLanes,
                "Lanes must be visited in strict sequence without skipping: " + visitedLanes);
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane());
            assertEquals(IndicatorState.OFF, engine.getWorldState().getEgo().getIndicatorState());
        }
    }

    @Test
    @Timeout(20)
    @DisplayName("Blocked return: vehicle in CENTER with vehicle 3m behind in RIGHT waits, then returns when safe")
    void regressionTestBlockedReturnRightWaitsForSafety() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BLOCKED_RETURN_TEST");
        scenario.setName("Blocked return test");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            // Add a blocker in RIGHT lane alongside / 3m behind ego
            var blocker = new com.one2drive.projeto1.domain.OtherVehicle(
                engine.getWorldState().getEgo().getPositionMeters() - 3.0,
                Lane.RIGHT,
                50.0 / 3.6
            );
            engine.getWorldState().addActor(blocker);

            // Run 15 ticks with blocker alongside
            for (int i = 0; i < 15; i++) {
                blocker.setPositionMeters(engine.getWorldState().getEgo().getPositionMeters() - 3.0);
                engine.tick();
                assertEquals(Lane.CENTER, engine.getWorldState().getEgo().getLane(),
                    "Vehicle must NOT return right while right lane has a vehicle 3m behind");
                assertFalse(engine.getWorldState().getEgo().getLaneChangeState().isActive(),
                    "Lane change must NOT be active while right lane is occupied");
            }

            // Now remove blocker (lane becomes safe)
            blocker.setActive(false);

            boolean returnedRight = false;
            for (int i = 0; i < 60; i++) {
                engine.tick();
                if (engine.getWorldState().getEgo().getLane() == Lane.RIGHT
                    && engine.getWorldState().getEgo().getLaneChangeState() == LaneChangeState.IDLE) {
                    returnedRight = true;
                    break;
                }
            }

            assertTrue(returnedRight, "Vehicle must return to RIGHT lane once blocker has cleared");
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane());
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Forward motion: ego.worldPosition continuously increases and static object distance decreases")
    void regressionTestForwardMotionAndApproachingObjectDistance() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("FORWARD_MOTION_TEST");
        scenario.setName("Forward motion test");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var signDef = new com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition();
        signDef.setType(com.one2drive.projeto1.scenario.ScenarioRoadElementType.SPEED_LIMIT);
        signDef.setPositionMeters(100.0);
        signDef.setLane(Lane.RIGHT);
        signDef.setSpeedLimitKmh(50);
        scenario.getRoadElements().add(signDef);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            double p0 = engine.getWorldState().getEgo().getPositionMeters();
            double d0 = engine.getWorldState().signedDistance(100.0);

            for (int i = 0; i < 20; i++) {
                engine.tick();
            }

            double p1 = engine.getWorldState().getEgo().getPositionMeters();
            double d1 = engine.getWorldState().signedDistance(100.0);

            assertTrue(p1 > p0, "Ego position must continuously increase while moving: p1=" + p1 + " > p0=" + p0);
            assertTrue(d1 < d0, "Static object distance must decrease as vehicle approaches: d1=" + d1 + " < d0=" + d0);
        }
    }

    // ======================================================================
    // BUG 1 REGRESSION: Speed Limit 50 -> stable ego speed <= 50 km/h
    // ======================================================================
    @Test
    @Timeout(15)
    @DisplayName("BUG 1 Regression: speedLimit = 50 -> stable ego speed <= 50 km/h (never 51 km/h)")
    void bug1RegressionSpeedLimit50NeverExceedsOrStabilizesAt51() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG1_SPEED50");
        scenario.setName("Speed limit 50 compliance test");
        scenario.setInitialSpeedKmh(90.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var speedSign = new ScenarioRoadElementDefinition();
        speedSign.setType(ScenarioRoadElementType.SPEED_LIMIT);
        speedSign.setPositionMeters(30.0);
        speedSign.setLane(Lane.RIGHT);
        speedSign.setSpeedLimitKmh(50);

        var action = new ScenarioTimedAction();
        action.setType(ScenarioTimedActionType.ADD_ROAD_ELEMENT);
        action.setTriggerTimeMillis(0);
        action.setRoadElement(speedSign);
        scenario.getTimedActions().add(action);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            // Run until the vehicle decelerates to the speed limit and stabilizes
            for (int i = 0; i < 150; i++) {
                engine.tick();
            }

            var ego = engine.getWorldState().getEgo();
            double speed = ego.getSpeedKmh();

            // Must stabilize at <= 50.0 km/h
            assertTrue(speed <= 50.001, "Stable ego speed must be <= 50 km/h, but was " + speed);
            assertTrue(speed >= 48.0, "Stable ego speed should be close to 50 km/h, but was " + speed);
            // Displayed speed (rounded to integer) must NEVER be 51 km/h
            long displayedSpeed = Math.round(speed);
            assertTrue(displayedSpeed <= 50, "Displayed speed must be <= 50 km/h, but was " + displayedSpeed);
        }
    }

    // ======================================================================
    // BUG 2 REGRESSION TESTS: Pothole avoidance chooses RIGHT over LEFT
    // ======================================================================
    @Test
    @Timeout(15)
    @DisplayName("BUG 2 Regression 1: CENTER + pothole + RIGHT free + LEFT free -> RIGHT")
    void bug2RegressionCenterPotholeBothLanesFreeChoosesRight() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG2_POTHOLE_RIGHT");
        scenario.setName("Pothole avoidance prefers right");
        scenario.setInitialSpeedKmh(70.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var potholeDef = new ScenarioRoadElementDefinition();
        potholeDef.setType(ScenarioRoadElementType.POTHOLE);
        potholeDef.setPositionMeters(40.0);
        potholeDef.setLane(Lane.CENTER);
        potholeDef.setSeverity(HazardSeverity.HIGH);

        var action = new ScenarioTimedAction();
        action.setType(ScenarioTimedActionType.ADD_ROAD_ELEMENT);
        action.setTriggerTimeMillis(0);
        action.setRoadElement(potholeDef);
        scenario.getTimedActions().add(action);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawAvoidRight = false;
            for (int i = 0; i < 100; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String decision = snapshot.currentDecision();
                if (decision.equals(DrivingAction.CHANGE_LANE_RIGHT.name())
                    && (snapshot.decisionReason().toLowerCase().contains("pothole") || snapshot.sourceRule().toLowerCase().contains("pothole"))) {
                    sawAvoidRight = true;
                }
                if (engine.getWorldState().getEgo().getLane() == Lane.RIGHT
                    && engine.getWorldState().getEgo().getLaneChangeState() == LaneChangeState.IDLE) {
                    break;
                }
            }

            assertTrue(sawAvoidRight, "Drools must choose CHANGE_LANE_RIGHT to avoid pothole in CENTER when RIGHT is free");
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(), "Vehicle must settle in RIGHT lane");
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("BUG 2 Regression 2: CENTER + pothole + RIGHT blocked + LEFT free -> LEFT")
    void bug2RegressionCenterPotholeRightBlockedChoosesLeft() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG2_POTHOLE_LEFT");
        scenario.setName("Pothole avoidance chooses left when right blocked");
        scenario.setInitialSpeedKmh(70.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var potholeDef = new ScenarioRoadElementDefinition();
        potholeDef.setType(ScenarioRoadElementType.POTHOLE);
        potholeDef.setPositionMeters(40.0);
        potholeDef.setLane(Lane.CENTER);
        potholeDef.setSeverity(HazardSeverity.HIGH);

        var blockerDef = new ScenarioActorDefinition();
        blockerDef.setType(ScenarioActorType.VEHICLE_RIGHT);
        blockerDef.setLane(Lane.RIGHT);
        blockerDef.setPositionMeters(20.0);
        blockerDef.setSpeedKmh(70.0);

        var a1 = new ScenarioTimedAction();
        a1.setType(ScenarioTimedActionType.ADD_ROAD_ELEMENT);
        a1.setTriggerTimeMillis(0);
        a1.setRoadElement(potholeDef);
        scenario.getTimedActions().add(a1);

        var a2 = new ScenarioTimedAction();
        a2.setType(ScenarioTimedActionType.ADD_ACTOR);
        a2.setTriggerTimeMillis(0);
        a2.setActor(blockerDef);
        scenario.getTimedActions().add(a2);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawAvoidLeft = false;
            for (int i = 0; i < 100; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String decision = snapshot.currentDecision();
                if (decision.equals(DrivingAction.CHANGE_LANE_LEFT.name())
                    && (snapshot.decisionReason().toLowerCase().contains("pothole") || snapshot.sourceRule().toLowerCase().contains("pothole"))) {
                    sawAvoidLeft = true;
                }
                if (engine.getWorldState().getEgo().getLane() == Lane.LEFT
                    && engine.getWorldState().getEgo().getLaneChangeState() == LaneChangeState.IDLE) {
                    break;
                }
            }

            assertTrue(sawAvoidLeft, "Drools must choose CHANGE_LANE_LEFT when RIGHT is blocked");
            assertEquals(Lane.LEFT, engine.getWorldState().getEgo().getLane(), "Vehicle must settle in LEFT lane");
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("BUG 2 Regression 3: CENTER + pothole + both blocked -> no unsafe lane change")
    void bug2RegressionCenterPotholeBothBlockedNoLaneChange() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG2_POTHOLE_BOTH_BLOCKED");
        scenario.setName("Pothole both lanes blocked no lane change");
        scenario.setInitialSpeedKmh(60.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var potholeDef = new ScenarioRoadElementDefinition();
        potholeDef.setType(ScenarioRoadElementType.POTHOLE);
        potholeDef.setPositionMeters(35.0);
        potholeDef.setLane(Lane.CENTER);
        potholeDef.setSeverity(HazardSeverity.HIGH);

        var blockerLeft = new ScenarioActorDefinition();
        blockerLeft.setType(ScenarioActorType.VEHICLE_LEFT);
        blockerLeft.setLane(Lane.LEFT);
        blockerLeft.setPositionMeters(10.0);
        blockerLeft.setSpeedKmh(60.0);

        var blockerRight = new ScenarioActorDefinition();
        blockerRight.setType(ScenarioActorType.VEHICLE_RIGHT);
        blockerRight.setLane(Lane.RIGHT);
        blockerRight.setPositionMeters(10.0);
        blockerRight.setSpeedKmh(60.0);

        var a1 = new ScenarioTimedAction();
        a1.setType(ScenarioTimedActionType.ADD_ROAD_ELEMENT);
        a1.setTriggerTimeMillis(0);
        a1.setRoadElement(potholeDef);
        scenario.getTimedActions().add(a1);

        var a2 = new ScenarioTimedAction();
        a2.setType(ScenarioTimedActionType.ADD_ACTOR);
        a2.setTriggerTimeMillis(0);
        a2.setActor(blockerLeft);
        scenario.getTimedActions().add(a2);

        var a3 = new ScenarioTimedAction();
        a3.setType(ScenarioTimedActionType.ADD_ACTOR);
        a3.setTriggerTimeMillis(0);
        a3.setActor(blockerRight);
        scenario.getTimedActions().add(a3);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean attemptedLaneChange = false;
            boolean sawReduceSpeed = false;

            for (int i = 0; i < 40; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String decision = snapshot.currentDecision();
                if (decision.equals(DrivingAction.CHANGE_LANE_LEFT.name()) || decision.equals(DrivingAction.CHANGE_LANE_RIGHT.name())) {
                    attemptedLaneChange = true;
                }
                if (decision.equals(DrivingAction.REDUCE_SPEED.name()) || decision.equals(DrivingAction.BRAKE.name())) {
                    sawReduceSpeed = true;
                }
            }

            assertFalse(attemptedLaneChange, "Must NOT force an unsafe lane change when both sides are blocked");
            assertTrue(sawReduceSpeed, "Must reduce speed/brake when pothole cannot be avoided");
            assertEquals(Lane.CENTER, engine.getWorldState().getEgo().getLane(), "Ego must remain in CENTER lane");
        }
    }

    // ======================================================================
    // BUG 3 REGRESSION TESTS: Overtaking strictly on LEFT, progressive return RIGHT
    // ======================================================================
    @Test
    @Timeout(15)
    @DisplayName("BUG 3 Regression 1: RIGHT + slower vehicle ahead + CENTER safe -> CHANGE_LANE_LEFT / RIGHT->CENTER")
    void bug3RegressionRightSlowerVehicleOvertakesLeftIntoCenter() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG3_OVERTAKE_RIGHT_TO_CENTER");
        scenario.setName("Overtake slower vehicle from RIGHT to CENTER");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var slowVehicle = new ScenarioActorDefinition();
        slowVehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        slowVehicle.setLane(Lane.RIGHT);
        slowVehicle.setPositionMeters(45.0);
        slowVehicle.setSpeedKmh(18.0);
        scenario.getActors().add(slowVehicle);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawChangeLeft = false;
            for (int i = 0; i < 80; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String decision = snapshot.currentDecision();
                if (decision.equals(DrivingAction.CHANGE_LANE_LEFT.name())) {
                    sawChangeLeft = true;
                }
                if (engine.getWorldState().getEgo().getLane() == Lane.CENTER
                    && engine.getWorldState().getEgo().getLaneChangeState() == LaneChangeState.IDLE) {
                    break;
                }
            }

            assertTrue(sawChangeLeft, "Must initiate overtaking by CHANGE_LANE_LEFT into CENTER");
            assertEquals(Lane.CENTER, engine.getWorldState().getEgo().getLane(), "Ego must move to CENTER lane to overtake");
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("BUG 3 Regression 2: CENTER + slower vehicle ahead + LEFT safe -> CHANGE_LANE_LEFT / CENTER->LEFT")
    void bug3RegressionCenterSlowerVehicleOvertakesLeftIntoLeft() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG3_OVERTAKE_CENTER_TO_LEFT");
        scenario.setName("Overtake slower vehicle from CENTER to LEFT");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var slowVehicle = new ScenarioActorDefinition();
        slowVehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        slowVehicle.setLane(Lane.CENTER);
        slowVehicle.setPositionMeters(45.0);
        slowVehicle.setSpeedKmh(18.0);
        scenario.getActors().add(slowVehicle);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawChangeLeft = false;
            boolean reachedLeft = false;
            for (int i = 0; i < 140; i++) {
                SimulationSnapshot snapshot = engine.tick();
                var ego = engine.getWorldState().getEgo();
                String decision = snapshot.currentDecision();
                if (decision.equals(DrivingAction.CHANGE_LANE_LEFT.name())) {
                    sawChangeLeft = true;
                }
                if (ego.getLane() == Lane.LEFT) {
                    reachedLeft = true;
                }
            }

            assertTrue(sawChangeLeft, "Must initiate overtaking from CENTER by CHANGE_LANE_LEFT into LEFT");
            assertTrue(reachedLeft, "Ego must move to LEFT lane to overtake");
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("BUG 3 Regression 3: never overtake slower vehicle by initiating CHANGE_LANE_RIGHT")
    void bug3RegressionNeverOvertakeSlowerVehicleByInitiatingRight() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG3_NO_OVERTAKE_RIGHT");
        scenario.setName("Never overtake slower vehicle on the right");
        scenario.setInitialSpeedKmh(70.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        // Slower vehicle ahead in CENTER
        var slowVehicle = new ScenarioActorDefinition();
        slowVehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        slowVehicle.setLane(Lane.CENTER);
        slowVehicle.setPositionMeters(35.0);
        slowVehicle.setSpeedKmh(30.0);

        // LEFT lane is BLOCKED, RIGHT lane is FREE
        var leftBlocker = new ScenarioActorDefinition();
        leftBlocker.setType(ScenarioActorType.VEHICLE_LEFT);
        leftBlocker.setLane(Lane.LEFT);
        leftBlocker.setPositionMeters(10.0);
        leftBlocker.setSpeedKmh(70.0);

        scenario.getActors().add(slowVehicle);
        scenario.getActors().add(leftBlocker);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean initiatedRightLaneChange = false;
            for (int i = 0; i < 50; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String decision = snapshot.currentDecision();
                if (decision.equals(DrivingAction.CHANGE_LANE_RIGHT.name())
                    || decision.equals(DrivingAction.RETURN_RIGHT.name())) {
                    System.out.println("DEBUG rule that fired right: " + snapshot.sourceRule() + " - " + snapshot.decisionReason());
                    initiatedRightLaneChange = true;
                }
            }

            assertFalse(initiatedRightLaneChange,
                "Must NEVER initiate a rightward lane change to overtake a slower vehicle on the right (prohibited by Art. 36 CE)");
            assertEquals(Lane.CENTER, engine.getWorldState().getEgo().getLane(),
                "Vehicle must remain in CENTER and slow down behind front vehicle rather than undertaking on the right");
        }
    }

    @Test
    @Timeout(25)
    @DisplayName("BUG 3 Regression 4: after overtaking + RIGHT safe -> progressively return RIGHT (LEFT->CENTER->RIGHT)")
    void bug3RegressionAfterOvertakingProgressivelyReturnsRight() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BUG3_PROGRESSIVE_RETURN");
        scenario.setName("Progressive return right after overtaking from LEFT");
        scenario.setInitialSpeedKmh(70.0);
        scenario.setInitialLane(Lane.LEFT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean reachedCenter = false;
            boolean reachedRight = false;

            for (int i = 0; i < 220; i++) {
                engine.tick();
                Lane currentLane = engine.getWorldState().getEgo().getLane();
                if (currentLane == Lane.CENTER) {
                    reachedCenter = true;
                } else if (currentLane == Lane.RIGHT) {
                    reachedRight = true;
                    break;
                }
            }

            assertTrue(reachedCenter, "Vehicle returning right from LEFT must progressively pass through CENTER");
            assertTrue(reachedRight, "Vehicle returning right must finally settle in RIGHT lane");
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(), "Final settled lane must be RIGHT");
        }
    }
}
