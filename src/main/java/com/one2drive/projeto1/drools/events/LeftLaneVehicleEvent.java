package com.one2drive.projeto1.drools.events;

/**
 * Radar sample of the nearest vehicle ahead in the lane immediately to the left of the
 * ego lane. The rules need it to avoid passing that vehicle on its right: the front
 * radar samples only describe the vehicle in the ego's own lane.
 */
public class LeftLaneVehicleEvent extends BaseEvent {
    private final double distanceMeters;
    private final double relativeSpeedMps;
    private final String targetId;

    public LeftLaneVehicleEvent(long timestamp, String sourceId, double distanceMeters, double relativeSpeedMps, String targetId) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.relativeSpeedMps = relativeSpeedMps;
        this.targetId = targetId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    /** Ego speed minus the other vehicle's speed: positive while the ego is closing in. */
    public double getRelativeSpeedMps() {
        return relativeSpeedMps;
    }

    public String getTargetId() {
        return targetId;
    }

    @Override
    public String toString() {
        return "LeftLaneVehicleEvent{distanceMeters=" + distanceMeters + ", relativeSpeedMps=" + relativeSpeedMps
            + ", targetId='" + targetId + "'}";
    }
}
