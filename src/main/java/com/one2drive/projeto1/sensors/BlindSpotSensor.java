package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.List;

public class BlindSpotSensor extends AbstractSensor<Object> {
    public BlindSpotSensor(SimulationConfig config) {
        super("blind-spot", config.getBlindSpotRateMillis(), config.getBlindSpotRangeMeters());
    }

    @Override
    public List<Object> observe(WorldState worldState, long timestampMillis) {
        List<Object> events = new ArrayList<>();
        Lane lane = worldState.getEgo().getLane();

        Lane leftLane = lane.left();
        Lane rightLane = lane.right();

        WorldState.LaneOccupancy left = worldState.nearestLaneOccupancy(leftLane, rangeMeters());
        WorldState.LaneOccupancy right = worldState.nearestLaneOccupancy(rightLane, rangeMeters());

        events.add(new LaneOccupancyEvent(
            timestampMillis,
            id(),
            left.lane(),
            left.occupied(),
            left.nearestDistanceMeters()
        ));
        events.add(new LaneOccupancyEvent(
            timestampMillis,
            id(),
            right.lane(),
            right.occupied(),
            right.nearestDistanceMeters()
        ));
        return events;
    }
}
