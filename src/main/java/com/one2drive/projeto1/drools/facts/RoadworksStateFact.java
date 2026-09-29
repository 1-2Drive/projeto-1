package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.HazardSeverity;

public class RoadworksStateFact {
    private String roadworksId;
    private double distanceMeters;
    private HazardSeverity severity;
    private long timestamp;

    public RoadworksStateFact(String roadworksId, double distanceMeters, HazardSeverity severity, long timestamp) {
        this.roadworksId = roadworksId;
        this.distanceMeters = distanceMeters;
        this.severity = severity;
        this.timestamp = timestamp;
    }

    public String getRoadworksId() {
        return roadworksId;
    }

    public void setRoadworksId(String roadworksId) {
        this.roadworksId = roadworksId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(HazardSeverity severity) {
        this.severity = severity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
