package com.one2drive.projeto1.drools.events;

public class YieldSignEvent extends BaseEvent {
    private final double distanceMeters;
    private final String signId;

    public YieldSignEvent(long timestamp, String sourceId, double distanceMeters, String signId) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.signId = signId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getSignId() {
        return signId;
    }

    @Override
    public String toString() {
        return "YieldSignEvent{distanceMeters=" + distanceMeters + ", signId='" + signId + "'}";
    }
}
