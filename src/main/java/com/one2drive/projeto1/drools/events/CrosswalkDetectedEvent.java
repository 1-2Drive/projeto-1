package com.one2drive.projeto1.drools.events;

/** The camera sees a pedestrian crossing ahead. It spans every lane, so it carries none. */
public class CrosswalkDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String crosswalkId;

    public CrosswalkDetectedEvent(long timestamp, String sourceId, double distanceMeters, String crosswalkId) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.crosswalkId = crosswalkId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getCrosswalkId() {
        return crosswalkId;
    }

    @Override
    public String toString() {
        return "CrosswalkDetectedEvent{distanceMeters=" + distanceMeters + "}";
    }
}
