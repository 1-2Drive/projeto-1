package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.PedestrianCrossingState;

public class PedestrianStateFact {
    private String pedestrianId;
    private double distanceMeters;
    private boolean inPath;
    private PedestrianCrossingState crossingState = PedestrianCrossingState.WAITING;
    private long timestamp;

    public PedestrianStateFact(String pedestrianId, double distanceMeters, boolean inPath, long timestamp) {
        this.pedestrianId = pedestrianId;
        this.distanceMeters = distanceMeters;
        this.inPath = inPath;
        this.timestamp = timestamp;
    }

    public String getPedestrianId() {
        return pedestrianId;
    }

    public void setPedestrianId(String pedestrianId) {
        this.pedestrianId = pedestrianId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public boolean isInPath() {
        return inPath;
    }

    public void setInPath(boolean inPath) {
        this.inPath = inPath;
    }

    /**
     * WAITING: no pedestrian, or one that is not crossing. CROSSING: the pedestrian is still
     * crossing the road ahead, whether or not it is in the ego's lane. CROSSED: it has
     * finished crossing and no longer holds the vehicle.
     */
    public PedestrianCrossingState getCrossingState() {
        return crossingState;
    }

    public void setCrossingState(PedestrianCrossingState crossingState) {
        this.crossingState = crossingState;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "PedestrianStateFact{pedestrianId='" + pedestrianId + "', distanceMeters=" + distanceMeters
            + ", inPath=" + inPath + ", crossingState=" + crossingState + "}";
    }
}
