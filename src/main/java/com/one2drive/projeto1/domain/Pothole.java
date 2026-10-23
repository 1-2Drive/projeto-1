package com.one2drive.projeto1.domain;

public class Pothole extends RoadHazard {
    public Pothole(double positionMeters, Lane lane, HazardSeverity severity) {
        super(positionMeters, lane, severity);
    }
}
