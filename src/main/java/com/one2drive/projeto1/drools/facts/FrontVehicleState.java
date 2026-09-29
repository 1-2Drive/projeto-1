package com.one2drive.projeto1.drools.facts;

public class FrontVehicleState {
    private String targetId;
    private double distanceMeters;
    private double relativeSpeedMps;
    private double ttcSeconds;
    private long timestamp;

    public FrontVehicleState(String targetId,
                             double distanceMeters,
                             double relativeSpeedMps,
                             double ttcSeconds,
                             long timestamp) {
        this.targetId = targetId;
        this.distanceMeters = distanceMeters;
        this.relativeSpeedMps = relativeSpeedMps;
        this.ttcSeconds = ttcSeconds;
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

    public double getRelativeSpeedMps() {
        return relativeSpeedMps;
    }

    public void setRelativeSpeedMps(double relativeSpeedMps) {
        this.relativeSpeedMps = relativeSpeedMps;
    }

    public double getTtcSeconds() {
        return ttcSeconds;
    }

    public void setTtcSeconds(double ttcSeconds) {
        this.ttcSeconds = ttcSeconds;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "FrontVehicleState{targetId='" + targetId + "', distanceMeters=" + distanceMeters +
            ", relativeSpeedMps=" + relativeSpeedMps +
            ", ttcSeconds=" + ttcSeconds +
            "}";
    }
}
