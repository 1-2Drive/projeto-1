package com.one2drive.projeto1.drools.events;

public class VehicleSpeedEvent extends BaseEvent {
    private final double speedKmh;

    public VehicleSpeedEvent(long timestamp, String sourceId, double speedKmh) {
        super(timestamp, sourceId);
        this.speedKmh = speedKmh;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    @Override
    public String toString() {
        return "VehicleSpeedEvent{speedKmh=" + speedKmh + "}";
    }
}
