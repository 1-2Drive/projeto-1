package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.inference.ExpertSystemException;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import com.one2drive.projeto1.simulation.SimulationEngine;
import com.one2drive.projeto1.simulation.SimulationSnapshot;
import com.one2drive.projeto1.simulation.SimulationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole loop with SWI-Prolog deciding: world, sensors, Prolog rules, the same arbitration
 * and actuator as with Drools. The red-light cases mirror RedTrafficLightStopE2ETest.
 */
class PrologSimulationE2ETest {
    private static final double LIGHT_POSITION_METERS = 118.0;
    private static final double STOP_LINE_METERS =
        LIGHT_POSITION_METERS - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

    private static ScenarioDefinition redLightScenario(double speedKmh) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("PROLOG_RED_LIGHT_" + Math.round(speedKmh));
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

    private static SimulationEngine startedPrologEngine(ScenarioDefinition scenario) {
        PrologTestHarness.assumeSwiplAvailable();
        SimulationEngine engine = new SimulationEngine(
            SimulationConfig.defaultConfig(), scenario, true, d -> { }, ExpertBackendType.PROLOG);
        engine.start();
        return engine;
    }

    private static TrafficLight light(SimulationEngine engine) {
        return engine.getWorldState().getRoadElements().stream()
            .filter(TrafficLight.class::isInstance).map(TrafficLight.class::cast).findFirst().orElseThrow();
    }

    private record Approach(double restPositionMeters, double furthestPositionMeters,
                            boolean emergencyBrake, boolean stopped) {
    }

