package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.HazardSeverity;

public class PotholeDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String potholeId;
    private final HazardSeverity severity;

    public PotholeDetectedEvent(long timestamp, String sourceId, double distanceMeters, String potholeId, HazardSeverity severity) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.potholeId = potholeId;
        this.severity = severity;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getPotholeId() {
        return potholeId;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return "PotholeDetectedEvent{distanceMeters=" + distanceMeters + ", severity=" + severity + "}";
    }
}
