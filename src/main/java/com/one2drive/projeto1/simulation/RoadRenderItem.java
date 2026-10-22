package com.one2drive.projeto1.simulation;

public record RoadRenderItem(
    String type,
    String id,
    double positionMeters,
    String lane,
    String details,
    double worldPositionMeters,
    /** Metres from the centre line for an actor that moves across the lanes (a pedestrian), else null. */
    Double lateralPositionMeters
) {
    public RoadRenderItem(String type, String id, double positionMeters, String lane, String details) {
        this(type, id, positionMeters, lane, details, positionMeters);
    }

    public RoadRenderItem(String type, String id, double positionMeters, String lane, String details,
                          double worldPositionMeters) {
        this(type, id, positionMeters, lane, details, worldPositionMeters, null);
    }
}

