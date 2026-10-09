package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

public class BarrierStateFact {
    private String barrierId;
    private double distanceMeters;
    private boolean open;
    private Lane lane;
    private long timestamp;

    public BarrierStateFact(String barrierId, double distanceMeters, boolean open, long timestamp) {
        this(barrierId, distanceMeters, open, Lane.CENTER, timestamp);
    }

    public BarrierStateFact(String barrierId, double distanceMeters, boolean open, Lane lane, long timestamp) {
        this.barrierId = barrierId;
        this.distanceMeters = distanceMeters;
        this.open = open;
        this.lane = lane;
        this.timestamp = timestamp;
    }

    public String getBarrierId() {
        return barrierId;
    }

    public void setBarrierId(String barrierId) {
        this.barrierId = barrierId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "BarrierStateFact{barrierId='" + barrierId + "', distanceMeters=" + distanceMeters
            + ", lane=" + lane + ", open=" + open + "}";
    }
}
