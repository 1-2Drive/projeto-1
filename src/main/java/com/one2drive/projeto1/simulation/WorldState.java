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
    private boolean loopWorldObjects;
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

    public boolean isLoopWorldObjects() {
        return loopWorldObjects;
    }

    public void setLoopWorldObjects(boolean loopWorldObjects) {
        this.loopWorldObjects = loopWorldObjects;
    }

    public Optional<RoadElement> findRoadElementById(String id) {
        return roadMap.findElement(id).or(() -> roadMap.findCellIdByElementId(id).flatMap(roadMap::findElement));
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
            double d = signedDistance(vehicle.getPositionMeters());
            if (d > 0.0 && d <= maxDistance && d < best) {
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
            double d = signedDistance(pedestrian.getPositionMeters());
            boolean inPath = (pedestrian.getLane() == ego.getLane())
                || pedestrian.isCrossing()
                || Math.abs(pedestrian.getLateralPositionMeters() - egoLateral) <= 3.5;
            if (inPath && d > 0.0 && d <= maxDistance && d < best) {
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
            double d = signedDistance(element.getPositionMeters());
            if (d > 0.0 && d <= maxDistance) {
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
        T nearestOwnLane = null;
        T nearestAnyLane = null;
        double bestOwnLane = Double.MAX_VALUE;
        double bestAnyLane = Double.MAX_VALUE;
        for (RoadElement element : getRoadElements()) {
            if (!type.isInstance(element) || !element.isActive()) {
                continue;
            }
            double d = signedDistance(element.getPositionMeters());
            if (d <= 0.0 || d > maxDistance) {
                continue;
            }
            if (d < bestAnyLane) {
                bestAnyLane = d;
                nearestAnyLane = (T) element;
            }
            if (element.getLane() == egoLane && d < bestOwnLane) {
                bestOwnLane = d;
                nearestOwnLane = (T) element;
            }
        }
        return Optional.ofNullable(nearestOwnLane != null ? nearestOwnLane : nearestAnyLane);
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
            double d = signedDistance(element.getPositionMeters());
            if (d <= 0.0 || d > maxDistance) {
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
        com.one2drive.projeto1.domain.SpeedRadar nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (RoadElement element : getRoadElements()) {
            if (element instanceof com.one2drive.projeto1.domain.SpeedRadar radar && radar.isActive()) {
                double distance = signedDistance(radar.getPositionMeters());
                if (distance > 0 && distance <= maxDistance && distance < nearestDistance) {
                    nearest = radar;
                    nearestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Optional<Pothole> nearestPotholeAhead(double maxDistance) {
        return nearestHazardAhead(Pothole.class, maxDistance);
    }

    public Optional<StaticObstacle> nearestObstacleAhead(double maxDistance) {
        return nearestHazardAhead(StaticObstacle.class, maxDistance);
    }

    public boolean hasHazardAheadInLane(Lane lane, double maxDistance) {
        for (RoadElement element : getRoadElements()) {
            if (!element.isActive() || element.getLane() != lane || !blocksLane(element)) {
                continue;
            }
            double d = signedDistance(element.getPositionMeters());
            if (d > 0.0 && d <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the lane on one side of the vehicle can be moved into. Past the road edge
     * there is no lane, so that side always reports occupied. Evaluates static hazards,
     * pedestrians, and dynamic actors with comprehensive safety gaps (front gap, rear gap, TTC).
     *
     * @return occupancy whose {@code lane} is the side (LEFT or RIGHT), as the rules read it
     */
    public LaneOccupancy sideLaneOccupancy(LaneChangeDirection side, double actorRange, double hazardLookahead) {
        Lane sideName = side == LaneChangeDirection.LEFT ? Lane.LEFT : Lane.RIGHT;
        Lane target = side == LaneChangeDirection.LEFT ? ego.getLane().left() : ego.getLane().right();
        if (target == ego.getLane()) {
            return new LaneOccupancy(sideName, true, 0.0, true, false, 0.0, 0.0, 0.0);
        }
        if (getMarkingBetween(ego.getLane(), target) == RoadMarkingType.CONTINUOUS) {
            return new LaneOccupancy(sideName, true, 0.0, false, false, 0.0, 0.0, 0.0);
        }

        double best = Double.POSITIVE_INFINITY;
        boolean hazardBlocking = false;

        for (RoadElement element : getRoadElements()) {
            if (!element.isActive() || element.getLane() != target || !blocksLane(element)) {
                continue;
            }
            // Elements sit at the start of their block, so one still alongside is up to a block behind.
            double d = signedDistance(element.getPositionMeters());
            if (d >= -roadMap.blockLengthMeters() && d <= hazardLookahead) {
                best = Math.min(best, Math.abs(d));
                hazardBlocking = true;
            }
        }

        double frontDist = Double.POSITIVE_INFINITY;
        double rearDist = Double.POSITIVE_INFINITY;
        double rearTtc = Double.POSITIVE_INFINITY;
        boolean actorUnsafe = false;

        for (DynamicActor actor : actors) {
            if (!actor.isActive()) {
                continue;
            }
            if (actor instanceof Pedestrian ped) {
                if (ped.getLane() == target || Math.abs(ped.getLateralPositionMeters() - target.centreMeters()) <= 2.5) {
                    double signedDist = signedDistance(ped.getPositionMeters());
                    if (signedDist >= -5.0 && signedDist <= 35.0) {
                        actorUnsafe = true;
                        best = Math.min(best, Math.abs(signedDist));
                    }
                }
                continue;
            }

            if (actor.getLane() != target) {
                continue;
            }

            double signedDist = signedDistance(actor.getPositionMeters());
            double absDist = Math.abs(signedDist);
            best = Math.min(best, absDist);

            // Blind spot sensor zone check (within actorRange)
            if (absDist <= actorRange) {
                actorUnsafe = true;
            } else if (signedDist > 0) {
                // Actor ahead in target lane
                frontDist = Math.min(frontDist, signedDist);
                double closingSpeed = ego.getSpeedMps() - actor.getSpeedMps();
                if (signedDist < 14.0) {
                    actorUnsafe = true;
                } else if (closingSpeed > 0.5 && (signedDist / closingSpeed) < 3.0) {
                    actorUnsafe = true;
                }
            } else {
                // Actor behind in target lane
                double gapBehind = Math.abs(signedDist);
                rearDist = Math.min(rearDist, gapBehind);
                double closingSpeed = actor.getSpeedMps() - ego.getSpeedMps();
                if (gapBehind < 12.0) {
                    actorUnsafe = true;
                } else if (closingSpeed > 0.5) {
                    double ttc = gapBehind / closingSpeed;
                    rearTtc = Math.min(rearTtc, ttc);
                    if (ttc < 3.5) {
                        actorUnsafe = true;
                    }
                }
            }
        }

        boolean occupied = hazardBlocking || actorUnsafe;
        boolean safeToEnter = !occupied;
        return new LaneOccupancy(sideName, occupied, Double.isFinite(best) ? best : 0.0, false, safeToEnter, frontDist, rearDist, rearTtc);
    }

    /** Whether an absolute lane is clear and safe for the ego to occupy. */
    public boolean isLaneSafe(Lane lane, double actorRange, double hazardLookahead) {
        if (lane == ego.getLane()) return false;
        LaneChangeDirection direction = lane.centreMeters() < ego.getLane().centreMeters()
            ? LaneChangeDirection.LEFT : LaneChangeDirection.RIGHT;
        Lane immediate = direction == LaneChangeDirection.LEFT ? ego.getLane().left() : ego.getLane().right();
        if (immediate != lane) return false;
        return !sideLaneOccupancy(direction, actorRange, hazardLookahead).occupied();
    }

    private static boolean blocksLane(RoadElement element) {
        return element instanceof StaticObstacle
            || element instanceof Pothole
            || (element instanceof Barrier barrier && !barrier.isOpen());
    }

    /** {@code roadEdge} marks a side with no lane at all, which is why it is occupied. */
    public record LaneOccupancy(
        Lane lane,
        boolean occupied,
        double nearestDistanceMeters,
        boolean roadEdge,
        boolean safeToEnter,
        double frontDistanceMeters,
        double rearDistanceMeters,
        double rearTtcSeconds
    ) {
        public LaneOccupancy(Lane lane, boolean occupied, double nearestDistanceMeters, boolean roadEdge) {
            this(lane, occupied, nearestDistanceMeters, roadEdge, !occupied && !roadEdge,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
    }
}
