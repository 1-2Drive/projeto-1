package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RoadMarkingType;
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
 * A slower vehicle in the lane to the left of the ego must be overtaken on the left
 * (Portuguese Highway Code Art. 36), never passed by driving on in the ego lane.
 */
class LeftLaneOvertakingE2ETest {
    private static final double EGO_SPEED_KMH = 60.0;
    private static final double SLOW_SPEED_KMH = 30.0;

    private static SimulationEngine runningEngine(RoadMarkingType rightMarking) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("LEFT_LANE_OVERTAKE");
        scenario.setName("Slower vehicle in the lane to the left");
        scenario.setInitialSpeedKmh(EGO_SPEED_KMH);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        if (rightMarking != null) {
            scenario.setRightMarking(rightMarking);
        }
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

    /** An overtaken vehicle leaves the world once it is out of the rendered window behind the car. */
    private static double gapTo(SimulationEngine engine, String actorId) {
        return engine.getWorldState().findActorById(actorId)
            .map(actor -> actor.getPositionMeters() - engine.getWorldState().getEgo().getPositionMeters())
            .orElse(Double.NEGATIVE_INFINITY);
    }

    /** True when the ego is physically in a lane to the right of the given vehicle's lane. */
    private static boolean egoIsRightOf(SimulationEngine engine, String actorId) {
        DynamicActor actor = engine.getWorldState().findActorById(actorId).orElseThrow();
        Lane physical = Lane.fromLateralPosition(engine.getWorldState().getEgo().getLateralPositionMeters());
        return physical.centreMeters() > actor.getLane().centreMeters();
    }

    private static OvertakingStateFact overtaking(SimulationEngine engine) {
        return (OvertakingStateFact) engine.getKnowledgeSession().getKieSession()
            .getObjects(new ClassObjectFilter(OvertakingStateFact.class)).iterator().next();
    }

