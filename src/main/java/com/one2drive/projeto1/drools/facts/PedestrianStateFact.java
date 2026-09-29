package com.one2drive.projeto1.drools.facts;

public class PedestrianStateFact {
    private String pedestrianId;
    private double distanceMeters;
    private boolean inPath;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
