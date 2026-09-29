package com.one2drive.projeto1.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ApiCreateActorRequest {
    @NotBlank
    private String type;

    @NotBlank
    private String lane;

    @NotNull
    @Min(1)
    @Max(1000)
    private Double distanceAheadMeters;

    @NotNull
    @Min(0)
    @Max(180)
    private Double speedKmh;

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

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(Double speedKmh) {
        this.speedKmh = speedKmh;
    }
}
