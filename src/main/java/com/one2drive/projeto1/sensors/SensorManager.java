package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SensorManager {
    private final List<Sensor<Object>> sensors;
    private final Map<String, Long> lastUpdateMillis;

    public SensorManager(List<Sensor<Object>> sensors) {
        this.sensors = sensors;
        this.lastUpdateMillis = new HashMap<>();
    }

    public List<Object> observe(WorldState worldState, long timestampMillis) {
        List<Object> allEvents = new ArrayList<>();
        for (Sensor<Object> sensor : sensors) {
            long last = lastUpdateMillis.getOrDefault(sensor.id(), Long.MIN_VALUE / 4);
            if (timestampMillis - last >= sensor.updateRateMillis()) {
                allEvents.addAll(sensor.observe(worldState, timestampMillis));
                lastUpdateMillis.put(sensor.id(), timestampMillis);
            }
        }
        return allEvents;
    }

    public List<Sensor<Object>> getSensors() {
        return sensors;
    }
}
