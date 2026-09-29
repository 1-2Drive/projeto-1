package com.one2drive.projeto1.domain;

public abstract class RoadHazard extends RoadElement {
    private final HazardSeverity severity;

    protected RoadHazard(double positionMeters, Lane lane, HazardSeverity severity) {
        super(positionMeters, lane);
        this.severity = severity;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return super.toString() + " severity=" + severity;
    }
}
