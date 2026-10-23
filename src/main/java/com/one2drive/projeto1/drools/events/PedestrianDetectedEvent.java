package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.PedestrianCrossingState;

public class PedestrianDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String pedestrianId;
    private final boolean inPath;
    private final PedestrianCrossingState crossingState;

    public PedestrianDetectedEvent(long timestamp, String sourceId, double distanceMeters, String pedestrianId, boolean inPath) {
        this(timestamp, sourceId, distanceMeters, pedestrianId, inPath, PedestrianCrossingState.WAITING);
    }

    public PedestrianDetectedEvent(long timestamp, String sourceId, double distanceMeters, String pedestrianId,
                                   boolean inPath, PedestrianCrossingState crossingState) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.pedestrianId = pedestrianId;
        this.inPath = inPath;
        this.crossingState = crossingState;
    }

    /**
     * WAITING: on the road but not crossing. CROSSING: walking across the road and not yet
     * at the far side, in whatever lane it is now. CROSSED: it has left the road.
     */
    public PedestrianCrossingState getCrossingState() {
        return crossingState;
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
        return "PedestrianDetectedEvent{distanceMeters=" + distanceMeters + ", inPath=" + inPath
            + ", crossingState=" + crossingState + "}";
    }
}
