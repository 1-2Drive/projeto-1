package com.one2drive.projeto1.domain;

public class Roadworks extends RoadHazard {
    public Roadworks(double positionMeters, Lane lane, HazardSeverity severity) {
        super(positionMeters, lane, severity);
    }
}
