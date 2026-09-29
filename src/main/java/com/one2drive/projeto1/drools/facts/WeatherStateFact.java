package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.WeatherCondition;

public class WeatherStateFact {
    private WeatherCondition condition;
    private double visibilityFactor;
    private double gripFactor;
    private long timestamp;

    public WeatherStateFact(WeatherCondition condition, double visibilityFactor, double gripFactor, long timestamp) {
        this.condition = condition;
        this.visibilityFactor = visibilityFactor;
        this.gripFactor = gripFactor;
        this.timestamp = timestamp;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "WeatherStateFact{condition=" + condition +
            ", visibilityFactor=" + visibilityFactor +
            ", gripFactor=" + gripFactor + "}";
    }
}
