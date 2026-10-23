package com.one2drive.projeto1.drools.events;

public class FrontDistanceEvent extends BaseEvent {
    private final double distanceMeters;
    private final String targetId;

    public FrontDistanceEvent(long timestamp, String sourceId, double distanceMeters, String targetId) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.targetId = targetId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getTargetId() {
        return targetId;
    }

    @Override
    public String toString() {
        return "FrontDistanceEvent{distanceMeters=" + distanceMeters + ", targetId='" + targetId + "'}";
    }
}
