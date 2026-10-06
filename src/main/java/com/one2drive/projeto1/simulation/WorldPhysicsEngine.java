package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.LaneChangeState;

/**
 * Advances world physics. Longitudinal motion is velocity integration; lateral
 * motion is a smooth interpolation toward the target lane centre so a maneuver is
 * visible as continuous movement rather than a jump.
 */
public class WorldPhysicsEngine {
    private final SimulationConfig config;

    public WorldPhysicsEngine(SimulationConfig config) {
        this.config = config;
    }

    public void updateWorld(WorldState worldState, double dtSeconds) {
        EgoVehicleState ego = worldState.getEgo();
        // No end of road: the map is a loop, so position keeps counting metres travelled.
        ego.advance(dtSeconds);
        advanceLaneChange(ego, dtSeconds);

        for (DynamicActor actor : worldState.getActors()) {
            if (!actor.isActive()) {
                continue;
            }
            actor.advance(dtSeconds);
        }
    }

    /**
     * Moves the vehicle toward the centre line of its target lane.
     *
     * @return true when the maneuver reached its target on this tick
     */
    public boolean advanceLaneChange(EgoVehicleState ego, double dtSeconds) {
        if (ego.getLaneChangeState() == LaneChangeState.IDLE) {
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
            return true;
        }

        // Progress is a linear accumulator; the rendered position applies smoothstep
        // on top of it so the vehicle eases out of the old lane and into the new one.
        double step = Math.max(config.getLaneChangeMetersPerSecond(), 0.1) * dtSeconds;
        double progress = Math.min(1.0, ego.getLaneChangeProgress() + step / distance);
        ego.setLaneChangeProgress(progress);
        ego.setLateralPositionMeters(origin + (target - origin) * smoothstep(progress));

        if (progress >= 1.0) {
            ego.setLateralPositionMeters(target);
            ego.setLaneChangeState(LaneChangeState.COMPLETED);
            return true;
        }
        return false;
    }

    private static double smoothstep(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }
}
