package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.Lane;

public class BarrierDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String barrierId;
    private final boolean open;
    private final Lane lane;

    public BarrierDetectedEvent(long timestamp, String sourceId, double distanceMeters, String barrierId, boolean open) {
        this(timestamp, sourceId, distanceMeters, barrierId, open, Lane.CENTER);
    }

    public BarrierDetectedEvent(long timestamp, String sourceId, double distanceMeters,
                                String barrierId, boolean open, Lane lane) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.barrierId = barrierId;
        this.open = open;
        this.lane = lane;
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

    public Lane getLane() {
        return lane;
    }

    @Override
    public String toString() {
        return "BarrierDetectedEvent{distanceMeters=" + distanceMeters + ", lane=" + lane + ", open=" + open + "}";
    }
}
