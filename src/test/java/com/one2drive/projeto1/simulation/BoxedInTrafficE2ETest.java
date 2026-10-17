package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slow traffic across every lane: three vehicles side by side ahead of the ego. From the
 * right lane the ego does not start an overtake it could never finish: it follows without
 * stop-and-go. Already in CENTER, it gives up, falls back and returns to the right lane
 * without ever getting too close to a vehicle in the lane it is in or moving into, also in
 * rain and with reduced visibility.
 */
class BoxedInTrafficE2ETest {

    private static final double SLOW_MPS = 1.25;     // 4.5 km/h
    private static final double MIN_SAME_LANE_GAP_METERS = 6.0;

    private static SimulationEngine boxedIn(ExpertBackendType backend, double aheadMeters) {
        return boxedIn(backend, aheadMeters, Lane.RIGHT);
    }

    private static SimulationEngine boxedIn(ExpertBackendType backend, double aheadMeters, Lane egoLane) {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        ScenarioDefinition scenario = library.findById("S1").orElseThrow();
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.getWorldState().getEgo().setLane(egoLane);
        engine.getWorldState().getEgo().setLateralPositionMeters(egoLane.centreMeters());
        engine.getWorldState().getEgo().setSpeedKmh(40.0);
        engine.start();
        for (Lane lane : Lane.values()) {
            ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
            vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
            vehicle.setLane(lane);
            vehicle.setPositionMeters(aheadMeters);
            vehicle.setSpeedKmh(SLOW_MPS * 3.6);
            engine.addActorAhead(vehicle);
        }
        return engine;
    }

    /** Smallest gap to a vehicle in the lane the ego is in (by its lateral position). */
    private static double sameLaneGap(SimulationEngine engine) {
        WorldState world = engine.getWorldState();
        double lateral = world.getEgo().getLateralPositionMeters();
        double best = Double.POSITIVE_INFINITY;
        for (DynamicActor actor : world.getActors()) {
            if (!actor.isActive() || actor.getLane() == null) {
                continue;
            }
            if (Math.abs(actor.getLane().centreMeters() - lateral) < 2.0) {
                best = Math.min(best, Math.abs(world.vehicleSignedDistance(actor)));
            }
        }
        return best;
    }

    private record Run(boolean leftRight, boolean backRight, double minGap,
                       double maxSpeedAfterWeather, List<String> trace) {
    }

