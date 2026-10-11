package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.facts.OvertakingStateFact;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.runtime.ClassObjectFilter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overtaking on a three-lane road with more than one vehicle ahead. Every overtake must
 * know which vehicle it is passing, a second overtake must be possible from the centre
 * lane, and the ego must return LEFT -> CENTER -> RIGHT without oscillating.
 */
class MultiVehicleOvertakingE2ETest {
    private static final double EGO_SPEED_KMH = 60.0;
    private static final double SLOW_SPEED_KMH = 30.0;

    private static SimulationEngine runningEngine() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("MULTI_VEHICLE_OVERTAKE");
        scenario.setName("Overtaking with several vehicles ahead");
        scenario.setInitialSpeedKmh(EGO_SPEED_KMH);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {});
        engine.start();
        for (int i = 0; i < 10; i++) {
            engine.tick();
        }
        return engine;
    }

    private static String addVehicle(SimulationEngine engine, Lane lane, double metersAhead, double speedKmh) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(metersAhead);
        vehicle.setSpeedKmh(speedKmh);
        return engine.addActorAheadAndGetId(vehicle);
    }

    private static OvertakingStateFact overtaking(SimulationEngine engine) {
        return (OvertakingStateFact) engine.getKnowledgeSession().getKieSession()
            .getObjects(new ClassObjectFilter(OvertakingStateFact.class)).iterator().next();
    }

    private static double gapTo(SimulationEngine engine, String actorId) {
        DynamicActor actor = engine.getWorldState().findActorById(actorId).orElseThrow();
        return actor.getPositionMeters() - engine.getWorldState().getEgo().getPositionMeters();
    }

    /** Smallest distance to a vehicle the ego shares a physical lane with; a collision check. */
    private static double closestInPhysicalLane(SimulationEngine engine, double closestSoFar) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        Lane physical = Lane.fromLateralPosition(ego.getLateralPositionMeters());
        double closest = closestSoFar;
        for (DynamicActor actor : engine.getWorldState().getActors()) {
            if (actor.getLane() == physical) {
                closest = Math.min(closest, Math.abs(actor.getPositionMeters() - ego.getPositionMeters()));
            }
        }
        return closest;
    }

    @Test
    @Timeout(30)
    @DisplayName("slow vehicles in RIGHT and CENTER, LEFT clear: the ego passes both through LEFT and returns to RIGHT once")
    void passesVehiclesInRightAndCenterThroughLeftAndReturnsRight() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String right = addVehicle(engine, Lane.RIGHT, 60.0, SLOW_SPEED_KMH);
            String center = addVehicle(engine, Lane.CENTER, 110.0, SLOW_SPEED_KMH);

            List<Lane> settledLanes = new ArrayList<>(List.of(Lane.RIGHT));
            boolean sawEmergencyBrake = false;
            boolean rightVehicleBackInFront = false;
            boolean passedRightVehicle = false;
            double closest = Double.MAX_VALUE;
            boolean done = false;
            for (int i = 0; i < 900 && !done; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                closest = closestInPhysicalLane(engine, closest);
                // The committed lane only changes when a maneuver settles.
                if (ego.getLane() != settledLanes.get(settledLanes.size() - 1)) {
                    settledLanes.add(ego.getLane());
                }
                if (gapTo(engine, right) < -5.0) {
                    passedRightVehicle = true;
                } else if (passedRightVehicle && gapTo(engine, right) > 0.0) {
                    rightVehicleBackInFront = true;
                }
                done = gapTo(engine, right) < 0 && gapTo(engine, center) < 0
                    && ego.getLane() == Lane.RIGHT && ego.getLaneChangeState() == LaneChangeState.IDLE;
            }

            assertTrue(done, "ego must pass both vehicles and settle back in RIGHT, lanes visited: " + settledLanes);
            assertEquals(List.of(Lane.RIGHT, Lane.CENTER, Lane.LEFT, Lane.CENTER, Lane.RIGHT), settledLanes,
                "ego must go RIGHT -> CENTER -> LEFT and return LEFT -> CENTER -> RIGHT without oscillating");
            assertFalse(rightVehicleBackInFront, "a vehicle already passed must not get back in front of the ego");
            assertFalse(sawEmergencyBrake, "overtaking must not need emergency braking");
            assertTrue(closest > 5.0, "ego must keep clear of vehicles in its own lane, closest was " + closest + " m");
            assertFalse(overtaking(engine).isActive(), "overtaking state must be cleared once back in RIGHT");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("RIGHT -> CENTER, then a slower vehicle in CENTER with LEFT clear: the ego continues CENTER -> LEFT")
    void continuesFromCenterToLeftWhenCenterVehicleIsSlower() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String right = addVehicle(engine, Lane.RIGHT, 50.0, SLOW_SPEED_KMH);
            String center = addVehicle(engine, Lane.CENTER, 100.0, SLOW_SPEED_KMH);

            boolean reachedCenter = false;
            boolean reachedLeft = false;
            String targetWhenSettledInCenter = null;
            boolean passedFlagWhenSettledInCenter = true;
            String targetWhenLeavingCenter = null;
            for (int i = 0; i < 500 && !reachedLeft; i++) {
                engine.tick();
                if (!reachedCenter && ego.getLane() == Lane.CENTER && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                    reachedCenter = true;
                    targetWhenSettledInCenter = overtaking(engine).getTargetId();
                    passedFlagWhenSettledInCenter = overtaking(engine).isPassedTarget();
                }
                if (reachedCenter && targetWhenLeavingCenter == null && ego.getLane() == Lane.CENTER
                    && ego.getLaneChangeState() != LaneChangeState.IDLE && ego.getTargetLane() == Lane.LEFT) {
                    targetWhenLeavingCenter = overtaking(engine).getTargetId();
                }
                reachedLeft = ego.getLane() == Lane.LEFT;
            }

            assertTrue(reachedCenter, "ego must first move to CENTER to pass the vehicle in RIGHT");
            assertEquals(right, targetWhenSettledInCenter,
                "arriving in CENTER the overtake must still track the RIGHT vehicle, not the new front vehicle");
            assertFalse(passedFlagWhenSettledInCenter,
                "the RIGHT vehicle is still ahead when the ego arrives in CENTER, so it is not passed yet");
            assertTrue(reachedLeft, "ego must continue CENTER -> LEFT to pass the slower CENTER vehicle");
            assertEquals(center, targetWhenLeavingCenter,
                "the second overtake must track the CENTER vehicle");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("same case with LEFT occupied: the ego stays in CENTER and adapts its speed")
    void staysInCenterAndAdaptsSpeedWhenLeftIsOccupied() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            // Three slow vehicles nearly abreast: once in CENTER the ego has the RIGHT vehicle
            // alongside and a LEFT vehicle just behind the CENTER one, so neither side helps.
            addVehicle(engine, Lane.RIGHT, 90.0, SLOW_SPEED_KMH);
            String center = addVehicle(engine, Lane.CENTER, 100.0, SLOW_SPEED_KMH);
            addVehicle(engine, Lane.LEFT, 95.0, SLOW_SPEED_KMH);

            boolean reachedCenter = false;
            boolean enteredLeft = false;
            boolean sawEmergencyBrake = false;
            double closest = Double.MAX_VALUE;
            for (int i = 0; i < 600; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                closest = closestInPhysicalLane(engine, closest);
                reachedCenter |= ego.getLane() == Lane.CENTER;
                enteredLeft |= Lane.fromLateralPosition(ego.getLateralPositionMeters()) == Lane.LEFT;
            }

            assertTrue(reachedCenter, "ego must move to CENTER to pass the vehicle in RIGHT");
            assertFalse(enteredLeft, "ego must not enter LEFT while it is occupied");
            assertEquals(Lane.CENTER, ego.getLane(), "ego must remain in CENTER behind the slower vehicle");
            assertTrue(gapTo(engine, center) > 8.0, "ego must stay behind the CENTER vehicle at a safe distance, gap was "
                + gapTo(engine, center) + " m");
            assertEquals(SLOW_SPEED_KMH, ego.getSpeedKmh(), 6.0, "ego must adapt its speed to the CENTER vehicle");
            assertFalse(sawEmergencyBrake, "adapting to the CENTER vehicle must not need emergency braking");
            assertTrue(closest > 5.0, "ego must keep clear of vehicles in its own lane, closest was " + closest + " m");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("an overtaken vehicle that is behind the ego is not tracked again by the front radar")
    void overtakenVehicleBehindIsNotTrackedAsFrontVehicle() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String right = addVehicle(engine, Lane.RIGHT, 50.0, SLOW_SPEED_KMH);

            boolean passed = false;
            boolean trackedAgainAfterPassing = false;
            double slowestWhilePassing = Double.MAX_VALUE;
            for (int i = 0; i < 500; i++) {
                SimulationSnapshot snapshot = engine.tick();
                if (ego.getLane() != Lane.RIGHT || ego.getLaneChangeState() != LaneChangeState.IDLE) {
                    slowestWhilePassing = Math.min(slowestWhilePassing, ego.getSpeedKmh());
                }
                if (gapTo(engine, right) < -5.0) {
                    passed = true;
                }
                if (passed && snapshot.radarDetectedObjectIds().contains(right)) {
                    trackedAgainAfterPassing = true;
                }
            }

            assertTrue(passed, "ego must pass the slower vehicle");
            assertFalse(trackedAgainAfterPassing, "a vehicle behind the ego must not be a front radar target again");
            assertTrue(gapTo(engine, right) < 0, "the overtaken vehicle must stay behind the ego");
            assertTrue(slowestWhilePassing >= SLOW_SPEED_KMH - 3.0,
                "ego must not drop below the speed of the vehicle it is passing, slowest was " + slowestWhilePassing + " km/h");
            assertEquals(Lane.RIGHT, ego.getLane(), "ego must be back in RIGHT at the end");
        }
    }
}
