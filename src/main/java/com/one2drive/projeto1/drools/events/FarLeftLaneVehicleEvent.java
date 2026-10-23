package com.one2drive.projeto1.drools.events;

/**
 * Radar sample of the nearest vehicle ahead two lanes to the left of the ego lane (LEFT,
 * seen from RIGHT). Before moving in behind a slower vehicle in the lane to its left to
 * overtake it on the left, the ego must make sure the lane it would overtake through is
 * clear far enough ahead (Art. 38 CE).
 */
public class FarLeftLaneVehicleEvent extends BaseEvent {
    private final double distanceMeters;
    private final double relativeSpeedMps;
    private final String targetId;

    public FarLeftLaneVehicleEvent(long timestamp, String sourceId, double distanceMeters, double relativeSpeedMps, String targetId) {
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
        return "FarLeftLaneVehicleEvent{distanceMeters=" + distanceMeters + ", relativeSpeedMps=" + relativeSpeedMps
            + ", targetId='" + targetId + "'}";
    }
}
