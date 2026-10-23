package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.TrafficLightState;

public class TrafficLightEvent extends BaseEvent {
    private final TrafficLightState state;
    private final double distanceMeters;
    private final String lightId;

    public TrafficLightEvent(long timestamp, String sourceId, TrafficLightState state, double distanceMeters, String lightId) {
        super(timestamp, sourceId);
        this.state = state;
        this.distanceMeters = distanceMeters;
        this.lightId = lightId;
    }

    public TrafficLightState getState() {
        return state;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getLightId() {
        return lightId;
    }

    @Override
    public String toString() {
        return "TrafficLightEvent{state=" + state + ", distanceMeters=" + distanceMeters + "}";
    }
}
