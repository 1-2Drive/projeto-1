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

    /** Half-width of the band around the recommended speed in which the car just holds it. */
    private static final double SPEED_DEAD_BAND_KMH = 0.5;

    public DecisionActuator(SimulationConfig config) {
        this.config = config;
    }

    public void applyDecision(WorldState worldState, DrivingDecision decision) {
        EgoVehicleState ego = worldState.getEgo();

        if (decision.getAction() == DrivingAction.CHANGE_LANE_LEFT
            || decision.getAction() == DrivingAction.CHANGE_LANE_RIGHT
            || decision.getAction() == DrivingAction.RETURN_RIGHT) {
            Lane target = switch (decision.getAction()) {
                case CHANGE_LANE_LEFT -> ego.getLane().left();
                case CHANGE_LANE_RIGHT -> ego.getLane().right();
                case RETURN_RIGHT -> ego.getLane() == Lane.LEFT ? Lane.CENTER : Lane.RIGHT;
                default -> ego.getLane();
            };
            if (!worldState.isLaneSafe(target, config.getBlindSpotRangeMeters(), 60.0)) {
                decision.apply(DrivingAction.REDUCE_SPEED, Math.max(0.0, ego.getSpeedKmh() - 10.0),
                    com.one2drive.projeto1.domain.RiskLevel.HIGH,
                    com.one2drive.projeto1.decision.DecisionPriority.COLLISION_AVOIDANCE,
                    "Lane-change command rejected: target lane is occupied or unsafe",
                    "Actuator safety guard",
                    decision.getTimestamp(),
                    java.util.List.of("target lane=" + target + " is not safe to enter"));
                ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
                ego.setIndicatorState(IndicatorState.OFF);
                ego.setSteeringState(SteeringState.STRAIGHT);
                return;
            }
        }

        switch (decision.getAction()) {
            case EMERGENCY_BRAKE -> {
                ego.setAccelerationMps2(-config.getEmergencyDecelerationMps2());
                ego.setIndicatorState(IndicatorState.HAZARD);
            }
            case BRAKE, STOP, WAIT -> ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
            // REDUCE_SPEED without a target is a plain slow-down; with a target the
            // speed-tracking block below decides how hard to brake, or whether to brake at all.
            case REDUCE_SPEED -> ego.setAccelerationMps2(Double.isNaN(decision.getRecommendedSpeedKmh())
                ? -config.getComfortableDecelerationMps2()
                : 0.0);
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
        boolean mandatoryStop = decision.getPriority() >= com.one2drive.projeto1.decision.DecisionPriority.LEGAL_MANDATORY_STOP;
        if (mandatoryStop) {
            DrivingAction action = decision.getAction();
            if (action == DrivingAction.STOP || action == DrivingAction.WAIT || action == DrivingAction.REDUCE_SPEED) {
                ego.setAccelerationMps2(ego.getSpeedKmh() < 0.6 ? 0.0 : -config.getComfortableDecelerationMps2());
            } else if (action == DrivingAction.EMERGENCY_BRAKE) {
                ego.setAccelerationMps2(-config.getEmergencyDecelerationMps2());
            }
            if ((action == DrivingAction.STOP || action == DrivingAction.WAIT) && ego.getSpeedKmh() < 0.6) {
                ego.setSpeedKmh(0.0);
                ego.setAccelerationMps2(0.0);
            }
            preserveIndicatorsAndState(ego, decision);
            return;
        }
        if (decision.getAction() != DrivingAction.EMERGENCY_BRAKE && !Double.isNaN(recommendedSpeed)) {
            double current = ego.getSpeedKmh();
            if (current > recommendedSpeed + SPEED_DEAD_BAND_KMH) {
                if (current > recommendedSpeed + 2.0) {
                    ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
                } else {
                    ego.setAccelerationMps2(-Math.max(1.0, (current - recommendedSpeed) * 1.5));
                }
            } else if (current < recommendedSpeed - SPEED_DEAD_BAND_KMH) {
                double accel = (decision.getAction() == DrivingAction.RESUME || decision.getAction() == DrivingAction.ACCELERATE || current < 5.0)
                    ? config.getMaxAccelerationMps2()
                    : config.getMaxAccelerationMps2() * 0.5;
                double dt = config.getTickMillis() / 1000.0;
                double maxAccel = ((recommendedSpeed / 3.6) - ego.getSpeedMps()) / Math.max(dt, 0.01);
                ego.setAccelerationMps2(Math.max(0.0, Math.min(accel, maxAccel)));
            } else {
                // Inside the dead band: hold the recommended speed instead of hunting around it.
                // A braking decision may hold but never raise the speed.
                boolean braking = decision.getAction() == DrivingAction.BRAKE
                    || decision.getAction() == DrivingAction.STOP
                    || decision.getAction() == DrivingAction.WAIT;
                ego.setSpeedKmh(braking ? Math.min(current, recommendedSpeed) : recommendedSpeed);
                ego.setAccelerationMps2(0);
            }
        }

        if (decision.getAction() == DrivingAction.STOP || decision.getAction() == DrivingAction.WAIT) {
            if (ego.getSpeedMps() < 2.0) {
                ego.setSpeedMps(0);
                ego.setAccelerationMps2(0);
            }
        }

        preserveIndicatorsAndState(ego, decision);
    }

    private static void preserveIndicatorsAndState(EgoVehicleState ego, DrivingDecision decision) {
        double recommendedSpeed = decision.getRecommendedSpeedKmh();
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
