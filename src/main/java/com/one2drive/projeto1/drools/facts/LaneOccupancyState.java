package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

public class LaneOccupancyState {
    private Lane lane;
    private boolean occupied;
    private double nearestDistanceMeters;
    private boolean safeToEnter;
    private double frontDistanceMeters;
    private double rearDistanceMeters;
    private double rearTtcSeconds;
    private long timestamp;

    public LaneOccupancyState(Lane lane, boolean occupied, double nearestDistanceMeters, long timestamp) {
        this(lane, occupied, nearestDistanceMeters, !occupied,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, timestamp);
    }

    public LaneOccupancyState(Lane lane,
                              boolean occupied,
                              double nearestDistanceMeters,
                              boolean safeToEnter,
                              double frontDistanceMeters,
                              double rearDistanceMeters,
                              double rearTtcSeconds,
                              long timestamp) {
        this.lane = lane;
        this.occupied = occupied;
        this.nearestDistanceMeters = nearestDistanceMeters;
        this.safeToEnter = safeToEnter;
        this.frontDistanceMeters = frontDistanceMeters;
        this.rearDistanceMeters = rearDistanceMeters;
        this.rearTtcSeconds = rearTtcSeconds;
        this.timestamp = timestamp;
    }

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public double getNearestDistanceMeters() {
        return nearestDistanceMeters;
    }

    public void setNearestDistanceMeters(double nearestDistanceMeters) {
        this.nearestDistanceMeters = nearestDistanceMeters;
    }

    public boolean isSafeToEnter() {
        return safeToEnter;
    }

    public void setSafeToEnter(boolean safeToEnter) {
        this.safeToEnter = safeToEnter;
    }

    public double getFrontDistanceMeters() {
        return frontDistanceMeters;
    }

    public void setFrontDistanceMeters(double frontDistanceMeters) {
        this.frontDistanceMeters = frontDistanceMeters;
    }

    public double getRearDistanceMeters() {
        return rearDistanceMeters;
    }

    public void setRearDistanceMeters(double rearDistanceMeters) {
        this.rearDistanceMeters = rearDistanceMeters;
    }

    public double getRearTtcSeconds() {
        return rearTtcSeconds;
    }

    public void setRearTtcSeconds(double rearTtcSeconds) {
        this.rearTtcSeconds = rearTtcSeconds;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "LaneOccupancyState{lane=" + lane +
            ", occupied=" + occupied +
            ", nearestDistanceMeters=" + nearestDistanceMeters +
            ", safeToEnter=" + safeToEnter + "}";
    }
}
