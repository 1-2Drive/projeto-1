package com.one2drive.projeto1.drools.events;

public class BarrierDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String barrierId;
    private final boolean open;

    public BarrierDetectedEvent(long timestamp, String sourceId, double distanceMeters, String barrierId, boolean open) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.barrierId = barrierId;
        this.open = open;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getBarrierId() {
        return barrierId;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public String toString() {
        return "BarrierDetectedEvent{distanceMeters=" + distanceMeters + ", open=" + open + "}";
    }
}
