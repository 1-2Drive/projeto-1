package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.drools.events.RoadGripEvent;
import com.one2drive.projeto1.drools.events.ReducedGripEvent;
import com.one2drive.projeto1.drools.events.ReducedVisibilityEvent;
import com.one2drive.projeto1.drools.events.VisibilityEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.List;

public class EnvironmentSensor extends AbstractSensor<Object> {
    public EnvironmentSensor(SimulationConfig config) {
        super("environment", config.getEnvironmentRateMillis(), config.getEnvironmentRangeMeters());
    }

    @Override
    public List<Object> observe(WorldState worldState, long timestampMillis) {
        List<Object> events = new ArrayList<>();
        var weather = worldState.getWeatherState();
        events.add(new WeatherEvent(
            timestampMillis,
            id(),
            weather.getCondition(),
            weather.getVisibilityFactor(),
            weather.getGripFactor()
        ));
        events.add(new VisibilityEvent(timestampMillis, id(), weather.getVisibilityFactor()));
        events.add(new RoadGripEvent(timestampMillis, id(), weather.getGripFactor()));
        if (weather.getVisibilityFactor() < 0.85) {
            events.add(new ReducedVisibilityEvent(timestampMillis, id(), weather.getVisibilityFactor()));
        }
        if (weather.getGripFactor() < 0.85) {
            events.add(new ReducedGripEvent(timestampMillis, id(), weather.getGripFactor()));
        }
        return events;
    }
}
