package com.one2drive.projeto1.drools.facts;

public class RoundaboutStateFact {
    private String roundaboutId;
    private double distanceMeters;
    private double approachSpeedKmh;
    private boolean occupied;
    private long timestamp;

    public RoundaboutStateFact(String roundaboutId,
                               double distanceMeters,
                               double approachSpeedKmh,
                               boolean occupied,
                               long timestamp) {
        this.roundaboutId = roundaboutId;
        this.distanceMeters = distanceMeters;
        this.approachSpeedKmh = approachSpeedKmh;
        this.occupied = occupied;
        this.timestamp = timestamp;
    }

    public String getRoundaboutId() {
        return roundaboutId;
    }

    public void setRoundaboutId(String roundaboutId) {
        this.roundaboutId = roundaboutId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public double getApproachSpeedKmh() {
        return approachSpeedKmh;
    }

    public void setApproachSpeedKmh(double approachSpeedKmh) {
        this.approachSpeedKmh = approachSpeedKmh;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "RoundaboutStateFact{roundaboutId='" + roundaboutId + "', distanceMeters=" + distanceMeters
            + ", approachSpeedKmh=" + approachSpeedKmh
            + ", occupied=" + occupied + "}";
    }
}
