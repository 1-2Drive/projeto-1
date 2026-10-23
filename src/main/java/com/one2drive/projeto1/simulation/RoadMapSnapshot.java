package com.one2drive.projeto1.simulation;

import java.util.List;

/** Read-only copy of the road map and the ego's place on it. */
public record RoadMapSnapshot(
    int[][] cells,
    double blockLengthMeters,
    int egoRow,
    long lap,
    List<MapLegend.Entry> legend
) {
}
