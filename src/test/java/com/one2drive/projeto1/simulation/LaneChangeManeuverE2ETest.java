package com.one2drive.projeto1.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;

/**
 * Proves the complete avoidance chain: world contains a hazard in the vehicle's lane,
 * a sensor detects it, Drools reasons over lane occupancy and selects a maneuver, the
 * actuator executes it and the vehicle's lateral position actually moves.
 *
 * <p>No test sets the target lane directly: the maneuver can only happen because
 * Drools selected it.
 */
class LaneChangeManeuverE2ETest {

    private static SimulationEngine runningEngine(ExpertBackendType backend) {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.getWorldState().getEgo().setLane(Lane.CENTER);
        engine.getWorldState().getEgo().setLateralPositionMeters(Lane.CENTER.centreMeters());
        engine.getWorldState().getEgo().setSpeedKmh(70.0);
        engine.start();
        return engine;
    }

    private static void addPothole(SimulationEngine engine, double distanceMeters, Lane lane) {
        ScenarioRoadElementDefinition pothole = new ScenarioRoadElementDefinition();
        pothole.setType(ScenarioRoadElementType.POTHOLE);
        pothole.setLane(lane);
        pothole.setPositionMeters(distanceMeters);
        pothole.setSeverity(com.one2drive.projeto1.domain.HazardSeverity.HIGH);
        engine.addRoadElementAhead(pothole);
    }

