package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.WeatherCondition;

public class WeatherEvent extends BaseEvent {
    private final WeatherCondition condition;
    private final double visibilityFactor;
    private final double gripFactor;

    public WeatherEvent(long timestamp,
                        String sourceId,
                        WeatherCondition condition,
                        double visibilityFactor,
                        double gripFactor) {
        super(timestamp, sourceId);
        this.condition = condition;
        this.visibilityFactor = visibilityFactor;
        this.gripFactor = gripFactor;
    }

    public WeatherCondition getCondition() {
        return condition;
    }

    public double getVisibilityFactor() {
        return visibilityFactor;
    }

    public double getGripFactor() {
        return gripFactor;
    }

    @Override
    public String toString() {
        return "WeatherEvent{condition=" + condition +
            ", visibilityFactor=" + visibilityFactor +
            ", gripFactor=" + gripFactor + "}";
    }
}