    @Test
    @Timeout(30)
    @DisplayName("ego in RIGHT, slower vehicle in CENTER, lanes clear: it is overtaken on the left, never passed on the right")
    void slowerCenterVehicleIsOvertakenOnTheLeft() {
        try (SimulationEngine engine = runningEngine(null)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String center = addVehicle(engine, Lane.CENTER, 80.0, SLOW_SPEED_KMH);

            boolean passedOnTheRight = false;
            double previousGap = Double.MAX_VALUE;
            boolean sawChangeLeftFromRight = false;
            boolean reachedLeft = false;
            boolean passed = false;
            for (int i = 0; i < 500 && !passed; i++) {
                SimulationSnapshot snapshot = engine.tick();
                if (ego.getLane() == Lane.RIGHT && snapshot.currentDecision().equals(DrivingAction.CHANGE_LANE_LEFT.name())) {
                    sawChangeLeftFromRight = true;
                }
                reachedLeft |= ego.getLane() == Lane.LEFT;
                double gapNow = gapTo(engine, center);
                if (previousGap > 0.0 && gapNow <= 0.0 && egoIsRightOf(engine, center)) {
                    passedOnTheRight = true;
                }
                previousGap = gapNow;
                passed = gapTo(engine, center) < -5.0;
            }

            assertFalse(passedOnTheRight, "the slower CENTER vehicle must not be passed on its right");
            assertTrue(sawChangeLeftFromRight, "the ego must start the overtake by leaving RIGHT to the left");
            assertTrue(reachedLeft, "the ego must go RIGHT -> CENTER -> LEFT to pass on the left");
            assertTrue(passed, "the ego must get past the slower vehicle");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("same case with LEFT occupied: the ego moves in behind the CENTER vehicle and adapts its speed")
    void adaptsSpeedBehindCenterVehicleWhenLeftIsOccupied() {
        try (SimulationEngine engine = runningEngine(null)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String center = addVehicle(engine, Lane.CENTER, 80.0, SLOW_SPEED_KMH);
            // A slow vehicle in LEFT just behind the CENTER one: LEFT offers no way past.
            addVehicle(engine, Lane.LEFT, 75.0, SLOW_SPEED_KMH);

            boolean passedOnTheRight = false;
            double previousGap = Double.MAX_VALUE;
            boolean enteredLeft = false;
            boolean sawEmergencyBrake = false;
            for (int i = 0; i < 600; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                enteredLeft |= Lane.fromLateralPosition(ego.getLateralPositionMeters()) == Lane.LEFT;
                double gapNow = gapTo(engine, center);
                if (previousGap > 0.0 && gapNow <= 0.0 && egoIsRightOf(engine, center)) {
                    passedOnTheRight = true;
                }
                previousGap = gapNow;
            }

            assertFalse(passedOnTheRight, "the slower CENTER vehicle must not be passed on its right");
            assertFalse(enteredLeft, "the ego must not enter LEFT when that lane gives no way past");
            assertTrue(gapTo(engine, center) > 8.0, "the ego must stay behind the CENTER vehicle, gap was "
                + gapTo(engine, center) + " m");
            assertEquals(SLOW_SPEED_KMH, ego.getSpeedKmh(), 6.0, "the ego must adapt its speed to the slower vehicle");
            assertFalse(sawEmergencyBrake, "adapting the speed must not need emergency braking");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("same case with CENTER closed by a continuous line: the ego stays in RIGHT, slows down and does not pass")
    void slowsDownInRightLaneWhenCenterCannotBeEntered() {
        try (SimulationEngine engine = runningEngine(RoadMarkingType.CONTINUOUS)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String center = addVehicle(engine, Lane.CENTER, 80.0, SLOW_SPEED_KMH);

            boolean leftRightLane = false;
            boolean sawEmergencyBrake = false;
            for (int i = 0; i < 600; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                leftRightLane |= Lane.fromLateralPosition(ego.getLateralPositionMeters()) != Lane.RIGHT;
            }

            assertFalse(leftRightLane, "the ego must not cross the continuous line");
            assertTrue(gapTo(engine, center) > 0.0, "the ego must not pass the CENTER vehicle on its right, gap was "
                + gapTo(engine, center) + " m");
            assertEquals(SLOW_SPEED_KMH, ego.getSpeedKmh(), 6.0, "the ego must slow down to the speed of the CENTER vehicle");
            assertFalse(sawEmergencyBrake, "slowing down must not need emergency braking");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("slower CENTER vehicle nearer than a slower RIGHT vehicle: the ego does not sit beside it, it overtakes both on the left")
    void overtakesOnTheLeftWhenAnotherVehicleIsFurtherAheadInItsOwnLane() {
        try (SimulationEngine engine = runningEngine(null)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String right = addVehicle(engine, Lane.RIGHT, 150.0, SLOW_SPEED_KMH);
            String center = addVehicle(engine, Lane.CENTER, 80.0, SLOW_SPEED_KMH);

            boolean passedOnTheRight = false;
            double previousGap = Double.MAX_VALUE;
            boolean done = false;
            for (int i = 0; i < 1200 && !done; i++) {
                engine.tick();
                double gapNow = gapTo(engine, center);
                if (previousGap > 0.0 && gapNow <= 0.0 && egoIsRightOf(engine, center)) {
                    passedOnTheRight = true;
                }
                previousGap = gapNow;
                done = gapTo(engine, center) < 0 && gapTo(engine, right) < 0
                    && ego.getLane() == Lane.RIGHT && ego.getLaneChangeState() == LaneChangeState.IDLE;
            }

            assertFalse(passedOnTheRight, "the slower CENTER vehicle must not be passed on its right");
            assertTrue(done, "the ego must overtake both vehicles on the left and return to RIGHT; gap to CENTER vehicle "
                + gapTo(engine, center) + " m, to RIGHT vehicle " + gapTo(engine, right) + " m, lane " + ego.getLane());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("slower CENTER vehicle appears close ahead: the ego falls back, moves in behind it and overtakes on the left")
    void fallsBackAndOvertakesOnTheLeftWhenTheSlowerVehicleIsAlreadyClose() {
        try (SimulationEngine engine = runningEngine(null)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String center = addVehicle(engine, Lane.CENTER, 16.0, SLOW_SPEED_KMH);

            boolean passedOnTheRight = false;
            double previousGap = Double.MAX_VALUE;
            boolean sawEmergencyBrake = false;
            boolean passed = false;
            for (int i = 0; i < 1200 && !passed; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                double gapNow = gapTo(engine, center);
                if (previousGap > 0.0 && gapNow <= 0.0 && egoIsRightOf(engine, center)) {
                    passedOnTheRight = true;
                }
                previousGap = gapNow;
                passed = gapTo(engine, center) < -5.0;
            }

            assertFalse(passedOnTheRight, "the slower CENTER vehicle must not be passed on its right");
            assertTrue(passed, "the ego must still get past it, on the left; gap " + gapTo(engine, center) + " m, lane " + ego.getLane());
            assertFalse(sawEmergencyBrake, "falling back must not need emergency braking");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("after the overtake the ego returns LEFT -> CENTER -> RIGHT, one safe step at a time")
    void returnsProgressivelyToRightAfterOvertakingOnTheLeft() {
        try (SimulationEngine engine = runningEngine(null)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String center = addVehicle(engine, Lane.CENTER, 80.0, SLOW_SPEED_KMH);

            List<Lane> lanes = new ArrayList<>(List.of(Lane.RIGHT));
            boolean returnedBehindOrBesideVehicle = false;
            boolean done = false;
            for (int i = 0; i < 900 && !done; i++) {
                engine.tick();
                if (ego.getLane() != lanes.get(lanes.size() - 1)) {
                    // Every step back to the right must happen clear of the overtaken vehicle.
                    boolean movingRight = ego.getLane().centreMeters() > lanes.get(lanes.size() - 1).centreMeters();
                    if (movingRight && gapTo(engine, center) > -10.0) {
                        returnedBehindOrBesideVehicle = true;
                    }
                    lanes.add(ego.getLane());
                }
                done = gapTo(engine, center) < 0 && ego.getLane() == Lane.RIGHT
                    && ego.getLaneChangeState() == LaneChangeState.IDLE;
            }

            assertTrue(done, "the ego must end up back in RIGHT ahead of the overtaken vehicle, lanes visited: " + lanes);
            assertEquals(List.of(Lane.RIGHT, Lane.CENTER, Lane.LEFT, Lane.CENTER, Lane.RIGHT), lanes,
                "the ego must go out and come back one lane at a time, without oscillating");
            assertFalse(returnedBehindOrBesideVehicle, "the ego must only move back right once clear of the overtaken vehicle");
            assertFalse(overtaking(engine).isActive(), "the overtaking state must be cleared once back in RIGHT");
            assertEquals(EGO_SPEED_KMH, ego.getSpeedKmh(), 10.0, "the ego must be back near its cruise speed");
        }
    }
}
