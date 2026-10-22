package com.one2drive.projeto1.api.model;

public record ApiRoadElement(
    String id,
    String type,
    double positionMeters,
    String lane,
    String details,
    double worldPositionMeters,
    /** Metres from the centre line for a pedestrian, which walks across the lanes; else null. */
    Double lateralPositionMeters
) {
    public ApiRoadElement(String id, String type, double positionMeters, String lane, String details) {
        this(id, type, positionMeters, lane, details, positionMeters);
    }

    public ApiRoadElement(String id, String type, double positionMeters, String lane, String details,
                          double worldPositionMeters) {
        this(id, type, positionMeters, lane, details, worldPositionMeters, null);
    }
}

