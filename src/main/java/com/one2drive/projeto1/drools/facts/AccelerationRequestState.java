package com.one2drive.projeto1.drools.facts;

public class AccelerationRequestState {
    private double targetSpeedKmh;
    private long timestamp;

    public AccelerationRequestState(double targetSpeedKmh, long timestamp) {
        this.targetSpeedKmh = targetSpeedKmh;
        this.timestamp = timestamp;
    }

    public double getTargetSpeedKmh() {
        return targetSpeedKmh;
    }

    public void setTargetSpeedKmh(double targetSpeedKmh) {
        this.targetSpeedKmh = targetSpeedKmh;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AccelerationRequestState{targetSpeedKmh=" + targetSpeedKmh + "}";
    }
}
