package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.drools.events.VehicleSpeedEvent;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.List;

public class SpeedSensor extends AbstractSensor<Object> {
    public SpeedSensor(SimulationConfig config) {
        super("speedometer", config.getSpeedSensorRateMillis(), 0);
    }

    @Override
    public List<Object> observe(WorldState worldState, long timestampMillis) {
        return List.of(new VehicleSpeedEvent(timestampMillis, id(), worldState.getEgo().getSpeedKmh()));
    }
}
