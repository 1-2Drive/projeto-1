package com.one2drive.projeto1.drools.events;

public class PedestrianDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String pedestrianId;
    private final boolean inPath;

    public PedestrianDetectedEvent(long timestamp, String sourceId, double distanceMeters, String pedestrianId, boolean inPath) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.pedestrianId = pedestrianId;
        this.inPath = inPath;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getPedestrianId() {
        return pedestrianId;
    }

    public boolean isInPath() {
        return inPath;
    }

    @Override
    public String toString() {
        return "PedestrianDetectedEvent{distanceMeters=" + distanceMeters + ", inPath=" + inPath + "}";
    }
}
