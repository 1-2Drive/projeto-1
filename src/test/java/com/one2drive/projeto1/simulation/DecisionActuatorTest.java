package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DecisionPriority;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.RiskLevel;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionActuatorTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaultConfig();

    private static WorldState worldAt(double speedKmh) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("ACTUATOR");
        scenario.setName("Actuator");
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        WorldState world = ScenarioFactory.buildWorld(scenario, CONFIG);
        world.getEgo().setSpeedKmh(speedKmh);
        return world;
    }

    private static DrivingDecision reduceTo(double kmh) {
        DrivingDecision decision = new DrivingDecision();
        decision.apply(DrivingAction.REDUCE_SPEED, kmh, RiskLevel.HIGH, DecisionPriority.ROAD_HAZARD,
            "test", "test rule", 0L, List.of());
        return decision;
    }

    private static DrivingDecision brakeTo(double kmh) {
        DrivingDecision decision = new DrivingDecision();
        decision.apply(DrivingAction.BRAKE, kmh, RiskLevel.HIGH, DecisionPriority.COLLISION_AVOIDANCE,
            "test", "test rule", 0L, List.of());
        return decision;
    }

    @Test
    void holdsTheRecommendedSpeedInsideTheDeadBand() {
        WorldState world = worldAt(51.7);
        new DecisionActuator(CONFIG).applyDecision(world, reduceTo(52.0));
        assertEquals(0.0, world.getEgo().getAccelerationMps2(), 1e-9, "no braking when already at the target");
        assertEquals(52.0, world.getEgo().getSpeedKmh(), 1e-6, "speed snaps to the recommended value");
    }

    @Test
    void acceleratesGentlyWhenBelowTheDeadBand() {
        WorldState world = worldAt(50.6);
        new DecisionActuator(CONFIG).applyDecision(world, reduceTo(52.0));
        assertTrue(world.getEgo().getAccelerationMps2() > 0.0, "below target − 0.5 the car builds speed back up");
    }

    @Test
    void deceleratesProportionallyJustAboveTheDeadBand() {
        WorldState world = worldAt(53.0);
        new DecisionActuator(CONFIG).applyDecision(world, reduceTo(52.0));
        double accel = world.getEgo().getAccelerationMps2();
        assertEquals(-1.5, accel, 1e-9,
            "1 km/h above the target the deceleration is proportional (1.5 × 1), not the full comfortable braking");
    }

    @Test
    void brakesComfortablyWhenWellAboveTheTarget() {
        WorldState world = worldAt(56.0);
        new DecisionActuator(CONFIG).applyDecision(world, reduceTo(52.0));
        assertEquals(-CONFIG.getComfortableDecelerationMps2(), world.getEgo().getAccelerationMps2(), 1e-9);
    }

    @Test
    void brakeInsideTheDeadBandHoldsWithoutRaisingTheSpeed() {
        WorldState world = worldAt(19.7);
        new DecisionActuator(CONFIG).applyDecision(world, brakeTo(20.0));
        assertEquals(0.0, world.getEgo().getAccelerationMps2(), 1e-9, "inside the band a braking decision holds");
        assertEquals(19.7, world.getEgo().getSpeedKmh(), 1e-6, "a braking decision never raises the speed to the target");
    }

    @Test
    void reduceSpeedWithoutATargetSlowsDownComfortably() {
        WorldState world = worldAt(50.0);
        new DecisionActuator(CONFIG).applyDecision(world, reduceTo(Double.NaN));
        assertEquals(-CONFIG.getComfortableDecelerationMps2(), world.getEgo().getAccelerationMps2(), 1e-9);
    }

    private static DrivingDecision changeLaneLeft() {
        DrivingDecision decision = reduceTo(40.0);
        decision.applyLane(DrivingAction.CHANGE_LANE_LEFT, DecisionPriority.MANEUVER_SAFETY, "test", "test lane rule", List.of());
        return decision;
    }

    @Test
    void refusesALaneChangeThatEndsTooCloseBehindASlowerVehicle() {
        // 40 km/h behind nothing, a 30 km/h vehicle 33 m ahead in CENTER: clear of the blind spot
        // and 12 s from a collision, but after the 3 s maneuver only 25 m are left, not enough
        // to slow to 30 km/h 12 m behind it braking at 2 m/s2.
        WorldState world = worldAt(40.0);
        world.addActor(new OtherVehicle(33.0, Lane.CENTER, 30.0 / 3.6));
        DrivingDecision decision = changeLaneLeft();
        new DecisionActuator(CONFIG).applyDecision(world, decision);
        assertEquals(LaneChangeState.IDLE, world.getEgo().getLaneChangeState(), "the lane change does not start");
        assertEquals(DrivingAction.KEEP_LANE, decision.getLaneAction());
        assertEquals("Actuator safety guard", decision.getLaneSourceRule());
        assertTrue(decision.getLaneReason().contains("too close to follow safely"), decision.getLaneReason());
    }

    @Test
    void acceptsALaneChangeWithRoomToFollowTheVehicleAhead() {
        WorldState world = worldAt(40.0);
        world.addActor(new OtherVehicle(60.0, Lane.CENTER, 30.0 / 3.6));
        DrivingDecision decision = changeLaneLeft();
        new DecisionActuator(CONFIG).applyDecision(world, decision);
        assertEquals(LaneChangeState.REQUESTED, world.getEgo().getLaneChangeState(), "the lane change starts");
        assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction());
    }
}
