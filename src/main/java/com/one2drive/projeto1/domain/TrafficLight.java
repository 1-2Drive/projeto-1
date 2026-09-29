package com.one2drive.projeto1.domain;

public class TrafficLight extends TrafficControl {
    private TrafficLightState state;

    public TrafficLight(double positionMeters, Lane lane, TrafficLightState state) {
        super(positionMeters, lane);
        this.state = state;
    }

    public TrafficLightState getState() {
        return state;
    }

    public void setState(TrafficLightState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return super.toString() + " state=" + state;
    }
}
