package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

public class LaneOccupancyState {
    private Lane lane;
    private boolean occupied;
    private double nearestDistanceMeters;
    private long timestamp;

    public LaneOccupancyState(Lane lane, boolean occupied, double nearestDistanceMeters, long timestamp) {
        this.lane = lane;
        this.occupied = occupied;
        this.nearestDistanceMeters = nearestDistanceMeters;
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
            ", nearestDistanceMeters=" + nearestDistanceMeters + "}";
    }
}
