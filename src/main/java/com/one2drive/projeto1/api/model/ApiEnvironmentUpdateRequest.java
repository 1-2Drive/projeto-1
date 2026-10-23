package com.one2drive.projeto1.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public class ApiEnvironmentUpdateRequest {
    @DecimalMin("0.2")
    @DecimalMax("1.0")
    private Double visibilityFactor;

    @DecimalMin("0.2")
    @DecimalMax("1.0")
    private Double gripFactor;

    private Boolean rain;

    public Double getVisibilityFactor() {
        return visibilityFactor;
    }

    public void setVisibilityFactor(Double visibilityFactor) {
        this.visibilityFactor = visibilityFactor;
    }

    public Double getGripFactor() {
        return gripFactor;
    }

    public void setGripFactor(Double gripFactor) {
        this.gripFactor = gripFactor;
    }

    public Boolean getRain() {
        return rain;
    }

    public void setRain(Boolean rain) {
        this.rain = rain;
    }
}
