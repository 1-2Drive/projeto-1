package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.decision.LaneChangeCheck;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RoadMarkingType;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Why not change lane?": after the rules fire, each engine is asked every sub-goal of
 * safeToChangeLane for each side, and names the one that fails even when no rule proposed
 * a lane change.
 */
class LaneChangeWhyNotE2ETest {

    private static SimulationEngine engine(ExpertBackendType backend, Lane egoLane, RoadMarkingType rightMarking) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("LANE_CHANGE_WHY_NOT");
        scenario.setName("Why not change lane");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(egoLane);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        if (rightMarking != null) {
            scenario.setRightMarking(rightMarking);
        }
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.start();
        return engine;
    }

    private static void addVehicle(SimulationEngine engine, Lane lane, double metersAhead, double speedKmh) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(metersAhead);
        vehicle.setSpeedKmh(speedKmh);
        engine.addActorAhead(vehicle);
    }

    private static void tick(SimulationEngine engine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            engine.tick();
        }
    }

    private static LaneChangeCheck check(SimulationEngine engine, Lane side) {
        List<LaneChangeCheck> checks = engine.getCurrentDecision().getLaneChecks();
        assertEquals(2, checks.size(), "one check per side");
        return checks.stream().filter(check -> check.side() == side).findFirst().orElseThrow();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("in RIGHT with nothing around: left is safe, right has no lane")
    void rightLaneHasNoLaneToTheRight(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.RIGHT, null)) {
            tick(engine, 5);
            assertTrue(check(engine, Lane.LEFT).allowed());
            LaneChangeCheck right = check(engine, Lane.RIGHT);
            assertFalse(right.allowed());
            assertTrue(right.maneuverIdle());
            assertFalse(right.noContinuousLine());
            assertEquals("no lane to the right", right.explanation());
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("a vehicle alongside: the side is not clear")
    void vehicleAlongsideMakesTheSideNotClear(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.RIGHT, null)) {
            addVehicle(engine, Lane.CENTER, 0.0, 50.0);
            tick(engine, 5);
            LaneChangeCheck left = check(engine, Lane.LEFT);
            assertTrue(left.maneuverIdle());
            assertTrue(left.noContinuousLine());
            assertFalse(left.sideClear());
            assertEquals("blind-spot sensor reports the left side occupied", left.explanation());
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("a continuous line between RIGHT and CENTER forbids changing left")
    void continuousLineForbidsChangingLeft(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.RIGHT, RoadMarkingType.CONTINUOUS)) {
            tick(engine, 5);
            LaneChangeCheck left = check(engine, Lane.LEFT);
            assertFalse(left.noContinuousLine());
            assertEquals("continuous line between RIGHT and CENTER", left.explanation());
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("while a lane change runs no other one is safe")
    void laneChangeInProgressBlocksBothSides(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.CENTER, null)) {
            // Keep right moves the ego from CENTER to RIGHT after 4 s.
            boolean changing = false;
            for (int i = 0; i < 100 && !changing; i++) {
                engine.tick();
                changing = engine.getWorldState().getEgo().getLaneChangeState() != LaneChangeState.IDLE
                    && engine.getCurrentDecision().getLaneChecks().stream().noneMatch(LaneChangeCheck::maneuverIdle);
            }
            assertTrue(changing, "a lane change must start");
            assertFalse(check(engine, Lane.LEFT).allowed());
            assertTrue(check(engine, Lane.RIGHT).explanation().contains("a lane change is already in progress"));
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("Drools and Prolog give the same answer every cycle")
    void bothEnginesAgree() {
        try (SimulationEngine drools = engine(ExpertBackendType.DROOLS, Lane.CENTER, null);
             SimulationEngine prolog = engine(ExpertBackendType.PROLOG, Lane.CENTER, null)) {
            for (SimulationEngine engine : List.of(drools, prolog)) {
                addVehicle(engine, Lane.RIGHT, 5.0, 45.0);
                addVehicle(engine, Lane.LEFT, 60.0, 30.0);
            }
            for (int i = 0; i < 150; i++) {
                drools.tick();
                prolog.tick();
                assertEquals(drools.getCurrentDecision().getLaneChecks(), prolog.getCurrentDecision().getLaneChecks(),
                    "tick " + i);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("in RIGHT at 50 km/h with a 20 km/h vehicle 45 m ahead in CENTER: left is clear but there is no room to follow")
    void namesTheVehicleAheadInTheTargetLaneThatIsTooCloseToFollow(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.RIGHT, null)) {
            // Beyond the 30 m of the blind-spot sensor, so the side is clear; within the front
            // radar's range. Closing in at 30 km/h, after the 3 s lane change 20 m are left:
            // not enough to slow to 20 km/h 12 m behind it at 2 m/s2.
            addVehicle(engine, Lane.CENTER, 45.0, 20.0);
            tick(engine, 5);
            LaneChangeCheck left = check(engine, Lane.LEFT);
            assertTrue(left.sideClear(), "the blind-spot sensor does not see 45 m ahead");
            assertFalse(left.roomToFollow());
            assertFalse(left.allowed());
            assertTrue(left.explanation().contains("vehicle ahead in the CENTER lane too close to follow safely"),
                left.explanation());
        }
    }
}