    private static Run drive(SimulationEngine engine, int ticks) {
        boolean leftRight = false;
        boolean backRight = false;
        double minGap = Double.POSITIVE_INFINITY;
        double maxSpeed = 0.0;
        List<String> trace = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            engine.tick();
            var ego = engine.getWorldState().getEgo();
            if (ego.getLane() != Lane.RIGHT) {
                leftRight = true;
            } else if (leftRight && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                backRight = true;
            }
            minGap = Math.min(minGap, sameLaneGap(engine));
            if (i > 20) {
                maxSpeed = Math.max(maxSpeed, ego.getSpeedKmh());
            }
            if (i % 10 == 0) {
                var d = engine.getCurrentDecision();
                trace.add(i + ": lane=" + ego.getLane() + " v=" + Math.round(ego.getSpeedKmh() * 10) / 10.0
                    + " gap=" + Math.round(sameLaneGap(engine) * 10) / 10.0
                    + " speed=" + d.getSpeedSourceRule() + " lane=" + d.getLaneAction() + " " + d.getLaneSourceRule());
            }
        }
        return new Run(leftRight, backRight, minGap, maxSpeed, trace);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("slow traffic in every lane, from the right lane: does not start an overtake it would have to give up")
    void doesNotStartAnImpossibleOvertakeFromTheRightLane(ExpertBackendType backend) {
        try (SimulationEngine engine = boxedIn(backend, 60.0)) {
            Run run = drive(engine, 900);
            String trace = String.join("\n", run.trace());
            assertFalse(run.leftRight(), "the CENTER lane is as slow as the right one, so the ego stays right\n" + trace);
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(), trace);
            assertTrue(run.minGap() >= MIN_SAME_LANE_GAP_METERS,
                "never closer than " + MIN_SAME_LANE_GAP_METERS + " m to a vehicle in its lane: " + run.minGap() + "\n" + trace);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("boxed in by slow traffic in every lane, already in CENTER: gives up and returns right without collision")
    void givesUpAnImpossibleOvertakeAndReturnsRight(ExpertBackendType backend) {
        try (SimulationEngine engine = boxedIn(backend, 60.0, Lane.CENTER)) {
            Run run = drive(engine, 900);
            String trace = String.join("\n", run.trace());
            assertTrue(run.backRight(), "it gives up and returns to the right lane\n" + trace);
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(),
                "and stays there, behind the slow vehicle, instead of trying again\n" + trace);
            assertTrue(run.minGap() >= MIN_SAME_LANE_GAP_METERS,
                "never closer than " + MIN_SAME_LANE_GAP_METERS + " m to a vehicle in its lane: " + run.minGap() + "\n" + trace);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("boxed in in rain: same outcome, never above the fuzzy weather ceiling")
    void givesUpAndReturnsRightInRain(ExpertBackendType backend) {
        try (SimulationEngine engine = boxedIn(backend, 60.0, Lane.CENTER)) {
            engine.setRain(true);
            Run run = drive(engine, 900);
            String trace = String.join("\n", run.trace());
            assertTrue(run.backRight(), "it gives up and returns to the right lane\n" + trace);
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(), trace);
            assertTrue(run.minGap() >= MIN_SAME_LANE_GAP_METERS, "min gap " + run.minGap() + "\n" + trace);
            assertTrue(run.maxSpeedAfterWeather() <= 50.57 + 1.0,
                "the fuzzy ceiling still applies: " + run.maxSpeedAfterWeather());
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("boxed in with reduced visibility: same outcome")
    void givesUpAndReturnsRightWithReducedVisibility(ExpertBackendType backend) {
        try (SimulationEngine engine = boxedIn(backend, 60.0, Lane.CENTER)) {
            engine.setWeatherFactors(0.5, 1.0);
            Run run = drive(engine, 900);
            String trace = String.join("\n", run.trace());
            assertTrue(run.backRight(), "it gives up and returns to the right lane\n" + trace);
            assertEquals(Lane.RIGHT, engine.getWorldState().getEgo().getLane(), trace);
            assertTrue(run.minGap() >= MIN_SAME_LANE_GAP_METERS, "min gap " + run.minGap() + "\n" + trace);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("following a slow vehicle settles on its speed without stop-and-go")
    void followsTheSlowVehicleWithoutStopAndGo(ExpertBackendType backend) {
        try (SimulationEngine engine = boxedIn(backend, 60.0)) {
            Run run = drive(engine, 600);
            String trace = String.join("\n", run.trace());
            // Settled behind the slow vehicle: the stop-and-go was a brake to 1.5 km/h, then the
            // cruise speed (60 km/h) driving it at the vehicle ahead, in a loop.
            double frontKmh = SLOW_MPS * 3.6;
            int brakes = 0;
            List<String> offending = new ArrayList<>();
            int cruiseRequests = 0;
            double minSpeed = Double.POSITIVE_INFINITY;
            double maxSpeed = 0.0;
            for (int i = 0; i < 300; i++) {
                engine.tick();
                var decision = engine.getCurrentDecision();
                if (decision.getSpeedAction() == DrivingAction.BRAKE) {
                    brakes++;
                    offending.add(i + " " + decision.getSpeedSourceRule() + " " + decision.getRecommendedSpeedKmh()
                        + " gap=" + sameLaneGap(engine) + " v=" + engine.getWorldState().getEgo().getSpeedKmh());
                }
                if (decision.getRecommendedSpeedKmh() > frontKmh + 5.0) {
                    cruiseRequests++;
                    offending.add(i + " " + decision.getSpeedSourceRule() + " " + decision.getRecommendedSpeedKmh()
                        + " gap=" + sameLaneGap(engine) + " v=" + engine.getWorldState().getEgo().getSpeedKmh());
                }
                double speed = engine.getWorldState().getEgo().getSpeedKmh();
                minSpeed = Math.min(minSpeed, speed);
                maxSpeed = Math.max(maxSpeed, speed);
            }
            assertEquals(0, brakes, "no braking to below the front vehicle's speed once following: " + offending);
            assertTrue(cruiseRequests <= 3,
                "not driven at the vehicle ahead by the cruise speed: " + offending);
            assertTrue(minSpeed >= frontKmh - 1.5 && maxSpeed <= frontKmh + 1.5,
                "speed stays at the front vehicle's: " + minSpeed + " .. " + maxSpeed + "\n" + trace);
        }
    }
}
