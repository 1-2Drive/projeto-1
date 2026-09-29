package com.one2drive.projeto1.drools.events;

public class AccelerationRequestEvent extends BaseEvent {
    private final double targetSpeedKmh;

    public AccelerationRequestEvent(long timestamp, String sourceId, double targetSpeedKmh) {
        super(timestamp, sourceId);
        this.targetSpeedKmh = targetSpeedKmh;
    }

    public double getTargetSpeedKmh() {
        return targetSpeedKmh;
    }

    @Override
    public String toString() {
        return "AccelerationRequestEvent{targetSpeedKmh=" + targetSpeedKmh + "}";
    }
}
