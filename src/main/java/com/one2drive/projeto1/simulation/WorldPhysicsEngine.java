package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pedestrian;

/**
 * Advances world physics. Longitudinal motion is velocity integration; lateral
 * motion is a smooth interpolation toward the target lane centre so a maneuver is
 * visible as continuous movement rather than a jump.
 */
public class WorldPhysicsEngine {
    /** Below this speed the vehicle counts as stopped. */
    private static final double AT_REST_SPEED_KMH = 0.1;
    /** How far ahead another vehicle looks for a pedestrian to give way to. */
    private static final double PEDESTRIAN_LOOKAHEAD_METERS = 60.0;
    /** Where another vehicle comes to rest before a pedestrian it gives way to. */
    private static final double PEDESTRIAN_STOP_GAP_METERS = 15.0;
    /** Below this deceleration there is still room: the vehicle keeps its speed for now. */
    private static final double START_BRAKING_MPS2 = 1.5;

    private final SimulationConfig config;

    public WorldPhysicsEngine(SimulationConfig config) {
        this.config = config;
    }

    public void updateWorld(WorldState worldState, double dtSeconds) {
        // State representation vector: [x, y, vx, vy] for ego followed by active actors
        EgoVehicleState ego = worldState.getEgo();
        // Whether the vehicle was at rest when this step began, before it is moved.
        boolean egoAtRest = ego.getSpeedKmh() < AT_REST_SPEED_KMH;
        // No end of road: the map is a loop, so position keeps counting metres travelled.
        ego.advance(dtSeconds);
        advanceLaneChange(ego, dtSeconds);

        for (DynamicActor actor : worldState.getActors()) {
            if (!actor.isActive()) {
                continue;
            }
            if (actor instanceof OtherVehicle vehicle) {
                giveWayToPedestrians(worldState, vehicle, dtSeconds);
            }
            actor.advance(dtSeconds);
        }

        // The traffic light cycle is part of the world: time passes for it like for the actors.
        // The red time only runs while the vehicle is waiting at the light: at rest, before
        // the light and no further back than the stop line and its tolerance.
        worldState.getRoadMap().advanceTrafficLights(
            Math.round(dtSeconds * 1000.0),
            Math.round(config.getYellowLightSeconds() * 1000.0),
            config.getRedHoldAfterStopMillis(),
            light -> {
                double distance = worldState.signedDistance(light.getPositionMeters());
                return egoAtRest && distance > 0.0
                    && distance <= StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS + StoppingPolicy.STOP_LINE_TOLERANCE_METERS;
            });
    }

    /**
     * The other vehicles give way to pedestrians as the ego does (Art. 103 CE): a pedestrian
     * ahead in the vehicle's lane, within 3.5 m of it, or crossing the road is stopped for
     * {@link #PEDESTRIAN_STOP_GAP_METERS} short of it, braking only as hard as the room left needs
     * (never past the emergency deceleration). With the way clear the vehicle speeds back up
     * to its cruise speed. Without this they drove through a pedestrian at constant speed.
     */
    private void giveWayToPedestrians(WorldState worldState, OtherVehicle vehicle, double dtSeconds) {
        double nearest = Double.POSITIVE_INFINITY;
        for (DynamicActor other : worldState.getActors()) {
            if (!(other instanceof Pedestrian pedestrian) || !pedestrian.isActive()) {
                continue;
            }
            double ahead = pedestrian.getPositionMeters() - vehicle.getPositionMeters();
            boolean inConflict = pedestrian.getLane() == vehicle.getLane()
                || Math.abs(pedestrian.getLateralPositionMeters() - vehicle.getLane().centreMeters()) <= 3.5
                || pedestrian.isCrossingInProgress();
            if (inConflict && ahead > 0.0 && ahead <= PEDESTRIAN_LOOKAHEAD_METERS) {
                nearest = Math.min(nearest, ahead);
            }
        }
        double speed = vehicle.getSpeedMps();
        if (Double.isFinite(nearest)) {
            double room = nearest - PEDESTRIAN_STOP_GAP_METERS;
            double needed = room <= 0.0 ? config.getEmergencyDecelerationMps2() : speed * speed / (2.0 * room);
            if (needed >= START_BRAKING_MPS2 || room <= 0.0) {
                double braking = Math.min(needed, config.getEmergencyDecelerationMps2());
                vehicle.setCurrentSpeedMps(Math.max(0.0, speed - braking * dtSeconds));
            }
            return;
        }
        if (speed < vehicle.getCruiseSpeedMps()) {
            vehicle.setCurrentSpeedMps(Math.min(vehicle.getCruiseSpeedMps(), speed + config.getMaxAccelerationMps2() * dtSeconds));
        }
    }

