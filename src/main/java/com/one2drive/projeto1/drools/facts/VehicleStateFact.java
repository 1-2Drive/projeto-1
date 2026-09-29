package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

public class VehicleStateFact {
    private double speedKmh;
    private double positionMeters;
    private Lane lane;
    private long timestamp;

    public VehicleStateFact(double speedKmh, double positionMeters, Lane lane, long timestamp) {
        this.speedKmh = speedKmh;
        this.positionMeters = positionMeters;
        this.lane = lane;
        this.timestamp = timestamp;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public double getPositionMeters() {
        return positionMeters;
    }

    public void setPositionMeters(double positionMeters) {
        this.positionMeters = positionMeters;
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
        return "VehicleStateFact{speedKmh=" + speedKmh +
            ", positionMeters=" + positionMeters +
            ", lane=" + lane + "}";
    }
}
