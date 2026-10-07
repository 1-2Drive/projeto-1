package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DrivingState;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.IndicatorState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RoadMarkingType;
import com.one2drive.projeto1.domain.SteeringState;

/**
 * Executes the maneuver Drools selected.
 *
 * <p>This class never decides whether a maneuver is safe or desirable: that is
 * entirely the knowledge base's job. It only turns the chosen
 * {@link DrivingDecision} into physical vehicle behaviour, and refuses to
 * contradict a maneuver that is already physically in progress so the vehicle
 * cannot oscillate between lanes.
 */
public class DecisionActuator {
    private final SimulationConfig config;

    public DecisionActuator(SimulationConfig config) {
        this.config = config;
    }

    public void applyDecision(WorldState worldState, DrivingDecision decision) {
        EgoVehicleState ego = worldState.getEgo();

        switch (decision.getAction()) {
            case EMERGENCY_BRAKE -> {
                ego.setAccelerationMps2(-config.getEmergencyDecelerationMps2());
                ego.setIndicatorState(IndicatorState.HAZARD);
            }
            case BRAKE, STOP, WAIT -> ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
            case REDUCE_SPEED -> ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
            case ACCELERATE, RESUME -> ego.setAccelerationMps2(config.getMaxAccelerationMps2());
            case CHANGE_LANE_LEFT -> {
                if (worldState.getMarkingBetween(ego.getLane(), ego.getLane().left()) != RoadMarkingType.CONTINUOUS) {
                    requestLaneChange(ego, ego.getLane().left());
                    ego.setIndicatorState(IndicatorState.LEFT);
                    ego.setSteeringState(SteeringState.LEFT);
                } else {
                    ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
                }
            }
            case CHANGE_LANE_RIGHT, RETURN_RIGHT -> {
                if (worldState.getMarkingBetween(ego.getLane(), ego.getLane().right()) != RoadMarkingType.CONTINUOUS) {
                    requestLaneChange(ego, ego.getLane().right());
                    ego.setIndicatorState(IndicatorState.RIGHT);
                    ego.setSteeringState(SteeringState.RIGHT);
                } else {
                    ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
                }
            }
            case KEEP_LANE -> ego.setAccelerationMps2(0);
            case MAINTAIN_SPEED, CONTINUE -> ego.setAccelerationMps2(0);
        }

        double recommendedSpeed = decision.getRecommendedSpeedKmh();
        if (decision.getAction() != DrivingAction.EMERGENCY_BRAKE && !Double.isNaN(recommendedSpeed)) {
            double current = ego.getSpeedKmh();
            if (current > recommendedSpeed + 1) {
                ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
            } else if (current < recommendedSpeed - 1) {
                double accel = (decision.getAction() == DrivingAction.RESUME || decision.getAction() == DrivingAction.ACCELERATE || current < 5.0)
                    ? config.getMaxAccelerationMps2()
                    : config.getMaxAccelerationMps2() * 0.5;
                ego.setAccelerationMps2(accel);
            } else if (decision.getAction() == DrivingAction.MAINTAIN_SPEED || decision.getAction() == DrivingAction.CONTINUE) {
                ego.setAccelerationMps2(0);
            }
        }

        if (decision.getAction() == DrivingAction.STOP || decision.getAction() == DrivingAction.WAIT) {
            if (ego.getSpeedMps() < 2.0) {
                ego.setSpeedMps(0);
                ego.setAccelerationMps2(0);
            }
        }

        // Maintain turn signal and steering indicator while lane change is in progress
        if (ego.getLaneChangeState() == LaneChangeState.REQUESTED || ego.getLaneChangeState() == LaneChangeState.CHANGING) {
            if (ego.getTargetLane().centreMeters() < ego.getLateralPositionMeters()) {
                ego.setIndicatorState(IndicatorState.LEFT);
                ego.setSteeringState(SteeringState.LEFT);
            } else if (ego.getTargetLane().centreMeters() > ego.getLateralPositionMeters()) {
                ego.setIndicatorState(IndicatorState.RIGHT);
                ego.setSteeringState(SteeringState.RIGHT);
            }
        } else if (ego.getLaneChangeState() == LaneChangeState.COMPLETED || ego.getLaneChangeState() == LaneChangeState.IDLE) {
            if (decision.getAction() != DrivingAction.EMERGENCY_BRAKE) {
                ego.setIndicatorState(IndicatorState.OFF);
            }
            ego.setSteeringState(SteeringState.STRAIGHT);
        }

        ego.updateDrivingState(decision.getAction(), recommendedSpeed);
    }

    /**
     * Starts a lane change toward {@code target}.
     *
     * <p>If a maneuver is already physically moving the vehicle keeps its current
     * target instead of reversing. That is an execution-lifecycle guard only:
     * whether a new maneuver is warranted is still decided by Drools on the next
     * cycle, and emergency decisions can always interrupt by braking.
     *
     * @return true when this call started a new maneuver
     */
    public boolean requestLaneChange(EgoVehicleState ego, Lane target) {
        if (ego.getLaneChangeState() == LaneChangeState.CHANGING
            || ego.getLaneChangeState() == LaneChangeState.COMPLETED) {
            return false;
        }
        if (target == ego.getLane() && ego.getLaneChangeState() != LaneChangeState.REQUESTED) {
            return false;
        }
        ego.setTargetLane(target);
        ego.setLaneChangeOriginLateralMeters(ego.getLateralPositionMeters());
        ego.setLaneChangeProgress(0.0);
        ego.setLaneChangeState(LaneChangeState.REQUESTED);
        return true;
    }
}
