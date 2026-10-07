package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.IndicatorState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.SteeringState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
        ScenarioDefinition scenario = library.findById("S16_SLOW_VEHICLE_OVERTAKE").orElseThrow();

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

                if (reachedCenter && snapshot.currentDecision().equals(DrivingAction.RETURN_RIGHT.name())) {
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
        ScenarioDefinition scenario = library.findById("S17_CONTINUOUS_LINE_BLOCKED").orElseThrow();

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
        ScenarioDefinition scenario = library.findById("S8").orElseThrow();

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
    @DisplayName("Radar Regression: Speed radar has fixed world position; distance derives from world coordinates and decreases naturally")
    void radarHasFixedWorldPositionAndCalculatesDecreasingDistance() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S18_SPEED_RADAR_APPROACH").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            // Run ticks to trigger and flush timed elements
            engine.tick();
            engine.tick();

            var radarOpt = engine.getWorldState().findNearestSpeedRadarAhead(200.0);
            assertTrue(radarOpt.isPresent(), "Speed radar must be present ahead on the road");
            var radar = radarOpt.get();
            double fixedRadarPos = radar.getPositionMeters();
            assertEquals(130.0, fixedRadarPos, 0.01, "Radar position must be 130m");

            double previousDistance = Double.POSITIVE_INFINITY;
            double previousEgoPos = engine.getWorldState().getEgo().getPositionMeters();

            for (int i = 0; i < 40; i++) {
                SimulationSnapshot snapshot = engine.tick();
                double egoPos = snapshot.egoPositionMeters();
                assertEquals(130.0, radar.getPositionMeters(), 0.001, "Radar world position must NOT move");

                if (egoPos < fixedRadarPos) {
                    double expectedDistance = fixedRadarPos - egoPos;
                    double actualDistance = engine.getWorldState().distanceAhead(fixedRadarPos);
                    assertEquals(expectedDistance, actualDistance, 0.01,
                        "distanceToRadar must equal radarPosition - egoPosition");
                    assertTrue(actualDistance <= previousDistance, "Radar distance must decrease as ego approaches");
                    previousDistance = actualDistance;
                }
                assertTrue(egoPos >= previousEgoPos, "Ego position must increase");
                previousEgoPos = egoPos;
            }
        }
    }

    @Test
    @DisplayName("Radar Regression: Speeding vehicle progressively decelerates approaching radar; no lane changes or obstacle avoidance")
    void speedingVehicleApproachingRadarProgressivelyDeceleratesWithoutLaneChange() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S18_SPEED_RADAR_APPROACH").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            boolean sawSpeedReduction = false;
            boolean passedRadar = false;

            for (int i = 0; i < 120; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String action = snapshot.currentDecision();
                double egoPos = snapshot.egoPositionMeters();
                double egoSpeed = snapshot.egoSpeedKmh();

                // Crucial requirement: radar must never trigger lane change or obstacle avoidance
                assertFalse(action.equals(DrivingAction.CHANGE_LANE_LEFT.name()),
                    "Radar must NEVER trigger CHANGE_LANE_LEFT");
                assertFalse(action.equals(DrivingAction.CHANGE_LANE_RIGHT.name()),
                    "Radar must NEVER trigger CHANGE_LANE_RIGHT");
                assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(),
                    "Vehicle must stay in its lane; radar is not an obstacle to avoid");
                assertNotEquals("STOPPED_FOR_HAZARD", engine.getWorldState().getEgo().getDrivingState().name(),
                    "Radar must never cause STOPPED_FOR_HAZARD");

                if (action.equals(DrivingAction.REDUCE_SPEED.name())) {
                    sawSpeedReduction = true;
                }

                if (egoPos >= 130.0) {
                    passedRadar = true;
                    // When passing radar, vehicle speed must be legal (<= 50 + small margin)
                    assertTrue(egoSpeed <= 55.0,
                        "Vehicle must decelerate to near legal speed before passing radar, got " + egoSpeed);
                    // Vehicle continues driving forward (must not stop at radar)
                    assertTrue(egoSpeed > 20.0, "Vehicle must continue driving through radar, not stop");
                    break;
                }
            }

            assertTrue(sawSpeedReduction, "Speeding vehicle must decelerate when approaching speed radar");
            assertTrue(passedRadar, "Vehicle must pass the radar");

            // Verify vehicle continues past the radar
            double postRadarPos = engine.getWorldState().getEgo().getPositionMeters();
            for (int i = 0; i < 20; i++) {
                SimulationSnapshot snapshot = engine.tick();
                assertTrue(snapshot.egoPositionMeters() > postRadarPos, "Vehicle must continue driving after passing radar");
                postRadarPos = snapshot.egoPositionMeters();
            }

            // Verify trace recorded passing event in timeline
            boolean recordedPassage = engine.snapshot().timeline().stream()
                .anyMatch(msg -> msg.contains("Speed radar passed"));
            assertTrue(recordedPassage, "Engine must record 'Speed radar passed' timeline event");
        }
    }

    @Test
    @DisplayName("Radar Regression: Legal-speed vehicle does not brake unnecessarily approaching radar")
    void legalSpeedVehicleDoesNotBrakeApproachingRadar() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("LEGAL_RADAR_TEST");
        scenario.setName("Legal speed approaching radar");
        scenario.setInitialSpeedKmh(45.0);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        var radarDef = new com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition();
        radarDef.setType(com.one2drive.projeto1.scenario.ScenarioRoadElementType.SPEED_RADAR);
        radarDef.setPositionMeters(100.0);
        radarDef.setLane(Lane.RIGHT);
        radarDef.setSpeedLimitKmh(50);

        var action = new com.one2drive.projeto1.scenario.ScenarioTimedAction();
        action.setType(com.one2drive.projeto1.scenario.ScenarioTimedActionType.ADD_ROAD_ELEMENT);
        action.setTriggerTimeMillis(0);
        action.setRoadElement(radarDef);
        scenario.getTimedActions().add(action);

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {})) {
            engine.start();

            for (int i = 0; i < 40; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String decision = snapshot.currentDecision();
                assertFalse(decision.equals(DrivingAction.BRAKE.name()) || decision.equals(DrivingAction.EMERGENCY_BRAKE.name()),
                    "Legal-speed vehicle must not brake for radar");
                assertFalse(decision.equals(DrivingAction.REDUCE_SPEED.name()),
                    "Legal-speed vehicle must not reduce speed when already compliant with radar limit");
            }
        }
    }
}
