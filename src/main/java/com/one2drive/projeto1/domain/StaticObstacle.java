package com.one2drive.projeto1.domain;

public class StaticObstacle extends RoadHazard {
    public StaticObstacle(double positionMeters, Lane lane, HazardSeverity severity) {
        super(positionMeters, lane, severity);
    }
}
