package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.HazardSeverity;

public class PotholeStateFact {
    private String potholeId;
    private double distanceMeters;
    private HazardSeverity severity;
    private long timestamp;

    public PotholeStateFact(String potholeId, double distanceMeters, HazardSeverity severity, long timestamp) {
        this.potholeId = potholeId;
        this.distanceMeters = distanceMeters;
        this.severity = severity;
        this.timestamp = timestamp;
    }

    public String getPotholeId() {
        return potholeId;
    }

    public void setPotholeId(String potholeId) {
        this.potholeId = potholeId;
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
