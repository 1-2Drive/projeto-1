package com.one2drive.projeto1.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ApiCreateRoadElementRequest {
    @NotBlank
    private String type;

    @NotBlank
    private String lane;

    @NotNull
    @Min(1)
    @Max(1000)
    private Double distanceAheadMeters;

    private Integer speedLimitKmh;
    private String trafficLightState;
    private String severity;
    private Boolean barrierOpen;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLane() {
        return lane;
    }

    public void setLane(String lane) {
        this.lane = lane;
    }

    public Double getDistanceAheadMeters() {
        return distanceAheadMeters;
    }

    public void setDistanceAheadMeters(Double distanceAheadMeters) {
        this.distanceAheadMeters = distanceAheadMeters;
    }

    public Integer getSpeedLimitKmh() {
        return speedLimitKmh;
    }

    public void setSpeedLimitKmh(Integer speedLimitKmh) {
        this.speedLimitKmh = speedLimitKmh;
    }

    public String getTrafficLightState() {
        return trafficLightState;
    }

    public void setTrafficLightState(String trafficLightState) {
        this.trafficLightState = trafficLightState;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Boolean getBarrierOpen() {
        return barrierOpen;
    }

    public void setBarrierOpen(Boolean barrierOpen) {
        this.barrierOpen = barrierOpen;
    }
}
