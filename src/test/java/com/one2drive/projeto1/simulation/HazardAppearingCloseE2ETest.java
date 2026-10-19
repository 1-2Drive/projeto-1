package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hazards that appear close ahead, or in the lane the vehicle is moving into, and a slower
 * vehicle that appears close in the lane to the left. A lane change is only a way past a hazard
 * when it leaves the lane before it (StoppingPolicy.roomToLeaveLane); otherwise the approach
 * rules brake for the hazard, whichever lane the path runs through (VehicleStateFact.pathLane).
 * Legal passing is on the left: when comfortable braking would carry the ego past a slower
 * vehicle on its right, it brakes hard instead (Art. 36 CE).
 */
class HazardAppearingCloseE2ETest {
    private static final int BARRIER_CELL = 3;
    private static final int POTHOLE_CELL = 2;

    private static SimulationEngine engine(ExpertBackendType backend, String scenarioId, Lane lane, double speedKmh) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId(scenarioId);
        scenario.setName("Hazard appearing close");
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialLane(lane);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.start();
        for (int i = 0; i < 5; i++) {
            engine.tick();
        }
        return engine;
    }

    private static void addHazard(SimulationEngine engine, ScenarioRoadElementType type, Lane lane, double metersAhead) {
        ScenarioRoadElementDefinition hazard = new ScenarioRoadElementDefinition();
        hazard.setType(type);
        hazard.setLane(lane);
        hazard.setPositionMeters(metersAhead);
        hazard.setSeverity(HazardSeverity.HIGH);
        hazard.setBarrierOpen(false);
        engine.addRoadElementAhead(hazard);
    }

    private static OtherVehicle addVehicle(SimulationEngine engine, Lane lane, double metersAhead, double speedKmh) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(metersAhead);
        vehicle.setSpeedKmh(speedKmh);
        engine.addActorAhead(vehicle);
        engine.tick();
        for (DynamicActor actor : engine.getWorldState().getActors()) {
            if (actor instanceof OtherVehicle other && other.getLane() == lane) {
                return other;
            }
        }
        throw new IllegalStateException("vehicle not added");
    }

    /** The map cell under the vehicle, in the lane it is physically in. */
    private static int cellUnder(SimulationEngine engine) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        RoadMap map = engine.getWorldState().getRoadMap();
        return map.get(map.rowAt(ego.getPositionMeters()), Lane.fromLateralPosition(ego.getLateralPositionMeters()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("a closed barrier appearing 35 m ahead is braked for, not swerved around through its cell")
    void barrierAppearingCloseIsBrakedForNotSwervedAround(ExpertBackendType backend) {
        // At 60 km/h a lane change needs 47 m to leave the lane before the barrier: started from
        // 35 m it would carry the vehicle over the barrier, so the vehicle brakes in its lane.
        try (SimulationEngine engine = engine(backend, "HAZARD_CLOSE", Lane.CENTER, 60.0)) {
            addHazard(engine, ScenarioRoadElementType.BARRIER, Lane.CENTER, 35.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            boolean braked = false;
            for (int i = 0; i < 300; i++) {
                engine.tick();
                assertFalse(cellUnder(engine) == BARRIER_CELL, "drove through the barrier at " + ego.getSpeedKmh() + " km/h");
                braked |= engine.getCurrentDecision().getSpeedAction().isBraking();
                // Once nearly stopped there is room to go round from where it is; keeping right
                // away from the barrier's lane is not swerving around it.
                String laneRule = engine.getCurrentDecision().getLaneSourceRule();
                if (ego.getSpeedKmh() > 20.0 && laneRule != null && laneRule.startsWith("Avoid closed barrier")) {
                    assertFalse(engine.getCurrentDecision().getLaneAction().isLaneManeuver(),
                        "swerved at " + ego.getSpeedKmh() + " km/h: " + laneRule);
                }
                if (ego.getSpeedKmh() < 0.5) {
                    break;
                }
            }
            assertTrue(braked, "the vehicle must brake for a barrier it cannot go round in time");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("a pothole appearing 45 m ahead is crossed slowly, not at speed on the way to a free lane")
    void potholeAppearingCloseIsCrossedSlowly(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, "HAZARD_CLOSE", Lane.CENTER, 60.0)) {
            addHazard(engine, ScenarioRoadElementType.POTHOLE, Lane.CENTER, 45.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double speedOverIt = Double.NaN;
            for (int i = 0; i < 150 && Double.isNaN(speedOverIt); i++) {
                engine.tick();
                if (cellUnder(engine) == POTHOLE_CELL) {
                    speedOverIt = ego.getSpeedKmh();
                }
            }
            assertFalse(Double.isNaN(speedOverIt), "the pothole was expected under the vehicle");
            assertTrue(speedOverIt <= 35.0, "crossed the pothole at " + speedOverIt + " km/h");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("a pothole appearing 20 m ahead is braked for in the lane, not swerved around")
    void potholeAppearingTooCloseIsBrakedForNotSwervedAround(ExpertBackendType backend) {
        // 20 m at 60 km/h leaves room neither to slow to the crossing speed nor to leave the
        // lane: the vehicle brakes as much as comfort allows and keeps its lane.
        try (SimulationEngine engine = engine(backend, "HAZARD_CLOSE", Lane.CENTER, 60.0)) {
            addHazard(engine, ScenarioRoadElementType.POTHOLE, Lane.CENTER, 20.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double speedOverIt = Double.NaN;
            for (int i = 0; i < 150 && Double.isNaN(speedOverIt); i++) {
                engine.tick();
                assertFalse(engine.getCurrentDecision().getLaneAction().isLaneManeuver(),
                    "swerved towards a pothole it cannot clear: " + engine.getCurrentDecision().getLaneSourceRule());
                if (cellUnder(engine) == POTHOLE_CELL) {
                    speedOverIt = ego.getSpeedKmh();
                }
            }
            assertFalse(Double.isNaN(speedOverIt), "the pothole was expected under the vehicle");
            assertTrue(speedOverIt < 55.0, "did not brake for the pothole: " + speedOverIt + " km/h");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("a closed barrier in the lane being entered is braked for during the lane change")
    void barrierInTheLaneBeingEnteredIsBrakedFor(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, "HAZARD_CLOSE", Lane.RIGHT, 60.0)) {
            addHazard(engine, ScenarioRoadElementType.BARRIER, Lane.RIGHT, 150.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            boolean placed = false;
            for (int i = 0; i < 400; i++) {
                if (!placed && ego.getLaneChangeState() == LaneChangeState.CHANGING) {
                    addHazard(engine, ScenarioRoadElementType.BARRIER, Lane.CENTER, 70.0);
                    placed = true;
                }
                engine.tick();
                assertFalse(cellUnder(engine) == BARRIER_CELL, "drove through a barrier at " + ego.getSpeedKmh() + " km/h");
                if (placed && ego.getSpeedKmh() < 0.5) {
                    break;
                }
            }
            assertTrue(placed, "the first barrier must have started a lane change");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("a wall of barriers at 90 km/h is stopped for from the braking curve, without an emergency")
    void wallAtNinetyIsStoppedForComfortably(ExpertBackendType backend) {
        // The presentation demo keeps the vehicle in the centre lane (no keep-right).
        try (SimulationEngine engine = engine(backend, "S12_PRESENTATION_DEMO", Lane.CENTER, 90.0)) {
            for (Lane lane : java.util.List.of(Lane.LEFT, Lane.CENTER, Lane.RIGHT)) {
                addHazard(engine, ScenarioRoadElementType.BARRIER, lane, 150.0);
            }
            engine.tick();
            double barrierAt = engine.getWorldState().getRoadElements().stream()
                .filter(e -> e.getLane() == Lane.CENTER).findFirst().orElseThrow().getPositionMeters();
            EgoVehicleState ego = engine.getWorldState().getEgo();
            int emergencyTicks = 0;
            for (int i = 0; i < 300 && ego.getSpeedKmh() > 0.3; i++) {
                engine.tick();
                assertFalse(cellUnder(engine) == BARRIER_CELL, "drove into the wall");
                if (engine.getCurrentDecision().getSpeedAction() == DrivingAction.EMERGENCY_BRAKE) {
                    emergencyTicks++;
                }
            }
            double stoppedBefore = barrierAt - ego.getPositionMeters();
            assertTrue(ego.getSpeedKmh() <= 0.3, "the vehicle must stop before the wall");
            assertTrue(stoppedBefore >= 5.0, "stopped only " + stoppedBefore + " m before the wall");
            assertEquals(0, emergencyTicks, "seen from far enough, the wall needs no emergency braking");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("at 90 km/h a slower vehicle 40 m ahead in the centre lane is not passed on its right")
    void fastEgoDoesNotPassSlowerCentreVehicleOnItsRight(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, "HAZARD_CLOSE", Lane.RIGHT, 90.0)) {
            OtherVehicle slow = addVehicle(engine, Lane.CENTER, 40.0, 30.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 300; i++) {
                engine.tick();
                boolean inRightLane = Lane.fromLateralPosition(ego.getLateralPositionMeters()) == Lane.RIGHT;
                assertFalse(inRightLane && ego.getPositionMeters() > slow.getPositionMeters(),
                    "passed the slower vehicle on its right at t=" + (i / 10.0) + " s");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("at 90 km/h the ego slows before moving in behind a slower vehicle 60 m ahead on its left")
    void fastEgoSlowsBeforeMovingInBehindSlowerCentreVehicle(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, "HAZARD_CLOSE", Lane.RIGHT, 90.0)) {
            OtherVehicle slow = addVehicle(engine, Lane.CENTER, 60.0, 30.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double minSpeed = ego.getSpeedKmh();
            for (int i = 0; i < 300; i++) {
                engine.tick();
                minSpeed = Math.min(minSpeed, ego.getSpeedKmh());
                boolean inRightLane = Lane.fromLateralPosition(ego.getLateralPositionMeters()) == Lane.RIGHT;
                assertFalse(inRightLane && ego.getPositionMeters() > slow.getPositionMeters(),
                    "passed the slower vehicle on its right at t=" + (i / 10.0) + " s");
            }
            assertTrue(minSpeed > 10.0, "moved in behind the vehicle too early and had to stop (min " + minSpeed + " km/h)");
        }
    }
}
