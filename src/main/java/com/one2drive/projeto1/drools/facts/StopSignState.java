package com.one2drive.projeto1.drools.facts;

public class StopSignState {
    private String signId;
    private double distanceMeters;
    private long detectedTimestamp;
    private long stopStartTimestamp;
    private boolean satisfied;

    public StopSignState(String signId, double distanceMeters, long detectedTimestamp) {
        this.signId = signId;
        this.distanceMeters = distanceMeters;
        this.detectedTimestamp = detectedTimestamp;
        this.stopStartTimestamp = -1L;
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

    public long getStopStartTimestamp() {
        return stopStartTimestamp;
    }

    public void setStopStartTimestamp(long stopStartTimestamp) {
        this.stopStartTimestamp = stopStartTimestamp;
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public void setSatisfied(boolean satisfied) {
        this.satisfied = satisfied;
    }
}