    /**
     * Extracts a state representation vector for analysis / matrix modeling:
     * [ego_x, ego_y, ego_vx, ego_vy, actor1_x, actor1_y, actor1_vx, actor1_vy, ...]
     */
    public double[] extractStateVector(WorldState worldState) {
        EgoVehicleState ego = worldState.getEgo();
        java.util.List<DynamicActor> actors = worldState.getActors();
        double[] state = new double[4 + actors.size() * 4];
        state[0] = ego.getPositionMeters();
        state[1] = ego.getLateralPositionMeters();
        state[2] = ego.getSpeedMps();
        state[3] = ego.getSpeedMps() * Math.sin(Math.toRadians(ego.getHeadingDegrees()));

        for (int i = 0; i < actors.size(); i++) {
            DynamicActor a = actors.get(i);
            int base = 4 + i * 4;
            state[base] = a.getPositionMeters();
            state[base + 1] = (a instanceof com.one2drive.projeto1.domain.Pedestrian p)
                ? p.getLateralPositionMeters()
                : (a.getLane() != null ? a.getLane().centreMeters() : 0.0);
            state[base + 2] = a.getSpeedMps();
            state[base + 3] = (a instanceof com.one2drive.projeto1.domain.Pedestrian p)
                ? (p.isCrossing() ? p.getLateralSpeedMps() : 0.0)
                : 0.0;
        }
        return state;
    }

    /**
     * Moves the vehicle toward the centre line of its target lane.
     *
     * @return true when the maneuver reached its target on this tick
     */
    public boolean advanceLaneChange(EgoVehicleState ego, double dtSeconds) {
        if (ego.getLaneChangeState() == LaneChangeState.IDLE) {
            ego.setHeadingDegrees(0.0);
            return false;
        }

        ego.setLaneChangeState(LaneChangeState.CHANGING);

        double origin = ego.getLaneChangeOriginLateralMeters();
        double target = ego.getTargetLane().centreMeters();
        double distance = Math.abs(target - origin);
        if (distance < 1e-6) {
            ego.setLateralPositionMeters(target);
            ego.setLaneChangeProgress(1.0);
            ego.setLaneChangeState(LaneChangeState.COMPLETED);
            ego.setHeadingDegrees(0.0);
            return true;
        }

        // Progress is a linear accumulator; the rendered position applies smoothstep
        // on top of it so the vehicle eases out of the old lane and into the new one.
        double step = Math.max(config.getLaneChangeMetersPerSecond(), 0.1) * dtSeconds;
        double progress = Math.min(1.0, ego.getLaneChangeProgress() + step / distance);
        ego.setLaneChangeProgress(progress);
        double prevLateral = ego.getLateralPositionMeters();
        double newLateral = origin + (target - origin) * smoothstep(progress);
        ego.setLateralPositionMeters(newLateral);

        double forwardSpeed = Math.max(ego.getSpeedMps(), 2.0);
        double lateralVelocity = (newLateral - prevLateral) / dtSeconds;
        double heading = Math.toDegrees(Math.atan2(lateralVelocity, forwardSpeed));
        ego.setHeadingDegrees(Math.max(-15.0, Math.min(15.0, heading)));

        if (progress >= 1.0) {
            ego.setLateralPositionMeters(target);
            ego.setLaneChangeState(LaneChangeState.COMPLETED);
            ego.setHeadingDegrees(0.0);
            return true;
        }
        return false;
    }

    private static double smoothstep(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }
}
