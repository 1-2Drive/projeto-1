package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.SpeedRadar;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadMapTest {

    private static RoadMap map(int[][] cells) {
        return new RoadMap(cells, 5.0, MapLegend.defaults());
    }

    @Test
    @DisplayName("each non-zero cell becomes a road element at the start of its block, named after the cell")
    void cellsBecomeRoadElements() {
        RoadMap map = map(new int[][] {{0, 0, 0}, {0, 1, 0}, {2, 0, 9}});

        assertEquals(3, map.elements().size());
        RoadElement obstacle = map.findElement("r1-CENTER").orElseThrow();
        assertInstanceOf(StaticObstacle.class, obstacle);
        assertEquals(5.0, obstacle.getPositionMeters());
        assertEquals(Lane.CENTER, obstacle.getLane());

        assertInstanceOf(Pothole.class, map.findElement("r2-LEFT").orElseThrow());
        SpeedLimitSign sign = (SpeedLimitSign) map.findElement("r2-RIGHT").orElseThrow();
        assertEquals(50, sign.getLimitKmh());
    }

    @Test
    @DisplayName("the road loops: rows and distances wrap around the map length")
    void distancesWrapAroundTheLoop() {
        RoadMap map = RoadMap.empty(100, 5.0, MapLegend.defaults());

        assertEquals(500.0, map.lengthMeters());
        assertEquals(0, map.rowAt(0.0));
        assertEquals(99, map.rowAt(499.9));
        assertEquals(1, map.rowAt(507.0));
        assertEquals(2, map.lapAt(1000.0));

        assertEquals(20.0, map.distanceAhead(490.0, 10.0), 1e-9);
        assertEquals(20.0, map.distanceAhead(990.0, 10.0), 1e-9);
        assertEquals(-20.0, map.signedDistance(10.0, 490.0), 1e-9);
        assertEquals(30.0, map.signedDistance(480.0, 10.0), 1e-9);
    }

    @Test
    @DisplayName("placing an element snaps it to the block that contains the position")
    void placeSnapsToBlock() {
        RoadMap map = RoadMap.empty(100, 5.0, MapLegend.defaults());
        ScenarioRoadElementDefinition obstacle = new ScenarioRoadElementDefinition();
        obstacle.setType(ScenarioRoadElementType.STATIC_OBSTACLE);
        obstacle.setLane(Lane.RIGHT);

        String id = map.place(obstacle, 593.0);

        assertEquals("r18-RIGHT", id);
        assertEquals(1, map.get(18, Lane.RIGHT));
        assertEquals(90.0, map.findElement(id).orElseThrow().getPositionMeters());

        assertTrue(map.clear(id));
        assertEquals(0, map.get(18, Lane.RIGHT));
        assertTrue(map.elements().isEmpty());
    }

    @Test
    @DisplayName("the legend reuses codes for known elements and adds codes for new parameters")
    void legendAddsCodesForUnknownParameters() {
        MapLegend legend = MapLegend.defaults();
        ScenarioRoadElementDefinition limit = new ScenarioRoadElementDefinition();
        limit.setType(ScenarioRoadElementType.SPEED_LIMIT);

        limit.setSpeedLimitKmh(50);
        assertEquals(9, legend.codeFor(limit));

        limit.setSpeedLimitKmh(40);
        int added = legend.codeFor(limit);
        assertEquals(15, added);
        assertEquals("Limit 40 km/h", legend.entry(added).orElseThrow().label());
        assertEquals(added, legend.codeFor(limit));
    }

    @Test
    @DisplayName("a speed radar occupies one infrastructure marker and its map code spans all lanes")
    void radarMapCodeRepresentsAllLaneInfrastructure() {
        RoadMap map = RoadMap.empty(20, 5.0, MapLegend.defaults());
        ScenarioRoadElementDefinition radar = new ScenarioRoadElementDefinition();
        radar.setType(ScenarioRoadElementType.SPEED_RADAR);
        radar.setLane(Lane.RIGHT);
        radar.setSpeedLimitKmh(70);
        assertEquals("r4-ALL", map.place(radar, 20.0));
        assertEquals(1, map.elements().stream().filter(SpeedRadar.class::isInstance).count());
        assertEquals(map.get(4, Lane.LEFT), map.get(4, Lane.CENTER));
        assertEquals(map.get(4, Lane.CENTER), map.get(4, Lane.RIGHT));
        SpeedRadar device = (SpeedRadar) map.elements().get(0);
        assertTrue(device.getLane() == null);
        assertEquals(70.0, device.getSpeedLimitKmh());
        assertTrue(map.clear("r4-ALL"));
        assertEquals(0, map.get(4, Lane.LEFT) + map.get(4, Lane.CENTER) + map.get(4, Lane.RIGHT));
    }

    @Test
    @DisplayName("a malformed matrix or an unknown code is rejected")
    void rejectsInvalidMatrix() {
        assertThrows(IllegalArgumentException.class, () -> map(new int[][] {{0, 0}}));
        assertThrows(IllegalArgumentException.class, () -> map(new int[][] {{0, 0, 99}}));
        assertThrows(IllegalArgumentException.class, () -> map(new int[0][]));

        RoadMap map = map(new int[][] {{0, 0, 0}});
        assertThrows(IllegalArgumentException.class, () -> map.set(1, Lane.LEFT, 1));
        assertFalse(map.clear("r0-LEFT"));
    }
}
