package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.Roundabout;
import com.one2drive.projeto1.domain.Roadworks;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.YieldSign;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.RoundaboutDetectedEvent;
import com.one2drive.projeto1.drools.events.RoadworksDetectedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.YieldSignEvent;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CameraSensor extends AbstractSensor<Object> {
    public CameraSensor(SimulationConfig config) {
        super("camera", config.getCameraRateMillis(), config.getCameraRangeMeters());
    }

    @Override
    public List<Object> observe(WorldState worldState, long timestampMillis) {
        List<Object> events = new ArrayList<>();
        double egoPos = worldState.getEgo().getPositionMeters();

        Optional<SpeedLimitSign> sign = worldState.nearestSpeedLimitSign(rangeMeters());
        sign.ifPresent(s -> events.add(new SpeedLimitDetectedEvent(
            timestampMillis,
            id(),
            s.getLimitKmh(),
            s.getPositionMeters() - egoPos,
            s.getId()
        )));

        Optional<TrafficLight> light = worldState.nearestTrafficLight(rangeMeters());
        light.ifPresent(l -> events.add(new TrafficLightEvent(
            timestampMillis,
            id(),
            l.getState(),
            l.getPositionMeters() - egoPos,
            l.getId()
        )));

        Optional<StopSign> stopSign = worldState.nearestStopSign(rangeMeters());
        stopSign.ifPresent(s -> events.add(new StopSignEvent(
            timestampMillis,
            id(),
            s.getPositionMeters() - egoPos,
            s.getId()
        )));

        Optional<YieldSign> yieldSign = worldState.activeRoadElementsInFront(rangeMeters()).stream()
            .filter(YieldSign.class::isInstance)
            .map(YieldSign.class::cast)
            .min((a, b) -> Double.compare(a.getPositionMeters(), b.getPositionMeters()));
        yieldSign.ifPresent(y -> events.add(new YieldSignEvent(
            timestampMillis,
            id(),
            y.getPositionMeters() - egoPos,
            y.getId()
        )));

        Optional<Pedestrian> pedestrian = worldState.nearestPedestrianInLane(rangeMeters());
        pedestrian.ifPresent(p -> events.add(new PedestrianDetectedEvent(
            timestampMillis,
            id(),
            p.getPositionMeters() - egoPos,
            p.getId(),
            true
        )));

        Optional<Pothole> pothole = worldState.nearestPotholeAnyLane(rangeMeters());
        pothole.ifPresent(p -> events.add(new PotholeDetectedEvent(
            timestampMillis,
            id(),
            p.getPositionMeters() - egoPos,
            p.getId(),
            p.getSeverity(),
            p.getLane()
        )));

        Optional<Barrier> barrier = worldState.nearestBarrier(rangeMeters());
        barrier.ifPresent(b -> events.add(new BarrierDetectedEvent(
            timestampMillis,
            id(),
            b.getPositionMeters() - egoPos,
            b.getId(),
            b.isOpen()
        )));

        Optional<Roundabout> roundabout = worldState.nearestRoundabout(rangeMeters());
        roundabout.ifPresent(r -> events.add(new RoundaboutDetectedEvent(
            timestampMillis,
            id(),
            r.getPositionMeters() - egoPos,
            r.getId(),
            r.getRecommendedApproachSpeedKmh(),
            worldState.nearestFrontVehicleInLane(20).isPresent()
        )));

        Optional<Roadworks> roadworks = worldState.nearestRoadworks(rangeMeters());
        roadworks.ifPresent(r -> events.add(new RoadworksDetectedEvent(
            timestampMillis,
            id(),
            r.getPositionMeters() - egoPos,
            r.getId(),
            r.getSeverity()
        )));

        Optional<StaticObstacle> obstacle = worldState.nearestObstacleAnyLane(rangeMeters());
        obstacle.ifPresent(o -> events.add(new ObstacleDetectedEvent(
            timestampMillis,
            id(),
            o.getPositionMeters() - egoPos,
            o.getId(),
            o.getSeverity(),
            o.getLane()
        )));

        return events;
    }
}
