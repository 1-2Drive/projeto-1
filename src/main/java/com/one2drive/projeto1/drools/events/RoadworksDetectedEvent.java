package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.HazardSeverity;

public class RoadworksDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String roadworksId;
    private final HazardSeverity severity;

    public RoadworksDetectedEvent(long timestamp, String sourceId, double distanceMeters, String roadworksId, HazardSeverity severity) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.roadworksId = roadworksId;
        this.severity = severity;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getRoadworksId() {
        return roadworksId;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return "RoadworksDetectedEvent{distanceMeters=" + distanceMeters + ", severity=" + severity + "}";
    }
}
