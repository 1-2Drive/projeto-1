package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The road map end to end: matrix cells are what the sensors see, the road loops back
 * to row 0, and Drools steers around or stops for what the matrix holds.
 */
class RoadMapE2ETest {

    private static SimulationEngine engine(double speedKmh, int[][] cells) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("MAP_TEST");
        scenario.setName("Road map test");
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        scenario.setMap(cells);
        SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        });
        engine.start();
        return engine;
    }

    @Test
    @Timeout(30)
    @DisplayName("after the last row the car continues at row 0 and meets the same cell again")
    void roadLoopsBackToTheFirstRow() {
        int[][] cells = new int[40][3];
        cells[20][Lane.CENTER.ordinal()] = 9;

        try (SimulationEngine engine = engine(50, cells)) {
            boolean wasVisible = false;
            int sightings = 0;
            for (int i = 0; i < 500 && (sightings < 2 || engine.roadMapSnapshot().lap() < 1); i++) {
                boolean visible = engine.tick().cameraDetectedObjectIds().contains("r20-CENTER");
                if (visible && !wasVisible) {
                    sightings++;
                }
                wasVisible = visible;
            }

            assertTrue(engine.getWorldState().getEgo().getPositionMeters() > 200.0,
                "The car must drive past the end of the 200 m map instead of stopping there");
            assertTrue(engine.roadMapSnapshot().lap() >= 1);
            assertTrue(sightings >= 2, "The same sign should come into view again on the next lap, saw " + sightings);
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("an obstacle across every lane stops the car; opening one cell lets it steer through")
    void wallOfObstaclesStopsTheCarUntilALaneOpens() {
        int[][] cells = new int[100][3];
        cells[30] = new int[] {1, 1, 1};

        try (SimulationEngine engine = engine(70, cells)) {
            boolean stopRuleSeen = false;
            boolean swerved = false;
            for (int i = 0; i < 300; i++) {
                engine.tick();
                stopRuleSeen |= "Stop before obstacle when no lane is free".equals(engine.getCurrentDecision().getSourceRule());
                DrivingAction action = engine.getCurrentDecision().getAction();
                swerved |= action == DrivingAction.CHANGE_LANE_LEFT || action == DrivingAction.CHANGE_LANE_RIGHT;
            }

            double gap = engine.getWorldState().distanceAhead(150.0);
            assertTrue(stopRuleSeen, "Drools should explain the stop with the no-free-lane rule");
            assertFalse(swerved, "No lane is free, so the car must not try to change lane");
            assertEquals(0.0, engine.getWorldState().getEgo().getSpeedKmh(), 0.5);
            assertTrue(gap > 8.0 && gap <= 20.0,
                "The car should stop short of the wall but outside the emergency zone, gap was " + gap + " m");

            engine.setMapCell(30, Lane.LEFT, 0);
            for (int i = 0; i < 300 && engine.getWorldState().getEgo().getPositionMeters() < 170.0; i++) {
                engine.tick();
            }

            assertEquals(Lane.LEFT, engine.getWorldState().getEgo().getLane(), "The car should use the opened lane");
            assertTrue(engine.getWorldState().getEgo().getPositionMeters() >= 170.0, "The car should drive past the wall");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("the matrix obstacle course is driven lap after lap without touching an obstacle")
    void obstacleCourseIsDrivenWithoutCollisions() {
        ScenarioDefinition course = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json")
            .findById("S15_MATRIX_COURSE").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), course, true, d -> {
        })) {
            engine.start();
            RoadMap map = engine.getWorldState().getRoadMap();
            Set<Lane> lanesUsed = EnumSet.noneOf(Lane.class);
            int firstObstacleAvoidances = 0;
            DrivingAction previous = null;
            // The course slows the car for the pothole and nothing accelerates it again
            // (that is a driver request), so a lap takes a while.
            for (int i = 0; i < 2500 && firstObstacleAvoidances < 2; i++) {
                engine.tick();
                var ego = engine.getWorldState().getEgo();
                Lane physicalLane = Lane.fromLateralPosition(ego.getLateralPositionMeters());
                int row = map.rowAt(ego.getPositionMeters());
                lanesUsed.add(ego.getLane());
                assertFalse(map.get(row, physicalLane) == 1,
                    "Drove into the obstacle at row " + row + ", lane " + physicalLane);

                DrivingAction action = engine.getCurrentDecision().getAction();
                if (action == DrivingAction.CHANGE_LANE_LEFT && previous != action && row < 24) {
                    firstObstacleAvoidances++;
                }
                previous = action;
            }

            assertTrue(engine.roadMapSnapshot().lap() >= 1, "The course should loop back to row 0");
            assertEquals(2, firstObstacleAvoidances, "The first obstacle must be avoided again on the next lap");
            assertEquals(EnumSet.allOf(Lane.class), lanesUsed, "The slalom uses every lane");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("painting a cell while running puts a hazard in front of the camera, painting 0 removes it")
    void paintingACellChangesWhatTheCameraSees() {
        try (SimulationEngine engine = engine(50, new int[100][3])) {
            for (int i = 0; i < 5; i++) {
                engine.tick();
            }
            RoadMap map = engine.getWorldState().getRoadMap();
            int row = (map.rowAt(engine.getWorldState().getEgo().getPositionMeters()) + 16) % map.rows();
            String id = RoadMap.cellId(row, Lane.CENTER);

            engine.setMapCell(row, Lane.CENTER, 1);
            boolean seen = false;
            for (int i = 0; i < 5 && !seen; i++) {
                seen = engine.tick().cameraDetectedObjectIds().contains(id);
            }
            assertTrue(seen, "The painted obstacle should be detected by the camera");

            engine.setMapCell(row, Lane.CENTER, 0);
            engine.tick();
            assertTrue(engine.getWorldState().findRoadElementById(id).isEmpty());
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("from an outer lane the road edge counts as an occupied side")
    void roadEdgeIsAnOccupiedSide() {
        try (SimulationEngine engine = engine(50, new int[100][3])) {
            var world = engine.getWorldState();
            world.getEgo().setLane(Lane.LEFT);

            WorldState.LaneOccupancy left = world.sideLaneOccupancy(
                com.one2drive.projeto1.domain.LaneChangeDirection.LEFT, 30, 60);
            WorldState.LaneOccupancy right = world.sideLaneOccupancy(
                com.one2drive.projeto1.domain.LaneChangeDirection.RIGHT, 30, 60);

            assertTrue(left.occupied() && left.roadEdge());
            assertFalse(right.occupied() || right.roadEdge());
            assertEquals(Lane.RIGHT, right.lane(), "Occupancy is reported by side, as the rules read it");
        }
    }
}
