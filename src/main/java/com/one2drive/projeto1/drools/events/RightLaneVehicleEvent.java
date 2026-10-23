package com.one2drive.projeto1.drools.events;

/**
 * Radar sample of the nearest vehicle ahead in the lane immediately to the right of the
 * ego lane, the counterpart of {@link LeftLaneVehicleEvent}. Before moving into that lane
 * the rules must know whether the ego can still follow the vehicle there.
 */
public class RightLaneVehicleEvent extends BaseEvent {
    private final double distanceMeters;
    private final double relativeSpeedMps;
    private final String targetId;

    public RightLaneVehicleEvent(long timestamp, String sourceId, double distanceMeters, double relativeSpeedMps, String targetId) {
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
        return "RightLaneVehicleEvent{distanceMeters=" + distanceMeters + ", relativeSpeedMps=" + relativeSpeedMps
            + ", targetId='" + targetId + "'}";
    }
}
