package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A red light is respected in the world, not only in the decision: the ego must be at rest
 * before the stop line, StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS short of the light,
 * stay there while the light is red and drive on when it turns green.
 */
class RedTrafficLightStopE2ETest {
    private static final double LIGHT_POSITION_METERS = 118.0;
    private static final double STOP_LINE_METERS =
        LIGHT_POSITION_METERS - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

    private static ScenarioDefinition redLightScenario(double speedKmh) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("RED_LIGHT_STOP_" + Math.round(speedKmh));
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialLane(Lane.RIGHT);
        ScenarioRoadElementDefinition red = new ScenarioRoadElementDefinition();
        red.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        red.setLane(Lane.RIGHT);
        red.setPositionMeters(LIGHT_POSITION_METERS);
        red.setTrafficLightState(TrafficLightState.RED);
        scenario.getRoadElements().add(red);
        return scenario;
    }

    private static SimulationEngine startedEngine(ExpertBackendType backend, double speedKmh) {
        SimulationEngine engine = BackendTestSupport.engine(redLightScenario(speedKmh), backend);
        engine.start();
        return engine;
    }

    private static TrafficLight light(SimulationEngine engine) {
        return engine.getWorldState().getRoadElements().stream()
            .filter(TrafficLight.class::isInstance).map(TrafficLight.class::cast).findFirst().orElseThrow();
    }

    /** What the world did during an approach, read from WorldState. */
    private record Approach(double restPositionMeters, double furthestPositionMeters,
                            double firstBrakingPositionMeters, boolean emergencyBrake, boolean stopped) {
    }

    private static Approach driveUntilStopped(SimulationEngine engine) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        double firstBraking = Double.NaN;
        boolean emergency = false;
        boolean stopped = false;
        double furthest = ego.getPositionMeters();
        for (int i = 0; i < 400 && !stopped; i++) {
            double speedBefore = ego.getSpeedKmh();
            SimulationSnapshot snapshot = engine.tick();
            furthest = Math.max(furthest, ego.getPositionMeters());
            emergency |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
            if (Double.isNaN(firstBraking) && ego.getSpeedKmh() < speedBefore - 0.01) {
                firstBraking = ego.getPositionMeters();
            }
            stopped = ego.getSpeedKmh() < 0.05;
        }
        return new Approach(ego.getPositionMeters(), furthest, firstBraking, emergency, stopped);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("red light at 70 km/h: the ego brakes early, without an emergency brake, and is at rest before the stop line")
    void at70KmhBrakesEarlyAndRestsBeforeTheStopLine(ExpertBackendType backend) {
        try (SimulationEngine engine = startedEngine(backend, 70.0)) {
            Approach approach = driveUntilStopped(engine);

            assertTrue(approach.stopped(), "the ego must come to rest");
            double needed = StoppingPolicy.brakingDistanceMeters(70.0, StoppingPolicy.COMFORT_DECELERATION_MPS2);
            assertTrue(STOP_LINE_METERS - approach.firstBrakingPositionMeters() >= needed,
                "braking must start with at least the comfortable braking distance (" + needed
                    + " m) left before the stop line, it started " + (STOP_LINE_METERS - approach.firstBrakingPositionMeters())
                    + " m before it");
            assertFalse(approach.emergencyBrake(), "a light seen from afar must not need the emergency brake");
            assertTrue(approach.restPositionMeters() <= STOP_LINE_METERS,
                "the ego must be at rest before the stop line at " + STOP_LINE_METERS + " m, it stopped at "
                    + approach.restPositionMeters() + " m");
            assertEquals(0.0, engine.getWorldState().getEgo().getSpeedKmh(), 0.05);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("one red light governs the whole road: the ego stops at its stop line whichever lane it drives in")
    void oneLightStopsTheEgoInEveryLane() {
        for (Lane egoLane : new Lane[]{Lane.LEFT, Lane.CENTER, Lane.RIGHT}) {
            ScenarioDefinition scenario = new ScenarioDefinition();
            scenario.setId("RED_LIGHT_ALL_LANES_" + egoLane);
            scenario.setInitialSpeedKmh(50.0);
            scenario.setInitialLane(egoLane);
            // Keeps the ego in its lane, so each run shows one lane against the one light.
            scenario.setLeftMarking(com.one2drive.projeto1.domain.RoadMarkingType.CONTINUOUS);
            scenario.setRightMarking(com.one2drive.projeto1.domain.RoadMarkingType.CONTINUOUS);
            ScenarioRoadElementDefinition red = new ScenarioRoadElementDefinition();
            red.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
            red.setLane(Lane.CENTER);
            red.setPositionMeters(LIGHT_POSITION_METERS);
            red.setTrafficLightState(TrafficLightState.RED);
            scenario.getRoadElements().add(red);

            try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
                assertEquals(1, engine.getWorldState().getRoadElements().stream()
                    .filter(TrafficLight.class::isInstance).count(), "one light in the world stands for all three lanes");
                engine.start();
                Approach approach = driveUntilStopped(engine);

                assertEquals(egoLane, engine.getWorldState().getEgo().getLane());
                assertTrue(approach.stopped(), "ego in " + egoLane + " must stop for the light");
                assertTrue(approach.furthestPositionMeters() <= STOP_LINE_METERS,
                    "ego in " + egoLane + " must not pass the stop line at " + STOP_LINE_METERS
                        + " m, it reached " + approach.furthestPositionMeters() + " m");
                assertTrue(approach.restPositionMeters() >= STOP_LINE_METERS - 2.0 * StoppingPolicy.STOP_LINE_TOLERANCE_METERS,
                    "ego in " + egoLane + " must stop at the line, it stopped at " + approach.restPositionMeters() + " m");
            }
        }
    }

    @ParameterizedTest(name = "{0}")

    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("red light at 30, 50, 70 and 90 km/h: the ego never passes the stop line and does not stop needlessly far from it")
    void neverPassesTheStopLineAtAnySpeed(ExpertBackendType backend) {
        for (double speedKmh : new double[]{30.0, 50.0, 70.0, 90.0}) {
            try (SimulationEngine engine = startedEngine(backend, speedKmh)) {
                Approach approach = driveUntilStopped(engine);

                assertTrue(approach.stopped(), speedKmh + " km/h: the ego must come to rest");
                assertTrue(approach.furthestPositionMeters() <= STOP_LINE_METERS,
                    speedKmh + " km/h: the ego must never pass the stop line at " + STOP_LINE_METERS
                        + " m, it reached " + approach.furthestPositionMeters() + " m");
                assertTrue(approach.furthestPositionMeters() < LIGHT_POSITION_METERS,
                    speedKmh + " km/h: the ego must never pass the light");
                assertTrue(approach.restPositionMeters() >= STOP_LINE_METERS - 2.0 * StoppingPolicy.STOP_LINE_TOLERANCE_METERS,
                    speedKmh + " km/h: the ego must stop at the line, not far behind it; it stopped at "
                        + approach.restPositionMeters() + " m, line at " + STOP_LINE_METERS + " m");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("red light: the ego stays at rest, in the same place, for as long as the light is red: the whole red time")
    void staysAtRestWhileTheLightIsRed(ExpertBackendType backend) {
        try (SimulationEngine engine = startedEngine(backend, 50.0)) {
            Approach approach = driveUntilStopped(engine);
            assertTrue(approach.stopped(), "the ego must come to rest");
            EgoVehicleState ego = engine.getWorldState().getEgo();

            // The light turns green once the ego has waited the red time; until then it is red.
            TrafficLight light = light(engine);
            int redCycles = 0;
            for (int i = 0; i < 150 && light.getState() == TrafficLightState.RED; i++) {
                SimulationSnapshot snapshot = engine.tick();
                if (light.getState() != TrafficLightState.RED) {
                    break;
                }
                redCycles++;
                assertEquals(0.0, ego.getSpeedKmh(), 0.05, "the ego must stay stopped, cycle " + i);
                assertEquals(approach.restPositionMeters(), ego.getPositionMeters(), 0.01,
                    "the ego must not creep forward, cycle " + i);
                assertEquals(DrivingAction.STOP.name(), snapshot.currentDecision(), "cycle " + i);
            }
            long holdCycles = SimulationConfig.RED_HOLD_AFTER_STOP_MS / SimulationConfig.defaultConfig().getTickMillis();
            assertEquals(holdCycles, redCycles, 1.0, "the light must stay red for the whole red time after the ego stopped");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("red then green: the ego moves off only when the light turns green and builds its speed back up")
    void drivesOnWhenTheLightTurnsGreen(ExpertBackendType backend) {
        try (SimulationEngine engine = startedEngine(backend, 50.0)) {
            Approach approach = driveUntilStopped(engine);
            assertTrue(approach.stopped(), "the ego must come to rest");
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 30; i++) {
                engine.tick();
            }
            assertEquals(approach.restPositionMeters(), ego.getPositionMeters(), 0.01, "still at the line while red");

            TrafficLight light = light(engine);
            ScenarioRoadElementDefinition green = new ScenarioRoadElementDefinition();
            green.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
            green.setLane(Lane.RIGHT);
            green.setPositionMeters(engine.getWorldState().signedDistance(light.getPositionMeters()));
            green.setTrafficLightState(TrafficLightState.GREEN);
            assertTrue(engine.updateRoadElementById(light.getId(), green).isPresent());

            for (int i = 0; i < 150; i++) {
                engine.tick();
            }
            assertTrue(ego.getPositionMeters() > LIGHT_POSITION_METERS,
                "on green the ego must drive past the light, it is at " + ego.getPositionMeters() + " m");
            assertTrue(ego.getSpeedKmh() > 30.0, "on green the ego must build its speed back up, it is at " + ego.getSpeedKmh());
        }
    }
}
