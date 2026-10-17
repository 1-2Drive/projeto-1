package com.one2drive.projeto1.drools.facts;

public class StopSignState {
    private String signId;
    private double distanceMeters;
    private long detectedTimestamp;
    private boolean satisfied;

    public StopSignState(String signId, double distanceMeters, long detectedTimestamp) {
        this.signId = signId;
        this.distanceMeters = distanceMeters;
        this.detectedTimestamp = detectedTimestamp;
        this.satisfied = false;
    }

    public String getSignId() {
        return signId;
    }

    public void setSignId(String signId) {
        this.signId = signId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public long getDetectedTimestamp() {
        return detectedTimestamp;
    }

    public void setDetectedTimestamp(long detectedTimestamp) {
        this.detectedTimestamp = detectedTimestamp;
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public void setSatisfied(boolean satisfied) {
        this.satisfied = satisfied;
    }

    @Override
    public String toString() {
        return "StopSignState{signId='" + signId + "', distanceMeters=" + distanceMeters
            + ", satisfied=" + satisfied + "}";
    }
}
