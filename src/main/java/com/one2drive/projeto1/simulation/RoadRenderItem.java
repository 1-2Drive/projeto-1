package com.one2drive.projeto1.simulation;

public record RoadRenderItem(
    String type,
    String id,
    double positionMeters,
    String lane,
    String details,
    double worldPositionMeters
) {
    public RoadRenderItem(String type, String id, double positionMeters, String lane, String details) {
        this(type, id, positionMeters, lane, details, positionMeters);
    }
}

