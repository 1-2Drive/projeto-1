package com.one2drive.projeto1.api.model;

public record ApiRoadElement(
    String id,
    String type,
    double positionMeters,
    String lane,
    String details,
    double worldPositionMeters
) {
    public ApiRoadElement(String id, String type, double positionMeters, String lane, String details) {
        this(id, type, positionMeters, lane, details, positionMeters);
    }
}

