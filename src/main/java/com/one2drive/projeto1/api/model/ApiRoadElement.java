package com.one2drive.projeto1.api.model;

public record ApiRoadElement(
    String id,
    String type,
    double positionMeters,
    String lane,
    String details
) {
}
