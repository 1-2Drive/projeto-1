package com.one2drive.projeto1.domain;

public class WeatherState {
    private WeatherCondition condition;
    private double visibilityFactor;
    private double gripFactor;

    public WeatherState(WeatherCondition condition, double visibilityFactor, double gripFactor) {
        this.condition = condition;
        this.visibilityFactor = visibilityFactor;
        this.gripFactor = gripFactor;
    }

    public WeatherCondition getCondition() {
        return condition;
    }

    public void setCondition(WeatherCondition condition) {
        this.condition = condition;
    }

    public double getVisibilityFactor() {
        return visibilityFactor;
    }

    public void setVisibilityFactor(double visibilityFactor) {
        this.visibilityFactor = visibilityFactor;
    }

    public double getGripFactor() {
        return gripFactor;
    }

    public void setGripFactor(double gripFactor) {
        this.gripFactor = gripFactor;
    }
}
