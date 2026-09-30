package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineE2ETest {
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
            for (int i = 0; i < 45; i++) {
                SimulationSnapshot s = engine.tick();
                detected = detected || s.timeline().stream().anyMatch(line -> line.contains("Camera read speed limit 50 km/h"));
                ruleFired = ruleFired || s.timeline().stream().anyMatch(line -> line.contains("Reduce speed when exceeding detected limit"));
                decisionReduce = decisionReduce || DrivingAction.REDUCE_SPEED.name().equals(s.currentDecision());
                if (detected && ruleFired && decisionReduce) {
                    break;
                }
            }

            SimulationSnapshot snapshot = engine.snapshot();
            assertTrue(detected);
            assertTrue(ruleFired);
            assertTrue(decisionReduce || snapshot.currentDecision().equals(DrivingAction.MAINTAIN_SPEED.name()));
            assertTrue(snapshot.recommendedSpeedKmh() <= 50.1);

            double speedBefore = snapshot.egoSpeedKmh();
            for (int i = 0; i < 25; i++) {
                engine.tick();
            }
            double speedAfter = engine.snapshot().egoSpeedKmh();
            assertTrue(speedAfter < speedBefore);

            for (int i = 0; i < 40; i++) {
                engine.tick();
            }
            assertTrue(engine.snapshot().egoSpeedKmh() <= 51.5);
            assertTrue(engine.snapshot().currentDecision().equals(DrivingAction.MAINTAIN_SPEED.name())
                || engine.snapshot().currentDecision().equals(DrivingAction.CONTINUE.name())
                || engine.snapshot().currentDecision().equals(DrivingAction.REDUCE_SPEED.name()));
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
            frontVehicle.setLane(Lane.CENTER);
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

    @Test
    @Timeout(12)
    void dynamicWorldMutationPotholeDuringRunTriggersReaction() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
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
                reacted = reacted || snapshot.currentDecision().equals(DrivingAction.REDUCE_SPEED.name())
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

    @Test
    @Timeout(12)
    void weatherMutationDuringRunChangesRecommendationsAndVehicleBehavior() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
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
            assertTrue(snapshot.timeline().stream().anyMatch(line -> line.contains("Low visibility requires stronger speed reduction")
                || line.contains("Low road grip requires stronger speed reduction")
                || line.contains("Reduced grip requires cautious speed")
                || line.contains("Reduced visibility requires conservative speed")));
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
            frontVehicle.setLane(Lane.CENTER);
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
                .anyMatch(view -> view.type().equals("CurrentSpeedLimit") && view.value().contains("limitKmh=50"));
            assertTrue(sawDetection, "Signed scenario should be perceived during the run");
            assertFalse(engine.getWorldState().getRoadElements().isEmpty());

            engine.reset(empty);

            assertEquals(0, engine.snapshot().simTimeMillis());
            assertEquals(0, engine.getWorldState().getRoadElements().size());
            assertTrue(engine.getWorkingMemoryFactViews().stream()
                .noneMatch(view -> view.type().equals("CurrentSpeedLimit") && view.value().contains("limitKmh=50")));
            assertTrue(engine.getWorldState().getActors().isEmpty());
            assertTrue(engine.getDemoEvidence().isEmpty());
        }
    }
}
