package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pedestrian first seen when comfortable braking can no longer stop short of it: at 90 km/h
 * the approach curve and the comfortable mandatory stop reached the pedestrian at 38 km/h.
 * The vehicle must brake hard as soon as that is the case (the same escalation the obstacle
 * and barrier rules make) and come to rest with the pedestrian still ahead.
 */
class PedestrianAtSpeedE2ETest {
    private static final String HARD_BRAKING_RULE =
        "Emergency brake for pedestrian when comfortable braking no longer stops in time";

    private static SimulationEngine runningEngine(ExpertBackendType backend, Lane lane, double speedKmh) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("PEDESTRIAN_AT_SPEED");
        scenario.setName("Pedestrian ahead at speed");
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

    private static Pedestrian addPedestrian(SimulationEngine engine, Lane lane, double metersAhead, double speedKmh) {
        ScenarioActorDefinition pedestrian = new ScenarioActorDefinition();
        pedestrian.setType(ScenarioActorType.PEDESTRIAN);
        pedestrian.setLane(lane);
        pedestrian.setPositionMeters(metersAhead);
        pedestrian.setSpeedKmh(speedKmh);
        String id = engine.addActorAheadAndGetId(pedestrian);
        engine.tick();
        return (Pedestrian) engine.getWorldState().findActorById(id).orElseThrow();
    }

    /** Drives until the vehicle is at rest; the closest it came to the pedestrian while moving. */
    private record Approach(double closestMovingMeters, boolean hardBraking, boolean cameToRest) {
    }

    private static Approach approach(SimulationEngine engine, Pedestrian pedestrian) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        double closest = Double.POSITIVE_INFINITY;
        boolean hardBraking = false;
        boolean rest = false;
        for (int i = 0; i < 300; i++) {
            engine.tick();
            var decision = engine.getCurrentDecision();
            hardBraking |= HARD_BRAKING_RULE.equals(decision.getSpeedSourceRule())
                && decision.getSpeedAction() == DrivingAction.EMERGENCY_BRAKE;
            double distance = engine.getWorldState().vehicleSignedDistance(pedestrian);
            boolean overlaps = Math.abs(pedestrian.getLateralPositionMeters() - ego.getLateralPositionMeters()) < 1.75;
            if (ego.getSpeedKmh() > 1.0 && overlaps && pedestrian.isInRoadway()) {
                closest = Math.min(closest, distance);
            }
            if (ego.getSpeedKmh() < 0.6) {
                rest = true;
                break;
            }
        }
        return new Approach(closest, hardBraking, rest);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("at 90 km/h a pedestrian standing 90 m ahead in the lane is braked for hard and never reached")
    void standingPedestrianAheadAtNinetyIsNotReached(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend, Lane.RIGHT, 90.0)) {
            Pedestrian pedestrian = addPedestrian(engine, Lane.RIGHT, 90.0, 0.0);
            Approach approach = approach(engine, pedestrian);
            assertTrue(approach.hardBraking(), "comfortable braking cannot stop from 90 km/h in 90 m: the hard-braking rule must fire");
            assertTrue(approach.cameToRest(), "the vehicle must come to rest for the pedestrian");
            assertTrue(approach.closestMovingMeters() > 3.0,
                "the vehicle reached the pedestrian: closest while moving " + approach.closestMovingMeters() + " m");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("at 90 km/h a pedestrian crossing into the lane from 80 m ahead is never reached")
    void pedestrianCrossingIntoTheLaneAtNinetyIsNotReached(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend, Lane.LEFT, 90.0)) {
            // From the centre lane a pedestrian crosses towards the left sidewalk, through the ego's lane.
            Pedestrian pedestrian = addPedestrian(engine, Lane.CENTER, 80.0, 4.0);
            Approach approach = approach(engine, pedestrian);
            assertTrue(approach.hardBraking(), "the hard-braking rule must fire for a crossing 80 m ahead at 90 km/h");
            assertTrue(approach.closestMovingMeters() > 3.0,
                "the vehicle reached the pedestrian: closest while moving " + approach.closestMovingMeters() + " m");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("at 50 km/h the same pedestrian is braked for comfortably, without the hard-braking rule")
    void pedestrianAheadAtFiftyIsBrakedForComfortably(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend, Lane.RIGHT, 50.0)) {
            Pedestrian pedestrian = addPedestrian(engine, Lane.RIGHT, 90.0, 0.0);
            Approach approach = approach(engine, pedestrian);
            assertTrue(!approach.hardBraking(), "from 50 km/h comfortable braking stops well short: no hard braking");
            assertTrue(approach.cameToRest(), "the vehicle must come to rest for the pedestrian");
            assertTrue(approach.closestMovingMeters() > 3.0, "closest while moving " + approach.closestMovingMeters() + " m");
        }
    }
}
