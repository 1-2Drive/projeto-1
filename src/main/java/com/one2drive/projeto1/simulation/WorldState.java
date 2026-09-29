package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.Roundabout;
import com.one2drive.projeto1.domain.Roadworks;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.WeatherState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class WorldState {
    private final EgoVehicleState ego;
    private final WeatherState weatherState;
    private final List<RoadElement> roadElements;
    private final List<DynamicActor> actors;
    private long simTimeMillis;

    public WorldState(EgoVehicleState ego, WeatherState weatherState) {
        this.ego = ego;
        this.weatherState = weatherState;
        this.roadElements = new ArrayList<>();
        this.actors = new ArrayList<>();
        this.simTimeMillis = 0L;
    }

    public EgoVehicleState getEgo() {
        return ego;
    }

    public WeatherState getWeatherState() {
        return weatherState;
    }

    public long getSimTimeMillis() {
        return simTimeMillis;
    }

    public void setSimTimeMillis(long simTimeMillis) {
        this.simTimeMillis = simTimeMillis;
    }

    public void addRoadElement(RoadElement element) {
        roadElements.add(element);
    }

    public void addActor(DynamicActor actor) {
        actors.add(actor);
    }

    public Optional<RoadElement> findRoadElementById(String id) {
        return roadElements.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Optional<DynamicActor> findActorById(String id) {
        return actors.stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    public boolean removeRoadElementById(String id) {
        return roadElements.removeIf(e -> e.getId().equals(id));
    }

    public boolean removeActorById(String id) {
        return actors.removeIf(a -> a.getId().equals(id));
    }

    public List<RoadElement> getRoadElements() {
        return Collections.unmodifiableList(roadElements);
    }

    public List<DynamicActor> getActors() {
        return Collections.unmodifiableList(actors);
    }

    public Optional<OtherVehicle> nearestFrontVehicleInLane(double maxDistance) {
        double egoPos = ego.getPositionMeters();
        Lane egoLane = ego.getLane();
        OtherVehicle nearest = null;
        double best = Double.MAX_VALUE;
        for (DynamicActor actor : actors) {
            if (!(actor instanceof OtherVehicle vehicle) || !vehicle.isActive()) {
                continue;
            }
            if (vehicle.getLane() != egoLane) {
                continue;
            }
            double d = vehicle.getPositionMeters() - egoPos;
            if (d > 0 && d <= maxDistance && d < best) {
                best = d;
                nearest = vehicle;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Optional<Pedestrian> nearestPedestrianInLane(double maxDistance) {
        double egoPos = ego.getPositionMeters();
        Lane egoLane = ego.getLane();
        Pedestrian nearest = null;
        double best = Double.MAX_VALUE;
        for (DynamicActor actor : actors) {
            if (!(actor instanceof Pedestrian pedestrian) || !pedestrian.isActive()) {
                continue;
            }
            if (pedestrian.getLane() != egoLane) {
                continue;
            }
            double d = pedestrian.getPositionMeters() - egoPos;
            if (d > 0 && d <= maxDistance && d < best) {
                best = d;
                nearest = pedestrian;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public List<RoadElement> activeRoadElementsInFront(double maxDistance) {
        double egoPos = ego.getPositionMeters();
        List<RoadElement> result = new ArrayList<>();
        for (RoadElement element : roadElements) {
            if (!element.isActive()) {
                continue;
            }
            double d = element.getPositionMeters() - egoPos;
            if (d >= 0 && d <= maxDistance) {
                result.add(element);
            }
        }
        return result;
    }

    public Optional<SpeedLimitSign> nearestSpeedLimitSign(double maxDistance) {
        return nearestByType(SpeedLimitSign.class, maxDistance);
    }

    public Optional<TrafficLight> nearestTrafficLight(double maxDistance) {
        return nearestByType(TrafficLight.class, maxDistance);
    }

    public Optional<StopSign> nearestStopSign(double maxDistance) {
        return nearestByType(StopSign.class, maxDistance);
    }

    public Optional<Pothole> nearestPothole(double maxDistance) {
        return nearestByType(Pothole.class, maxDistance);
    }

    public Optional<Barrier> nearestBarrier(double maxDistance) {
        return nearestByType(Barrier.class, maxDistance);
    }

    public Optional<Roundabout> nearestRoundabout(double maxDistance) {
        return nearestByType(Roundabout.class, maxDistance);
    }

    public Optional<StaticObstacle> nearestObstacle(double maxDistance) {
        return nearestByType(StaticObstacle.class, maxDistance);
    }

    public Optional<Roadworks> nearestRoadworks(double maxDistance) {
        return nearestByType(Roadworks.class, maxDistance);
    }

    @SuppressWarnings("unchecked")
    private <T extends RoadElement> Optional<T> nearestByType(Class<T> type, double maxDistance) {
        double egoPos = ego.getPositionMeters();
        Lane egoLane = ego.getLane();
        T nearest = null;
        double best = Double.MAX_VALUE;
        for (RoadElement element : roadElements) {
            if (!type.isInstance(element) || !element.isActive()) {
                continue;
            }
            if (element.getLane() != egoLane) {
                continue;
            }
            double d = element.getPositionMeters() - egoPos;
            if (d >= 0 && d <= maxDistance && d < best) {
                best = d;
                nearest = (T) element;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public LaneOccupancy nearestLaneOccupancy(Lane lane, double longitudinalRange) {
        double egoPos = ego.getPositionMeters();
        boolean occupied = false;
        double best = Double.POSITIVE_INFINITY;
        for (DynamicActor actor : actors) {
            if (!actor.isActive() || actor.getLane() != lane) {
                continue;
            }
            double d = actor.getPositionMeters() - egoPos;
            if (Math.abs(d) <= longitudinalRange) {
                occupied = true;
                best = Math.min(best, Math.abs(d));
            }
        }
        if (!occupied) {
            best = Double.POSITIVE_INFINITY;
        }
        return new LaneOccupancy(lane, occupied, best);
    }

    public record LaneOccupancy(Lane lane, boolean occupied, double nearestDistanceMeters) {
    }
}
