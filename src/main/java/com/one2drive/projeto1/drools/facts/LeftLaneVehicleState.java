package com.one2drive.projeto1.drools.facts;

/**
 * Nearest vehicle ahead in the lane immediately to the left of the ego lane, as last
 * reported by the front radar. "none" when there is no such vehicle.
 */
public class LeftLaneVehicleState {
    public static final String NO_TARGET = "none";

    private String targetId;
    private double distanceMeters;
    private double relativeSpeedMps;
    private long timestamp;

    public LeftLaneVehicleState(String targetId, double distanceMeters, double relativeSpeedMps, long timestamp) {
        this.targetId = targetId;
        this.distanceMeters = distanceMeters;
        this.relativeSpeedMps = relativeSpeedMps;
        this.timestamp = timestamp;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    /** Ego speed minus the other vehicle's speed: positive while the ego is closing in. */
    public double getRelativeSpeedMps() {
        return relativeSpeedMps;
    }

    public void setRelativeSpeedMps(double relativeSpeedMps) {
        this.relativeSpeedMps = relativeSpeedMps;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "LeftLaneVehicleState{targetId='" + targetId + "', distanceMeters=" + distanceMeters
            + ", relativeSpeedMps=" + relativeSpeedMps + "}";
    }
}
