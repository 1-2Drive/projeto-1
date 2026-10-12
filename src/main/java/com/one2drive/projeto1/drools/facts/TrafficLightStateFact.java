package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.TrafficLightState;

public class TrafficLightStateFact {
    private String lightId;
    private TrafficLightState state;
    private double distanceMeters;
    private long timestamp;
    private TrafficLightCommitment commitment = TrafficLightCommitment.NONE;

    public TrafficLightStateFact(String lightId, TrafficLightState state, double distanceMeters, long timestamp) {
        this.lightId = lightId;
        this.state = state;
        this.distanceMeters = distanceMeters;
        this.timestamp = timestamp;
    }

    public String getLightId() {
        return lightId;
    }

    public void setLightId(String lightId) {
        this.lightId = lightId;
    }

    public TrafficLightState getState() {
        return state;
    }

    public void setState(TrafficLightState state) {
        this.state = state;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    /** What the rules decided to do about this light: stop before the line, or drive on. */
    public TrafficLightCommitment getCommitment() {
        return commitment;
    }

    public void setCommitment(TrafficLightCommitment commitment) {
        this.commitment = commitment;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "TrafficLightStateFact{lightId='" + lightId + "', state=" + state +
            ", distanceMeters=" + distanceMeters + ", commitment=" + commitment + "}";
    }
}
