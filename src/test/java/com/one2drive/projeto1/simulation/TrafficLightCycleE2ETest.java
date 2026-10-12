package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Yellow is the transition from green to red: the ego stops before the line when it still
 * can, drives on when it no longer can, and does not change its mind. The light then runs
 * its cycle in the world: red after the yellow time, green once the ego has waited the red time.
 */
class TrafficLightCycleE2ETest {

    /** The default configuration with other traffic light times. */
    private static SimulationConfig configWithLightTimes(double yellowSeconds, double redSeconds) {
        SimulationConfig d = SimulationConfig.defaultConfig();
        return new SimulationConfig(
            d.getTickMillis(), d.getMapBlockLengthMeters(), d.getDefaultMapRows(), d.getMaxAccelerationMps2(),
            d.getComfortableDecelerationMps2(), d.getEmergencyDecelerationMps2(), d.getDefaultCruiseSpeedKmh(),
            d.getLaneChangeSeconds(), d.getLaneChangeMetersPerSecond(), d.getRadarRangeMeters(),
            d.getCameraRangeMeters(), d.getBlindSpotRangeMeters(), d.getEnvironmentRangeMeters(),
            d.getRadarRateMillis(), d.getCameraRateMillis(), d.getBlindSpotRateMillis(),
            d.getEnvironmentRateMillis(), d.getSpeedSensorRateMillis(), d.getDistanceNoiseStdMeters(),
            yellowSeconds, Math.round(redSeconds * 1000.0));
    }

    private static ScenarioRoadElementDefinition light(TrafficLightState state, double positionMeters) {
        ScenarioRoadElementDefinition light = new ScenarioRoadElementDefinition();
        light.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        light.setLane(Lane.RIGHT);
        light.setPositionMeters(positionMeters);
        light.setTrafficLightState(state);
        return light;
    }

