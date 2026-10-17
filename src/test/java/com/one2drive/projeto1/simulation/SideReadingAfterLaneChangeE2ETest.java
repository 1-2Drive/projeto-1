package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The blind-spot sensor reads every 200 ms and describes the sides of the lane the ego is
 * committed to. For a tick or two after a lane change settles, its last reading still
 * describes the sides of the previous lane. The rules that return right, and the one that
 * marks the overtaken vehicle passed, must not act on that reading: they would pull back in
 * right behind the vehicle being overtaken.
 *
 * <p>The situation is the one built from the toolbox: one vehicle in each lane at 30 km/h,
 * each added 150 m ahead of the ego 10 s after the previous one.
 */
class SideReadingAfterLaneChangeE2ETest {

    /** The return-right rules that need the right lane clear for 80 m ahead. */
    private static final Set<String> RIGHT_LANE_CLEAR_80M_RULES = Set.of(
        "Return to right lane after overtaking slow vehicle",
        "Enforce keep right road law when right lane is safe");

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("after a lane change, returning right waits for a reading of the new right side")
    void returnRightUsesASideReadingTakenInTheCurrentLane(ExpertBackendType backend) {
        ScenarioDefinition scenario = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json").findById("S1").orElseThrow();
        try (SimulationEngine engine = BackendTestSupport.engine(scenario, backend)) {
            engine.start();
            List<Lane> order = List.of(Lane.CENTER, Lane.LEFT, Lane.RIGHT);
            double minGap = Double.POSITIVE_INFINITY;
            int laneChanges = 0;
            Lane previousLane = engine.getWorldState().getEgo().getLane();
            for (int tick = 0; tick < 1200; tick++) {
                if (tick % 100 == 0 && tick / 100 < order.size()) {
                    addVehicle(engine, order.get(tick / 100));
                }
                engine.tick();
                WorldState world = engine.getWorldState();
                var decision = engine.getCurrentDecision();
                if (decision.getLaneAction() == DrivingAction.RETURN_RIGHT
                        && RIGHT_LANE_CLEAR_80M_RULES.contains(decision.getLaneSourceRule())) {
                    double rightFront = world.sideLaneOccupancy(LaneChangeDirection.RIGHT,
                        engine.getConfig().getBlindSpotRangeMeters(), 60.0).frontDistanceMeters();
                    if (rightFront <= 80.0) {
                        fail("t=" + decision.getTimestamp() + " '" + decision.getLaneSourceRule()
                            + "' returned right with a vehicle " + Math.round(rightFront)
                            + " m ahead in the right-side lane");
                    }
                }
                minGap = Math.min(minGap, overlappingGap(world));
                if (world.getEgo().getLane() != previousLane) {
                    laneChanges++;
                    previousLane = world.getEgo().getLane();
                }
            }
            assertTrue(minGap > 20.0, "never closer than 20 m to a vehicle it overlaps, was " + minGap);
            assertTrue(laneChanges <= 4, "no back-and-forth between the lanes, changed lane " + laneChanges + " times");
        }
    }

    private static void addVehicle(SimulationEngine engine, Lane lane) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(150.0);
        vehicle.setSpeedKmh(30.0);
        engine.addActorAhead(vehicle);
    }

    /** Smallest gap ahead to a vehicle whose lane the ego body overlaps laterally. */
    private static double overlappingGap(WorldState world) {
        double lateral = world.getEgo().getLateralPositionMeters();
        double best = Double.POSITIVE_INFINITY;
        for (DynamicActor actor : world.getActors()) {
            if (!actor.isActive() || actor.getLane() == null) {
                continue;
            }
            double gap = world.signedDistance(actor.getPositionMeters());
            if (Math.abs(actor.getLane().centreMeters() - lateral) < 2.6 && gap > -6.0) {
                best = Math.min(best, gap);
            }
        }
        return best;
    }
}
