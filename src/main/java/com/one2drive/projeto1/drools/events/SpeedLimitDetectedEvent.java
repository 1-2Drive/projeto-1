package com.one2drive.projeto1.drools.events;

public class SpeedLimitDetectedEvent extends BaseEvent {
    private final int limitKmh;
    private final double distanceMeters;
    private final String signId;

    public SpeedLimitDetectedEvent(long timestamp, String sourceId, int limitKmh, double distanceMeters, String signId) {
        super(timestamp, sourceId);
        this.limitKmh = limitKmh;
        this.distanceMeters = distanceMeters;
        this.signId = signId;
    }

    public int getLimitKmh() {
        return limitKmh;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getSignId() {
        return signId;
    }

    @Override
    public String toString() {
        return "SpeedLimitDetectedEvent{limitKmh=" + limitKmh + ", distanceMeters=" + distanceMeters + "}";
    }
}
