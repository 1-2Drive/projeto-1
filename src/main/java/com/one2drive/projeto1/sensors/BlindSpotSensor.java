package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports whether the lane on each side of the vehicle can be moved into. Events are
 * keyed by side (LEFT = the lane to the vehicle's left), which is how the lane change
 * and avoidance rules read them.
 */
public class BlindSpotSensor extends AbstractSensor<Object> {
    /**
     * How far ahead a fixed hazard in a side lane makes it unusable. Avoidance starts
     * 50 m before a hazard, so a lane blocked within that window must not be chosen.
     */
    static final double HAZARD_LOOKAHEAD_METERS = 60.0;

    public BlindSpotSensor(SimulationConfig config) {
        super("blind-spot", config.getBlindSpotRateMillis(), config.getBlindSpotRangeMeters());
    }

    @Override
    public List<Object> observe(WorldState worldState, long timestampMillis) {
        List<Object> events = new ArrayList<>();
        for (LaneChangeDirection side : LaneChangeDirection.values()) {
            WorldState.LaneOccupancy occupancy =
                worldState.sideLaneOccupancy(side, rangeMeters(), HAZARD_LOOKAHEAD_METERS);
            events.add(new LaneOccupancyEvent(
                timestampMillis,
                id(),
                occupancy.lane(),
                occupancy.occupied(),
                occupancy.nearestDistanceMeters()
            ));
        }
        return events;
    }
}
