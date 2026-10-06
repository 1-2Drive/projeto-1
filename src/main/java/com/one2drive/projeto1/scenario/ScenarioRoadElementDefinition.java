package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.TrafficLightState;

public class ScenarioRoadElementDefinition {
    private ScenarioRoadElementType type;
    private double positionMeters;
    private Lane lane;
    private Integer speedLimitKmh;
    private TrafficLightState trafficLightState;
    private HazardSeverity severity;
    private Boolean barrierOpen;

    public ScenarioRoadElementType getType() {
        return type;
    }

    public void setType(ScenarioRoadElementType type) {
        this.type = type;
    }

    public double getPositionMeters() {
        return positionMeters;
    }

    public void setPositionMeters(double positionMeters) {
        this.positionMeters = positionMeters;
    }

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public Integer getSpeedLimitKmh() {
        return speedLimitKmh;
    }

    public void setSpeedLimitKmh(Integer speedLimitKmh) {
        this.speedLimitKmh = speedLimitKmh;
    }

    public TrafficLightState getTrafficLightState() {
        return trafficLightState;
    }

    public void setTrafficLightState(TrafficLightState trafficLightState) {
        this.trafficLightState = trafficLightState;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(HazardSeverity severity) {
        this.severity = severity;
    }

    public Boolean getBarrierOpen() {
        return barrierOpen;
    }

    public void setBarrierOpen(Boolean barrierOpen) {
        this.barrierOpen = barrierOpen;
    }

    public ScenarioRoadElementDefinition copy() {
        ScenarioRoadElementDefinition copy = new ScenarioRoadElementDefinition();
        copy.setType(type);
        copy.setPositionMeters(positionMeters);
        copy.setLane(lane);
        copy.setSpeedLimitKmh(speedLimitKmh);
        copy.setTrafficLightState(trafficLightState);
        copy.setSeverity(severity);
        copy.setBarrierOpen(barrierOpen);
        return copy;
    }
}
