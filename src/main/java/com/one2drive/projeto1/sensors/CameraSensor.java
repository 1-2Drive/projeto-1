package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CameraSensor extends AbstractSensor<Object> {
    public CameraSensor(SimulationConfig config) {
        super("camera", config.getCameraRateMillis(), config.getCameraRangeMeters());
    }

    @Override
    public List<Object> observe(WorldState worldState, long timestampMillis) {
        List<Object> events = new ArrayList<>();

        Optional<SpeedLimitSign> sign = worldState.nearestSpeedLimitSign(rangeMeters());
        sign.ifPresent(s -> events.add(new SpeedLimitDetectedEvent(
            timestampMillis,
            id(),
            s.getLimitKmh(),
            worldState.signedDistance(s.getPositionMeters()),
            s.getId()
        )));

        Optional<TrafficLight> light = worldState.nearestTrafficLight(rangeMeters());
        light.ifPresent(l -> events.add(new TrafficLightEvent(
            timestampMillis,
            id(),
            l.getState(),
            worldState.signedDistance(l.getPositionMeters()),
            l.getId()
        )));

        Optional<StopSign> stopSign = worldState.nearestStopSign(rangeMeters());
        stopSign.ifPresent(s -> events.add(new StopSignEvent(
            timestampMillis,
            id(),
            worldState.signedDistance(s.getPositionMeters()),
            s.getId()
        )));

        Optional<Pedestrian> pedestrian = worldState.nearestPedestrianInLane(rangeMeters());
        pedestrian.ifPresent(p -> events.add(new PedestrianDetectedEvent(
            timestampMillis,
            id(),
            worldState.signedDistance(p.getPositionMeters()),
            p.getId(),
            true
        )));

        Optional<Pothole> pothole = worldState.nearestPotholeAhead(rangeMeters());
        pothole.ifPresent(p -> events.add(new PotholeDetectedEvent(
            timestampMillis,
            id(),
            worldState.signedDistance(p.getPositionMeters()),
            p.getId(),
            p.getSeverity(),
            p.getLane()
        )));

        Optional<Barrier> barrier = worldState.nearestBarrier(rangeMeters());
        barrier.ifPresent(b -> events.add(new BarrierDetectedEvent(
            timestampMillis,
            id(),
            worldState.signedDistance(b.getPositionMeters()),
            b.getId(),
            b.isOpen(),
            b.getLane()
        )));

        Optional<StaticObstacle> obstacle = worldState.nearestObstacleAhead(rangeMeters());
        obstacle.ifPresent(o -> events.add(new ObstacleDetectedEvent(
            timestampMillis,
            id(),
            worldState.signedDistance(o.getPositionMeters()),
            o.getId(),
            o.getSeverity(),
            o.getLane()
        )));

        return events;
    }
}
