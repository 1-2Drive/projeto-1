package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.Lane;

public class LaneOccupancyEvent extends BaseEvent {
    private final Lane lane;
    private final boolean occupied;
    private final double nearestDistanceMeters;

    public LaneOccupancyEvent(long timestamp,
                              String sourceId,
                              Lane lane,
                              boolean occupied,
                              double nearestDistanceMeters) {
        super(timestamp, sourceId);
        this.lane = lane;
        this.occupied = occupied;
        this.nearestDistanceMeters = nearestDistanceMeters;
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

    @Override
    public String toString() {
        return "LaneOccupancyEvent{lane=" + lane + ", occupied=" + occupied +
            ", nearestDistanceMeters=" + nearestDistanceMeters + "}";
    }
}