    /**
     * Adds a vehicle travelling at the ego speed so it stays alongside for the whole
     * test. A slower blocker would simply be overtaken and free the lane again.
     */
    private static void addVehicleInLane(SimulationEngine engine, double distanceMeters, Lane lane) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(distanceMeters);
        vehicle.setSpeedKmh(70.0);
        engine.addActorAhead(vehicle);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("in rain the fuzzy weather ceiling slows the car but does not stop it avoiding a pothole")
    void rainDoesNotBlockPotholeAvoidance(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            engine.setRain(true);
            addPothole(engine, 100.0, Lane.CENTER);

            boolean weatherCeilingSeen = false;
            for (int i = 0; i < 160 && engine.getWorldState().getEgo().getLane() == Lane.CENTER; i++) {
                engine.tick();
                weatherCeilingSeen |= engine.getCurrentDecision().getProposals().stream()
                    .anyMatch(p -> "Defuzzify weather speed ceiling".equals(p.getSourceRule()));
            }

            assertTrue(weatherCeilingSeen, "rain must produce the fuzzy weather ceiling");
            assertTrue(engine.getWorldState().getEgo().getLane() != Lane.CENTER,
                "the lane channel is arbitrated apart from the speed ceiling, so the car still leaves the pothole lane");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("pothole in own lane with right lane blocked makes Drools choose CHANGE_LANE_LEFT and the car moves left")
    void potholeAvoidanceMovesVehicleLeft(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            addPothole(engine, 90.0, Lane.CENTER);
            addVehicleInLane(engine, 22.0, Lane.RIGHT);

            boolean decisionSeen = false;
            String firedRule = null;
            double lateralBefore = engine.getWorldState().getEgo().getLateralPositionMeters();
            List<Double> lateralSamples = new java.util.ArrayList<>();

            for (int i = 0; i < 120; i++) {
                engine.tick();

                if (!decisionSeen && DrivingAction.CHANGE_LANE_LEFT.name().equals(
                        engine.getCurrentDecision().getAction().name())) {
                    decisionSeen = true;
                    firedRule = engine.getCurrentDecision().getSourceRule();
                }
                if (engine.getWorldState().getEgo().getLaneChangeState().isActive()) {
                    lateralSamples.add(engine.getWorldState().getEgo().getLateralPositionMeters());
                }
                if (engine.getWorldState().getEgo().getLane() == Lane.LEFT
                    && engine.getWorldState().getEgo().getLaneChangeState() == LaneChangeState.IDLE) {
                    break;
                }
            }

            assertTrue(decisionSeen, "Drools should have selected CHANGE_LANE_LEFT for a clear left lane");
            assertTrue(firedRule != null && firedRule.toLowerCase().contains("avoid"),
                "Expected an avoidance rule to fire but got: " + firedRule);

            assertEquals(Lane.LEFT, engine.getWorldState().getEgo().getLane(),
                "Vehicle should have completed the lane change into the left lane");
            assertEquals(Lane.LEFT.centreMeters(), engine.getWorldState().getEgo().getLateralPositionMeters(), 0.01,
                "Vehicle should sit on the left lane centre line");

            // The move must be progressive, never a teleport.
            assertTrue(lateralSamples.size() > 5,
                "Lane change should take several ticks, saw " + lateralSamples.size());
            double direction = Math.signum(Lane.LEFT.centreMeters() - lateralBefore);
            for (int i = 1; i < lateralSamples.size(); i++) {
                double previous = (lateralSamples.get(i - 1) - lateralBefore) * direction;
                double current = (lateralSamples.get(i) - lateralBefore) * direction;
                assertTrue(current >= previous - 1e-9,
                    "Lateral position must move steadily towards the target lane centre");
            }

            // The hazard never moved; only the vehicle did.
            // A one-shot toolbox/map hazard is consumed after it is detected and passed.
            assertTrue(engine.getWorldState().getRoadElements().stream()
                    .filter(Pothole.class::isInstance).map(Pothole.class::cast)
                    .allMatch(pothole -> pothole.getLane() == Lane.CENTER),
                "Any still-present pothole must remain in its original lane");
            assertTrue(lateralBefore > engine.getWorldState().getEgo().getLateralPositionMeters(),
                "Lateral position should end up left of where it started");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("pothole with both adjacent lanes occupied must not change lane")
    void blockedLanesPreventLaneChange(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            addPothole(engine, 90.0, Lane.CENTER);
            addVehicleInLane(engine, 18.0, Lane.LEFT);
            addVehicleInLane(engine, 22.0, Lane.RIGHT);

            for (int i = 0; i < 90; i++) {
                for (com.one2drive.projeto1.domain.DynamicActor actor : engine.getWorldState().getActors()) {
                    actor.setSpeedMps(engine.getWorldState().getEgo().getSpeedMps());
                }
                engine.tick();
            }

            assertEquals(Lane.CENTER, engine.getWorldState().getEgo().getLane(),
                "Vehicle must stay in its lane when both adjacent lanes are occupied");
            assertEquals(0.0, engine.getWorldState().getEgo().getLateralPositionMeters(), 0.01,
                "Vehicle must not drift laterally when avoidance is unsafe");

            String action = engine.getCurrentDecision().getAction().name();
            assertFalse(DrivingAction.CHANGE_LANE_LEFT.name().equals(action));
            assertFalse(DrivingAction.CHANGE_LANE_RIGHT.name().equals(action));
            assertTrue(
                DrivingAction.REDUCE_SPEED.name().equals(action)
                    || DrivingAction.BRAKE.name().equals(action)
                    || DrivingAction.STOP.name().equals(action)
                    || DrivingAction.MAINTAIN_SPEED.name().equals(action),
                "Expected a conservative action when avoidance is unsafe but got " + action);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("pothole in centre lane steers right when only the right lane is clear")
    void potholeAvoidancePrefersRightWhenLeftOccupied(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            addPothole(engine, 90.0, Lane.CENTER);
            addVehicleInLane(engine, 20.0, Lane.LEFT);

            for (int i = 0; i < 120; i++) {
                engine.tick();
                if (engine.getWorldState().getEgo().getLane() == Lane.RIGHT
                    && engine.getWorldState().getEgo().getLaneChangeState() == LaneChangeState.IDLE) {
                    break;
                }
            }

            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(),
                "Vehicle should use the only clear adjacent lane");
            assertEquals(Lane.RIGHT.centreMeters(), engine.getWorldState().getEgo().getLateralPositionMeters(), 0.01);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("vehicle does not oscillate between lanes once the hazard is behind it")
    void doesNotOscillateAfterAvoidance(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            addPothole(engine, 70.0, Lane.CENTER);

            List<String> committedLanes = new java.util.ArrayList<>();
            for (int i = 0; i < 260; i++) {
                engine.tick();
                String lane = engine.getWorldState().getEgo().getLane().name();
                if (committedLanes.isEmpty() || !committedLanes.get(committedLanes.size() - 1).equals(lane)) {
                    committedLanes.add(lane);
                }
            }

            assertTrue(committedLanes.size() <= 4,
                "Vehicle should settle after one avoidance and return right, lane history was " + committedLanes);
            assertEquals(Lane.RIGHT.name(), committedLanes.get(committedLanes.size() - 1));
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("driver request to change lane left is executed when the left lane is clear")
    void driverRequestedLaneChangeLeftIsExecuted(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            engine.requestLaneChange(com.one2drive.projeto1.domain.LaneChangeDirection.LEFT);

            boolean decisionSeen = false;
            boolean reachedLeft = false;
            for (int i = 0; i < 200; i++) {
                engine.tick();
                decisionSeen |= DrivingAction.CHANGE_LANE_LEFT == engine.getCurrentDecision().getAction();
                if (engine.getWorldState().getEgo().getLane() == Lane.LEFT) {
                    reachedLeft = true;
                    break;
                }
            }

            assertTrue(decisionSeen, "Drools should have selected CHANGE_LANE_LEFT for the driver request");
            assertTrue(reachedLeft, "Vehicle should have reached the left lane");
            assertEquals(Lane.LEFT, engine.getWorldState().getEgo().getLane(),
                "Vehicle should have moved to the left lane");
            assertEquals(Lane.LEFT.centreMeters(), engine.getWorldState().getEgo().getLateralPositionMeters(), 0.01);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("driver request to change lane is executed while the car is moving off from a standstill")
    void driverRequestedLaneChangeWhileMovingOff(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            engine.getWorldState().getEgo().setSpeedKmh(0.0);
            for (int i = 0; i < 10; i++) {
                engine.tick();
            }
            assertEquals(DrivingAction.CONTINUE, engine.getCurrentDecision().getAction(), "The car should be moving off");

            engine.requestLaneChange(com.one2drive.projeto1.domain.LaneChangeDirection.LEFT);
            boolean reachedLeft = false;
            for (int i = 0; i < 150; i++) {
                engine.tick();
                if (engine.getWorldState().getEgo().getLane() == Lane.LEFT) {
                    reachedLeft = true;
                }
            }

            assertTrue(reachedLeft, "Moving off must not swallow the driver's lane change request");
            assertTrue(engine.getWorldState().getEgo().getSpeedKmh() >= 25.0,
                "The car should keep moving off after the lane change, speed was "
                    + engine.getWorldState().getEgo().getSpeedKmh());
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("driver request to change lane right is refused while the right lane is occupied")
    void driverRequestedLaneChangeIntoOccupiedLaneIsRefused(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            addVehicleInLane(engine, 20.0, Lane.RIGHT);
            for (int i = 0; i < 5; i++) {
                engine.tick();
            }
            engine.requestLaneChange(com.one2drive.projeto1.domain.LaneChangeDirection.RIGHT);

            for (int i = 0; i < 60; i++) {
                engine.tick();
                assertFalse(DrivingAction.CHANGE_LANE_RIGHT == engine.getCurrentDecision().getAction(),
                    "Drools must not approve a lane change into an occupied lane");
            }

            assertEquals(Lane.CENTER, engine.getWorldState().getEgo().getLane());
        }
    }
}