    private static SimulationEngine startedEngine(SimulationConfig config, double speedKmh,
                                                 ScenarioRoadElementDefinition... elements) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("TRAFFIC_LIGHT_CYCLE");
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialLane(Lane.RIGHT);
        for (ScenarioRoadElementDefinition element : elements) {
            scenario.getRoadElements().add(element);
        }
        SimulationEngine engine = new SimulationEngine(config, scenario, true, d -> { });
        engine.start();
        return engine;
    }

    private static TrafficLight onlyLight(SimulationEngine engine) {
        return engine.getWorldState().getRoadElements().stream()
            .filter(TrafficLight.class::isInstance).map(TrafficLight.class::cast).findFirst().orElseThrow();
    }

    @Test
    @Timeout(30)
    @DisplayName("yellow far ahead at 60 km/h: the ego can stop, and is at rest before the stop line")
    void yellowFarAheadStopsBeforeTheLine() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 60.0,
            light(TrafficLightState.YELLOW, 80.0))) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            TrafficLight light = onlyLight(engine);
            double stopLine = light.getPositionMeters() - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

            boolean stopped = false;
            boolean emergencyBrake = false;
            boolean spedUp = false;
            for (int i = 0; i < 200 && !stopped; i++) {
                double before = ego.getSpeedKmh();
                SimulationSnapshot snapshot = engine.tick();
                emergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                spedUp |= ego.getSpeedKmh() > before + 0.01;
                stopped = ego.getSpeedKmh() < 0.05;
            }

            assertTrue(stopped, "the ego must come to rest");
            assertFalse(light.getState() == TrafficLightState.GREEN, "the ego must be at rest before the light is green again");
            assertTrue(ego.getPositionMeters() <= stopLine,
                "the ego must be at rest before the stop line at " + stopLine + " m, it stopped at " + ego.getPositionMeters());
            assertFalse(emergencyBrake, "a yellow with room to stop must not need the emergency brake");
            assertFalse(spedUp, "the ego must not speed up towards a yellow light it is stopping for");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("stopped at yellow: stays stopped when it turns red, and moves off only when it turns green once the ego has waited the red time")
    void staysStoppedThroughRedAndDrivesOnAtGreen() {
        double yellowSeconds = 10.0;
        double redSeconds = 5.0;
        try (SimulationEngine engine = startedEngine(configWithLightTimes(yellowSeconds, redSeconds), 50.0,
            light(TrafficLightState.YELLOW, 80.0))) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            TrafficLight light = onlyLight(engine);
            double stopLine = light.getPositionMeters() - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

            boolean stoppedDuringYellow = false;
            double restPosition = Double.NaN;
            int redTicks = 0;
            boolean movedDuringRed = false;
            boolean reachedGreen = false;
            for (int i = 0; i < 400 && !reachedGreen; i++) {
                engine.tick();
                TrafficLightState state = light.getState();
                if (state == TrafficLightState.YELLOW && ego.getSpeedKmh() < 0.05 && !stoppedDuringYellow) {
                    stoppedDuringYellow = true;
                    restPosition = ego.getPositionMeters();
                }
                if (state == TrafficLightState.RED) {
                    redTicks++;
                    movedDuringRed |= ego.getSpeedKmh() > 0.05 || Math.abs(ego.getPositionMeters() - restPosition) > 0.01;
                }
                reachedGreen = state == TrafficLightState.GREEN;
            }

            assertTrue(stoppedDuringYellow, "the ego must be at rest while the light is still yellow");
            assertTrue(restPosition <= stopLine, "the ego must be at rest before the stop line, it stopped at " + restPosition);
            assertEquals(redSeconds * 10, redTicks, 1.0, "the red phase must last the configured " + redSeconds + " s");
            assertFalse(movedDuringRed, "the ego must stay stopped, in the same place, while the light is red");
            assertTrue(reachedGreen, "the light must turn green once the ego has waited the red time");

            for (int i = 0; i < 150; i++) {
                engine.tick();
            }
            assertTrue(ego.getPositionMeters() > light.getPositionMeters(),
                "on green the ego must drive past the light, it is at " + ego.getPositionMeters());
            assertTrue(ego.getSpeedKmh() > 30.0, "on green the ego must build its speed back up, it is at " + ego.getSpeedKmh());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("yellow too close to stop safely: the ego drives on, does not brake across the light and is not held after it")
    void yellowTooCloseIsDrivenThroughWithoutBraking() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 60.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            engine.addRoadElementAhead(light(TrafficLightState.YELLOW, 50.0));
            engine.tick();
            TrafficLight light = onlyLight(engine);

            double slowest = ego.getSpeedKmh();
            boolean braked = false;
            boolean sawRedWhilePassing = false;
            for (int i = 0; i < 150; i++) {
                SimulationSnapshot snapshot = engine.tick();
                slowest = Math.min(slowest, ego.getSpeedKmh());
                String decision = snapshot.currentDecision();
                braked |= decision.equals(DrivingAction.STOP.name()) || decision.equals(DrivingAction.BRAKE.name())
                    || decision.equals(DrivingAction.REDUCE_SPEED.name()) || decision.equals(DrivingAction.EMERGENCY_BRAKE.name());
                sawRedWhilePassing |= light.getState() == TrafficLightState.RED;
            }

            assertTrue(sawRedWhilePassing, "the light must have turned red during the run");
            assertTrue(ego.getPositionMeters() > light.getPositionMeters() + 100.0,
                "the ego must clear the light and keep going, it is at " + ego.getPositionMeters());
            assertFalse(braked, "the ego must not brake for a yellow it cannot stop for");
            assertEquals(60.0, slowest, 1.0, "the ego must keep its speed across the light and after it");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("committed to drive on at yellow, the light turns red before the ego has passed: it still drives on")
    void keepsDrivingOnWhenTheYellowTurnsRedBeforeItHasPassed() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 20.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            // 25 m at 20 km/h is 4.5 s away: closer than a comfortable stop before the line
            // allows, and further than the 3 s the light stays yellow.
            engine.addRoadElementAhead(light(TrafficLightState.YELLOW, 25.0));
            engine.tick();
            TrafficLight light = onlyLight(engine);

            double slowest = ego.getSpeedKmh();
            boolean redBeforePassing = false;
            for (int i = 0; i < 120; i++) {
                engine.tick();
                slowest = Math.min(slowest, ego.getSpeedKmh());
                redBeforePassing |= light.getState() == TrafficLightState.RED
                    && ego.getPositionMeters() < light.getPositionMeters();
            }

            assertTrue(redBeforePassing, "the light must have turned red with the ego still before it");
            assertTrue(ego.getPositionMeters() > light.getPositionMeters(), "the ego must have cleared the light");
            assertTrue(slowest >= 19.0, "the ego must not brake once committed to drive on, it slowed to " + slowest);
        }
    }

    private static String lightFact(SimulationEngine engine) {
        return engine.getWorkingMemoryFactViews().stream()
            .filter(f -> f.type().equals("TrafficLightStateFact")).map(f -> f.value()).findFirst().orElse("");
    }

    @Test
    @Timeout(40)
    @DisplayName("yellow detected far away: committed to stop from the first detection, progressive approach, never changes to drive on")
    void yellowDetectedFarAwayCommitsToStopFromTheFirstDetectionAndNeverChanges() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 30.0,
            light(TrafficLightState.YELLOW, 115.0))) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            TrafficLight light = onlyLight(engine);
            double stopLine = light.getPositionMeters() - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

            String commitmentAtFirstDetection = null;
            double distanceAtFirstDetection = Double.NaN;
            boolean everAnythingButStop = false;
            double distanceAtFirstBraking = Double.NaN;
            boolean emergencyBrake = false;
            boolean stopped = false;
            for (int i = 0; i < 300 && !stopped; i++) {
                double speedBefore = ego.getSpeedKmh();
                SimulationSnapshot snapshot = engine.tick();
                String fact = lightFact(engine);
                boolean detected = !fact.contains("lightId='none'");
                if (detected && commitmentAtFirstDetection == null) {
                    commitmentAtFirstDetection = fact;
                    distanceAtFirstDetection = light.getPositionMeters() - ego.getPositionMeters();
                    assertEquals(TrafficLightState.YELLOW, light.getState(), "the light must still be yellow when first seen");
                }
                if (detected) {
                    everAnythingButStop |= !fact.contains("commitment=COMMITTED_TO_STOP");
                }
                if (Double.isNaN(distanceAtFirstBraking) && ego.getSpeedKmh() < speedBefore - 0.01) {
                    distanceAtFirstBraking = light.getPositionMeters() - ego.getPositionMeters();
                }
                emergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                stopped = ego.getSpeedKmh() < 0.05;
            }

            assertTrue(commitmentAtFirstDetection != null, "the light must have been detected");
            assertTrue(distanceAtFirstDetection > 100.0, "the light must have been detected far away, it was at " + distanceAtFirstDetection);
            assertTrue(commitmentAtFirstDetection.contains("commitment=COMMITTED_TO_STOP"),
                "the decision to stop must be made at the first detection: " + commitmentAtFirstDetection);
            assertFalse(everAnythingButStop, "the commitment to stop must never change during the approach");
            // Committed to stop is not braking at once: at 30 km/h the brakes are not needed before 40 m.
            assertTrue(distanceAtFirstBraking < 60.0,
                "the approach must be progressive, braking started " + distanceAtFirstBraking + " m from the light");
            assertFalse(emergencyBrake, "a light seen from afar must not need the emergency brake");
            assertTrue(stopped, "the ego must come to rest");
            assertTrue(ego.getPositionMeters() <= stopLine,
                "the ego must be at rest before the stop line at " + stopLine + " m, it stopped at " + ego.getPositionMeters());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("committed to drive on: once the ego has cleared the light, the light's state and the commitment are cleared")
    void commitmentIsClearedOnceTheLightIsBehind() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 60.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            engine.addRoadElementAhead(light(TrafficLightState.YELLOW, 50.0));
            engine.tick();
            TrafficLight light = onlyLight(engine);

            boolean committedToProceed = false;
            for (int i = 0; i < 100 && ego.getPositionMeters() < light.getPositionMeters(); i++) {
                engine.tick();
                committedToProceed |= lightFact(engine).contains("commitment=COMMITTED_TO_PROCEED");
            }
            assertTrue(committedToProceed, "the ego must have committed to drive on");

            // The camera no longer reports a light that is behind; the fact is cleared 1 s later.
            for (int i = 0; i < 15; i++) {
                engine.tick();
            }
            String fact = lightFact(engine);
            assertTrue(fact.contains("lightId='none'"), "the passed light must no longer be in working memory: " + fact);
            assertTrue(fact.contains("commitment=NONE"), "the commitment must be cleared with it: " + fact);
            assertEquals(60.0, ego.getSpeedKmh(), 1.0, "nothing of that light must hold the ego afterwards");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("red light first seen 15 m ahead at 60 km/h, already past the stop line: never keeps its speed, brakes as hard as it can while the light is ahead")
    void redLightFirstSeenPastTheStopLineIsBrakedForHard() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 60.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            engine.addRoadElementAhead(light(TrafficLightState.RED, 15.0));
            TrafficLight light = null;

            boolean keptSpeedWithRedAhead = false;
            boolean emergencyBrake = false;
            boolean stopped = false;
            double speedWhenDetected = Double.NaN;
            double slowestWithLightAhead = Double.NaN;
            for (int i = 0; i < 100 && !stopped; i++) {
                SimulationSnapshot snapshot = engine.tick();
                if (light == null) {
                    light = onlyLight(engine);
                }
                boolean redAheadAndSeen = lightFact(engine).contains("state=RED")
                    && ego.getPositionMeters() < light.getPositionMeters();
                if (redAheadAndSeen) {
                    if (Double.isNaN(speedWhenDetected)) {
                        speedWhenDetected = ego.getSpeedKmh();
                    }
                    String decision = snapshot.currentDecision();
                    keptSpeedWithRedAhead |= decision.equals(DrivingAction.MAINTAIN_SPEED.name())
                        || decision.equals(DrivingAction.CONTINUE.name()) || decision.equals(DrivingAction.ACCELERATE.name());
                    emergencyBrake |= decision.equals(DrivingAction.EMERGENCY_BRAKE.name());
                    slowestWithLightAhead = ego.getSpeedKmh();
                }
                stopped = ego.getSpeedKmh() < 0.05;
            }

            assertEquals(60.0, speedWhenDetected, 1.0, "the light must have been seen with the ego at speed");
            assertFalse(keptSpeedWithRedAhead, "with a red light seen ahead the ego must never keep or gain speed");
            assertTrue(emergencyBrake, "this close and this fast the answer must be the emergency brake");
            // 15 m is less than even an emergency stop from 60 km/h needs: what can be asked is
            // that the ego sheds as much speed as the emergency brake allows before the light.
            assertTrue(slowestWithLightAhead < 35.0,
                "the ego must have braked hard while the light was ahead, it was still at " + slowestWithLightAhead + " km/h");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("red light first seen past the stop line at low speed: comfortable stop, at rest before the light, held, green after the red time")
    void redLightFirstSeenPastTheStopLineAtLowSpeedStopsComfortablyBeforeTheLightAndHolds() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 25.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            engine.addRoadElementAhead(light(TrafficLightState.RED, 15.0));
            engine.tick();
            TrafficLight light = onlyLight(engine);

            boolean emergencyBrake = false;
            boolean keptSpeedWithRedAhead = false;
            boolean stopped = false;
            for (int i = 0; i < 100 && !stopped; i++) {
                SimulationSnapshot snapshot = engine.tick();
                String decision = snapshot.currentDecision();
                emergencyBrake |= decision.equals(DrivingAction.EMERGENCY_BRAKE.name());
                if (lightFact(engine).contains("state=RED")) {
                    keptSpeedWithRedAhead |= decision.equals(DrivingAction.MAINTAIN_SPEED.name())
                        || decision.equals(DrivingAction.CONTINUE.name()) || decision.equals(DrivingAction.ACCELERATE.name());
                }
                stopped = ego.getSpeedKmh() < 0.05;
            }
            assertFalse(emergencyBrake, "a comfortable stop still fits before the light: no emergency brake");
            assertFalse(keptSpeedWithRedAhead, "with a red light seen ahead the ego must never keep or gain speed");
            assertTrue(stopped, "the ego must come to rest");
            assertTrue(ego.getPositionMeters() < light.getPositionMeters(),
                "the ego must be at rest before the light, it is at " + ego.getPositionMeters());
            double restPosition = ego.getPositionMeters();

            // Held where it stopped while the light is red, then released by the green.
            int heldCycles = 0;
            for (int i = 0; i < 100 && light.getState() == TrafficLightState.RED; i++) {
                SimulationSnapshot snapshot = engine.tick();
                if (light.getState() == TrafficLightState.RED) {
                    heldCycles++;
                    assertEquals(restPosition, ego.getPositionMeters(), 0.01, "the ego must hold its position at the red light");
                    assertEquals(DrivingAction.STOP.name(), snapshot.currentDecision());
                }
            }
            assertEquals(TrafficLightState.GREEN, light.getState(), "the light must turn green once the ego has waited at it");
            assertEquals(50.0, heldCycles, 1.0, "the ego must have been held for the red time");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("red light at a normal distance: the approach stays progressive, with no emergency brake")
    void redLightAtANormalDistanceIsStillApproachedProgressively() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 60.0,
            light(TrafficLightState.RED, 115.0))) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            TrafficLight light = onlyLight(engine);
            double stopLine = light.getPositionMeters() - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

            boolean emergencyBrake = false;
            boolean stopped = false;
            for (int i = 0; i < 300 && !stopped; i++) {
                SimulationSnapshot snapshot = engine.tick();
                emergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                stopped = ego.getSpeedKmh() < 0.05;
            }

            assertTrue(stopped, "the ego must come to rest");
            assertFalse(emergencyBrake, "a red light seen from afar must not need the emergency brake");
            assertTrue(ego.getPositionMeters() <= stopLine,
                "the ego must be at rest before the stop line at " + stopLine + " m, it stopped at " + ego.getPositionMeters());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("red light already behind the ego: it is no longer considered and does not brake the ego")
    void redLightBehindTheEgoDoesNotBrakeIt() {
        try (SimulationEngine engine = startedEngine(SimulationConfig.defaultConfig(), 60.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            // Committed to drive on at yellow; the light is red by the time it is behind.
            engine.addRoadElementAhead(light(TrafficLightState.YELLOW, 50.0));
            engine.tick();
            TrafficLight light = onlyLight(engine);
            for (int i = 0; i < 100 && ego.getPositionMeters() <= light.getPositionMeters(); i++) {
                engine.tick();
            }
            for (int i = 0; i < 15; i++) {
                engine.tick();
            }
            assertEquals(TrafficLightState.RED, light.getState(), "the light behind the ego must be red by now");

            double slowest = ego.getSpeedKmh();
            for (int i = 0; i < 100; i++) {
                SimulationSnapshot snapshot = engine.tick();
                slowest = Math.min(slowest, ego.getSpeedKmh());
                assertFalse(snapshot.sourceRule().toLowerCase().contains("traffic light"),
                    "a light behind the ego must not decide anything: " + snapshot.sourceRule());
            }
            assertTrue(lightFact(engine).contains("lightId='none'"), lightFact(engine));
            assertEquals(60.0, slowest, 1.0, "a red light behind the ego must not brake it, it slowed to " + slowest);
        }
    }
}
