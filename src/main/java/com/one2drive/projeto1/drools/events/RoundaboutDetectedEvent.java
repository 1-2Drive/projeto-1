package com.one2drive.projeto1.drools.events;

public class RoundaboutDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String roundaboutId;
    private final double approachSpeedKmh;
    private final boolean occupied;

    public RoundaboutDetectedEvent(long timestamp,
                                   String sourceId,
                                   double distanceMeters,
                                   String roundaboutId,
                                   double approachSpeedKmh,
                                   boolean occupied) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.roundaboutId = roundaboutId;
        this.approachSpeedKmh = approachSpeedKmh;
        this.occupied = occupied;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getRoundaboutId() {
        return roundaboutId;
    }

    public double getApproachSpeedKmh() {
        return approachSpeedKmh;
    }

    public boolean isOccupied() {
        return occupied;
    }

    @Override
    public String toString() {
        return "RoundaboutDetectedEvent{distanceMeters=" + distanceMeters +
            ", approachSpeedKmh=" + approachSpeedKmh +
            ", occupied=" + occupied +
            "}";
    }
}
