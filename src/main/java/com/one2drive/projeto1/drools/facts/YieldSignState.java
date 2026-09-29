package com.one2drive.projeto1.drools.facts;

public class YieldSignState {
    private String signId;
    private double distanceMeters;
    private long timestamp;

    public YieldSignState(String signId, double distanceMeters, long timestamp) {
        this.signId = signId;
        this.distanceMeters = distanceMeters;
        this.timestamp = timestamp;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "YieldSignState{signId='" + signId + "', distanceMeters=" + distanceMeters + "}";
    }
}