    private static Approach driveUntilStopped(SimulationEngine engine) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        boolean emergency = false;
        boolean stopped = false;
        double furthest = ego.getPositionMeters();
        for (int i = 0; i < 400 && !stopped; i++) {
            SimulationSnapshot snapshot = engine.tick();
            assertNull(engine.getBackendError(), "Prolog must keep deciding");
            furthest = Math.max(furthest, ego.getPositionMeters());
            emergency |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
            stopped = ego.getSpeedKmh() < 0.05;
        }
        return new Approach(ego.getPositionMeters(), furthest, emergency, stopped);
    }

    @Test
    @Timeout(60)
    @DisplayName("Prolog, red light at 30, 50 and 70 km/h: at rest before the stop line, no emergency brake")
    void redLightStopsBeforeTheLine() {
        for (double speedKmh : new double[]{30.0, 50.0, 70.0}) {
            try (SimulationEngine engine = startedPrologEngine(redLightScenario(speedKmh))) {
                Approach approach = driveUntilStopped(engine);

                assertTrue(approach.stopped(), speedKmh + " km/h: the ego must come to rest");
                assertFalse(approach.emergencyBrake(), speedKmh + " km/h: a light seen from afar needs no emergency brake");
                assertTrue(approach.furthestPositionMeters() <= STOP_LINE_METERS,
                    speedKmh + " km/h: never past the stop line at " + STOP_LINE_METERS + " m, reached "
                        + approach.furthestPositionMeters());
                assertTrue(approach.restPositionMeters() >= STOP_LINE_METERS - 2.0 * StoppingPolicy.STOP_LINE_TOLERANCE_METERS,
                    speedKmh + " km/h: stops at the line, not far behind it; stopped at " + approach.restPositionMeters());
                assertTrue(engine.getCurrentDecision().getSourceRule().contains("red traffic light"),
                    "the decision comes from a Prolog traffic-light rule: " + engine.getCurrentDecision().getSourceRule());
            }
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Prolog, red then green: the ego moves off on green and builds back up to the cruise speed")
    void drivesOnWhenTheLightTurnsGreen() {
        try (SimulationEngine engine = startedPrologEngine(redLightScenario(50.0))) {
            Approach approach = driveUntilStopped(engine);
            assertTrue(approach.stopped(), "the ego must come to rest");
            EgoVehicleState ego = engine.getWorldState().getEgo();

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
            assertNull(engine.getBackendError());
            assertTrue(ego.getPositionMeters() > LIGHT_POSITION_METERS,
                "on green the ego must drive past the light, it is at " + ego.getPositionMeters() + " m");
            assertTrue(ego.getSpeedKmh() > 30.0, "on green the ego must build its speed back up, it is at " + ego.getSpeedKmh());
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Prolog, stop sign (S4): full stop at the line, 1.2 s hold, then the vehicle pulls away")
    void stopSignFlowBringsVehicleToFullStopThenProceeds() {
        ScenarioDefinition scenario = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json")
            .findById("S4").orElseThrow();
        try (SimulationEngine engine = startedPrologEngine(scenario)) {
            double signPosition = engine.getWorldState().getRoadElements().stream()
                .filter(StopSign.class::isInstance).findFirst().orElseThrow().getPositionMeters();

            boolean emergency = false;
            boolean stoppedAtLine = false;
            double closestDistance = Double.MAX_VALUE;
            int stoppedTicks = 0;
            for (int i = 0; i < 500 && !stoppedAtLine; i++) {
                SimulationSnapshot snapshot = engine.tick();
                assertNull(engine.getBackendError(), "Prolog must keep deciding");
                double distance = engine.getWorldState().signedDistance(signPosition);
                closestDistance = Math.min(closestDistance, distance);
                emergency |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                if (snapshot.egoSpeedKmh() < 0.6 && distance <= 4.0) {
                    stoppedTicks++;
                    stoppedAtLine = stoppedTicks >= 10;
                } else {
                    stoppedTicks = 0;
                }
            }
            assertTrue(stoppedAtLine, "full stop at the sign, closest distance " + closestDistance);
            assertTrue(closestDistance >= -1.0 && closestDistance <= 4.0,
                "stops at the line instead of crossing it, closest distance " + closestDistance);
            assertFalse(emergency, "a sign seen from afar needs no emergency brake");

            boolean proceeded = false;
            for (int i = 0; i < 250; i++) {
                SimulationSnapshot snapshot = engine.tick();
                proceeded |= "Proceed after stop sign condition satisfied".equals(snapshot.sourceRule());
            }
            assertTrue(proceeded, "the stop condition must be released");
            assertTrue(engine.snapshot().egoSpeedKmh() >= 25.0,
                "the vehicle pulls away after the stop, speed " + engine.snapshot().egoSpeedKmh());
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Prolog, stop sign added 60 m ahead at 60 km/h: one steady approach, as SimulationEngineE2ETest requires of Drools")
    void stopSignApproachBrakesSteadilyInsteadOfPulsingEmergencyBrake() {
        ScenarioDefinition scenario = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json")
            .findById("S1").orElseThrow();
        try (SimulationEngine engine = startedPrologEngine(scenario)) {
            ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
            sign.setType(ScenarioRoadElementType.STOP_SIGN);
            sign.setLane(Lane.CENTER);
            sign.setPositionMeters(60.0);
            engine.addRoadElementAhead(sign);
            engine.tick();
            double signPosition = engine.getWorldState().getRoadElements().stream()
                .filter(StopSign.class::isInstance).findFirst().orElseThrow().getPositionMeters();

            int emergencyBursts = 0;
            int baselineTicks = 0;
            boolean approaching = false;
            boolean previousWasEmergency = false;
            boolean stopped = false;
            int stoppedTicks = 0;
            double closestDistance = Double.MAX_VALUE;
            double firstRestDistance = Double.NaN;
            for (int i = 0; i < 400 && !stopped; i++) {
                SimulationSnapshot snapshot = engine.tick();
                assertNull(engine.getBackendError(), "Prolog must keep deciding");
                double distance = engine.getWorldState().signedDistance(signPosition);
                closestDistance = Math.min(closestDistance, distance);
                if (snapshot.egoSpeedKmh() < 0.6 && Double.isNaN(firstRestDistance)) {
                    firstRestDistance = distance;
                }
                boolean emergency = DrivingAction.EMERGENCY_BRAKE.name().equals(snapshot.currentDecision());
                if (emergency && !previousWasEmergency) {
                    emergencyBursts++;
                } else if (!emergency && approaching && "Default maintain speed baseline".equals(snapshot.sourceRule())) {
                    baselineTicks++;
                }
                approaching |= snapshot.sourceRule() != null && snapshot.sourceRule().startsWith("Stop sign");
                previousWasEmergency = emergency;
                if (snapshot.egoSpeedKmh() < 0.6 && distance <= 4.0) {
                    stoppedTicks++;
                    stopped = stoppedTicks >= 10;
                } else {
                    stoppedTicks = 0;
                }
            }
            assertTrue(stopped, "full stop at the sign, closest distance " + closestDistance);
            // The same bounds SimulationEngineE2ETest holds Drools to.
            assertTrue(closestDistance >= -1.0 && closestDistance <= 4.0,
                "stops at the line, neither short of it nor past it, closest distance " + closestDistance);
            assertTrue(firstRestDistance >= -1.0 && firstRestDistance <= 4.0,
                "the first rest is at the line, not a stall short of it: " + firstRestDistance);
            assertTrue(emergencyBursts <= 2, "hard braking is one short burst, not a pulse train: " + emergencyBursts);
            assertTrue(baselineTicks < 10, "the stop sign owns the approach; baseline ticks " + baselineTicks);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Prolog working memory and timeline reach the same views the UI reads for Drools")
    void workingMemoryAndTimelineAreExposed() {
        try (SimulationEngine engine = startedPrologEngine(redLightScenario(50.0))) {
            for (int i = 0; i < 20; i++) {
                engine.tick();
            }
            assertEquals(ExpertBackendType.PROLOG, engine.getBackendType());
            assertTrue(engine.getWorkingMemoryFactViews().stream().anyMatch(f -> f.type().equals("ego_speed")),
                "the cycle's facts are visible");
            assertTrue(engine.getTimelineEntries().stream().anyMatch(e -> e.getMessage().startsWith("RULE FIRED")),
                "rules that concluded something appear on the timeline");
            assertThrows(IllegalStateException.class, engine::getKnowledgeSession,
                "Drools internals are not reachable while Prolog decides");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("switching backends reloads the scenario on the new engine, and back")
    void switchesBetweenBackends() {
        PrologTestHarness.assumeSwiplAvailable();
        ScenarioDefinition scenario = redLightScenario(50.0);
        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            engine.start();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            engine.switchBackend(ExpertBackendType.PROLOG, scenario);
            assertEquals(ExpertBackendType.PROLOG, engine.getBackendType());
            assertEquals(SimulationStatus.STOPPED, engine.getStatus(), "a switch is a reset");
            assertEquals(0L, engine.snapshot().simTimeMillis());
            engine.start();
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            assertNull(engine.getBackendError());

            engine.switchBackend(ExpertBackendType.DROOLS, scenario);
            assertEquals(ExpertBackendType.DROOLS, engine.getBackendType());
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("a backend that cannot start leaves the previous one deciding")
    void failedSwitchKeepsThePreviousBackend() {
        ScenarioDefinition scenario = redLightScenario(50.0);
        String previous = System.getProperty(PrologProcess.EXECUTABLE_PROPERTY);
        System.setProperty(PrologProcess.EXECUTABLE_PROPERTY, "/nonexistent/swipl");
        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { })) {
            assertThrows(ExpertSystemException.class, () -> engine.switchBackend(ExpertBackendType.PROLOG, scenario));
            assertEquals(ExpertBackendType.DROOLS, engine.getBackendType());
            engine.start();
            engine.tick();
            assertNull(engine.getBackendError());
        } finally {
            if (previous == null) {
                System.clearProperty(PrologProcess.EXECUTABLE_PROPERTY);
            } else {
                System.setProperty(PrologProcess.EXECUTABLE_PROPERTY, previous);
            }
        }
    }
}
