package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.WeatherState;

import com.one2drive.projeto1.domain.RoadMarkingType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Everything the simulated sensors can observe. Fixed road elements live in the
 * {@link RoadMap} matrix; vehicles and pedestrians move freely on the same looping
 * road, so every distance is measured around the loop.
 */
public class WorldState {
    private final EgoVehicleState ego;
    private final WeatherState weatherState;
    private final RoadMap roadMap;
    private final List<DynamicActor> actors;
    private long simTimeMillis;
    private RoadMarkingType leftMarking = RoadMarkingType.DASHED;
    private RoadMarkingType rightMarking = RoadMarkingType.DASHED;

    public WorldState(EgoVehicleState ego, WeatherState weatherState, RoadMap roadMap) {
        this.ego = ego;
        this.weatherState = weatherState;
        this.roadMap = roadMap;
        this.actors = new ArrayList<>();
        this.simTimeMillis = 0L;
    }

    public RoadMap getRoadMap() {
        return roadMap;
    }

    /** Distance from the ego forward to the next occurrence of a position on the loop. */
    public double distanceAhead(double positionMeters) {
        return roadMap.distanceAhead(ego.getPositionMeters(), positionMeters);
    }

    /** Shortest signed distance from the ego to a position on the loop; negative is behind. */
    public double signedDistance(double positionMeters) {
        return roadMap.signedDistance(ego.getPositionMeters(), positionMeters);
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

    public RoadMarkingType getLeftMarking() {
        return leftMarking;
    }

    public void setLeftMarking(RoadMarkingType leftMarking) {
        this.leftMarking = leftMarking;
    }

    public RoadMarkingType getRightMarking() {
        return rightMarking;
    }

    public void setRightMarking(RoadMarkingType rightMarking) {
        this.rightMarking = rightMarking;
    }

    public RoadMarkingType getMarkingBetween(Lane from, Lane to) {
        if ((from == Lane.CENTER && to == Lane.LEFT) || (from == Lane.LEFT && to == Lane.CENTER)) {
            return leftMarking;
        }
        if ((from == Lane.CENTER && to == Lane.RIGHT) || (from == Lane.RIGHT && to == Lane.CENTER)) {
            return rightMarking;
        }
        return RoadMarkingType.DASHED;
    }

    public void addActor(DynamicActor actor) {
        actors.add(actor);
    }

    public Optional<RoadElement> findRoadElementById(String id) {
        return roadMap.findElement(id);
    }

    public Optional<DynamicActor> findActorById(String id) {
        return actors.stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    public boolean removeRoadElementById(String id) {
        return roadMap.clear(id);
    }

    public boolean removeActorById(String id) {
        return actors.removeIf(a -> a.getId().equals(id));
    }

    /** Road elements of every occupied map cell. */
    public List<RoadElement> getRoadElements() {
        return roadMap.elements();
    }

    public List<DynamicActor> getActors() {
        return Collections.unmodifiableList(actors);
    }

    public Optional<OtherVehicle> nearestFrontVehicleInLane(double maxDistance) {
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
            double d = distanceAhead(vehicle.getPositionMeters());
            if (d > 0 && d <= maxDistance && d < best) {
                best = d;
                nearest = vehicle;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Optional<Pedestrian> nearestPedestrianInLane(double maxDistance) {
        double egoLateral = ego.getLateralPositionMeters();
        Pedestrian nearest = null;
        double best = Double.MAX_VALUE;
        for (DynamicActor actor : actors) {
            if (!(actor instanceof Pedestrian pedestrian) || !pedestrian.isActive()) {
                continue;
            }
            if (!pedestrian.isInRoadway()) {
                continue;
            }
            double d = distanceAhead(pedestrian.getPositionMeters());
            boolean inPath = (pedestrian.getLane() == ego.getLane())
                || pedestrian.isCrossing()
                || Math.abs(pedestrian.getLateralPositionMeters() - egoLateral) <= 3.5;
            if (inPath && d > 0 && d <= maxDistance && d < best) {
                best = d;
                nearest = pedestrian;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public List<RoadElement> activeRoadElementsInFront(double maxDistance) {
        List<RoadElement> result = new ArrayList<>();
        for (RoadElement element : getRoadElements()) {
            if (!element.isActive()) {
                continue;
            }
            double d = distanceAhead(element.getPositionMeters());
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

    public Optional<StaticObstacle> nearestObstacle(double maxDistance) {
        return nearestByType(StaticObstacle.class, maxDistance);
    }

    @SuppressWarnings("unchecked")
    private <T extends RoadElement> Optional<T> nearestByType(Class<T> type, double maxDistance) {
        Lane egoLane = ego.getLane();
        T nearest = null;
        double best = Double.MAX_VALUE;
        for (RoadElement element : getRoadElements()) {
            if (!type.isInstance(element) || !element.isActive()) {
                continue;
            }
            if (element.getLane() != egoLane) {
                continue;
            }
            double d = distanceAhead(element.getPositionMeters());
            if (d >= 0 && d <= maxDistance && d < best) {
                best = d;
                nearest = (T) element;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Nearest hazard of the given type ahead, preferring the vehicle's own lane. A
     * forward camera sees every lane and reports the lane with the hazard, but with a
     * map full of hazards the one in the vehicle's path is the one the rules must see.
     */
    @SuppressWarnings("unchecked")
    private <T extends RoadElement> Optional<T> nearestHazardAhead(Class<T> type, double maxDistance) {
        T nearestOwnLane = null;
        T nearestAnyLane = null;
        double bestOwnLane = Double.MAX_VALUE;
        double bestAnyLane = Double.MAX_VALUE;
        for (RoadElement element : getRoadElements()) {
            if (!type.isInstance(element) || !element.isActive()) {
                continue;
            }
            double d = distanceAhead(element.getPositionMeters());
            if (d > maxDistance) {
                continue;
            }
            if (d < bestAnyLane) {
                bestAnyLane = d;
                nearestAnyLane = (T) element;
            }
            if (element.getLane() == ego.getLane() && d < bestOwnLane) {
                bestOwnLane = d;
                nearestOwnLane = (T) element;
            }
        }
        return Optional.ofNullable(nearestOwnLane != null ? nearestOwnLane : nearestAnyLane);
    }

    public Optional<com.one2drive.projeto1.domain.SpeedRadar> findNearestSpeedRadarAhead(double maxDistance) {
        return nearestHazardAhead(com.one2drive.projeto1.domain.SpeedRadar.class, maxDistance);
    }

    public Optional<Pothole> nearestPotholeAhead(double maxDistance) {
        return nearestHazardAhead(Pothole.class, maxDistance);
    }

    public Optional<StaticObstacle> nearestObstacleAhead(double maxDistance) {
        return nearestHazardAhead(StaticObstacle.class, maxDistance);
    }

    /**
     * Whether the lane on one side of the vehicle can be moved into. Past the road edge
     * there is no lane, so that side always reports occupied. Otherwise a vehicle or
     * pedestrian alongside within {@code actorRange}, or a fixed hazard (obstacle,
     * pothole, closed barrier) in that lane from alongside up to {@code hazardLookahead}
     * ahead, occupies it.
     *
     * @return occupancy whose {@code lane} is the side (LEFT or RIGHT), as the rules read it
     */
    public LaneOccupancy sideLaneOccupancy(LaneChangeDirection side, double actorRange, double hazardLookahead) {
        Lane sideName = side == LaneChangeDirection.LEFT ? Lane.LEFT : Lane.RIGHT;
        Lane target = side == LaneChangeDirection.LEFT ? ego.getLane().left() : ego.getLane().right();
        if (target == ego.getLane()) {
            return new LaneOccupancy(sideName, true, 0.0, true);
        }

        double best = Double.POSITIVE_INFINITY;
        for (DynamicActor actor : actors) {
            if (!actor.isActive() || actor.getLane() != target) {
                continue;
            }
            double d = Math.abs(signedDistance(actor.getPositionMeters()));
            if (d <= actorRange) {
                best = Math.min(best, d);
            }
        }
        for (RoadElement element : getRoadElements()) {
            if (!element.isActive() || element.getLane() != target || !blocksLane(element)) {
                continue;
            }
            // Elements sit at the start of their block, so one still alongside is up to a block behind.
            double d = signedDistance(element.getPositionMeters());
            if (d >= -roadMap.blockLengthMeters() && d <= hazardLookahead) {
                best = Math.min(best, Math.abs(d));
            }
        }
        return new LaneOccupancy(sideName, Double.isFinite(best), best, false);
    }

    private static boolean blocksLane(RoadElement element) {
        return element instanceof StaticObstacle
            || element instanceof Pothole
            || (element instanceof Barrier barrier && !barrier.isOpen());
    }

    /** {@code roadEdge} marks a side with no lane at all, which is why it is occupied. */
    public record LaneOccupancy(Lane lane, boolean occupied, double nearestDistanceMeters, boolean roadEdge) {
    }
}
