package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.Lane;

public class LaneOccupancyEvent extends BaseEvent {
    private final Lane lane;
    private final boolean occupied;
    private final double nearestDistanceMeters;
    private final boolean safeToEnter;
    private final double frontDistanceMeters;
    private final double rearDistanceMeters;
    private final double rearTtcSeconds;

    public LaneOccupancyEvent(long timestamp,
                              String sourceId,
                              Lane lane,
                              boolean occupied,
                              double nearestDistanceMeters) {
        this(timestamp, sourceId, lane, occupied, nearestDistanceMeters, !occupied,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public LaneOccupancyEvent(long timestamp,
                              String sourceId,
                              Lane lane,
                              boolean occupied,
                              double nearestDistanceMeters,
                              boolean safeToEnter,
                              double frontDistanceMeters,
                              double rearDistanceMeters,
                              double rearTtcSeconds) {
        super(timestamp, sourceId);
        this.lane = lane;
        this.occupied = occupied;
        this.nearestDistanceMeters = nearestDistanceMeters;
        this.safeToEnter = safeToEnter;
        this.frontDistanceMeters = frontDistanceMeters;
        this.rearDistanceMeters = rearDistanceMeters;
        this.rearTtcSeconds = rearTtcSeconds;
    }

    public Lane getLane() {
        return lane;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public double getNearestDistanceMeters() {
        return nearestDistanceMeters;
    }

    public boolean isSafeToEnter() {
        return safeToEnter;
    }

    public double getFrontDistanceMeters() {
        return frontDistanceMeters;
    }

    public double getRearDistanceMeters() {
        return rearDistanceMeters;
    }

    public double getRearTtcSeconds() {
        return rearTtcSeconds;
    }

    @Override
    public String toString() {
        return "LaneOccupancyEvent{lane=" + lane + ", occupied=" + occupied +
            ", nearestDistanceMeters=" + nearestDistanceMeters + ", safeToEnter=" + safeToEnter + "}";
    }
}
