package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.SpeedRadar;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedRadarEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class FrontRadarSensor extends AbstractSensor<Object> {
    /**
     * Smallest distance the radar publishes for a tracked vehicle. The rule that updates the
     * front vehicle treats a distance of 0 or less as "no target", while the relative-speed and
     * TTC rules adopt the target again from the same sample; a vehicle that is really ahead but
     * whose noisy reading came out at 0 m would make them undo each other without end.
     */
    private static final double MIN_MEASURED_DISTANCE_METERS = 0.1;

    private final Random random;
    private final SimulationConfig config;

    public FrontRadarSensor(SimulationConfig config, long randomSeed) {
        super("front-radar", config.getRadarRateMillis(), config.getRadarRangeMeters());
        this.config = config;
        this.random = new Random(randomSeed);
    }

    @Override
    public List<Object> observe(WorldState worldState, long timestampMillis) {
        List<Object> events = new ArrayList<>();

        Optional<SpeedRadar> speedRadar = worldState.findNearestSpeedRadarAhead(Math.max(rangeMeters(), 250.0));
        speedRadar.ifPresent(radar -> {
            double distanceToRadar = worldState.signedDistance(radar.getPositionMeters());
            events.add(new SpeedRadarEvent(timestampMillis, radar.getId(), distanceToRadar, radar.getSpeedLimitKmh()));
        });

        Optional<OtherVehicle> front = worldState.nearestFrontVehicleInLane(rangeMeters());
        front.ifPresent(vehicle -> {
            double trueDistance = worldState.signedDistance(vehicle.getPositionMeters());
            // nearestFrontVehicleInLane only returns vehicles ahead (d > 0), so noise must not
            // publish one at 0 m or behind the car.
            double measuredDistance = Math.max(MIN_MEASURED_DISTANCE_METERS,
                trueDistance + random.nextGaussian() * config.getDistanceNoiseStdMeters());
            double relativeSpeed = worldState.getEgo().getSpeedMps() - vehicle.getSpeedMps();
            double ttc = relativeSpeed > 0.05 ? measuredDistance / relativeSpeed : Double.POSITIVE_INFINITY;

            events.add(new FrontDistanceEvent(timestampMillis, id(), measuredDistance, vehicle.getId()));
            events.add(new RelativeSpeedEvent(timestampMillis, id(), relativeSpeed, vehicle.getId()));
            events.add(new TTCEvent(timestampMillis, id(), ttc, vehicle.getId()));
        });

        return events;
    }
}
