package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.LaneChangeState;

/**
 * Advances world physics. Longitudinal motion is velocity integration; lateral
 * motion is a smooth interpolation toward the target lane centre so a maneuver is
 * visible as continuous movement rather than a jump.
 */
public class WorldPhysicsEngine {
    /** Below this speed the vehicle counts as stopped. */
    private static final double AT_REST_SPEED_KMH = 0.1;

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
