package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DrivingState;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.IndicatorState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RoadMarkingType;
import com.one2drive.projeto1.domain.SteeringState;

/**
 * Executes the decision the rules selected: the speed decision and the lane decision.
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

    /**
     * The gap the following rules keep behind a vehicle ahead ("Keep safe following distance
     * behind front vehicle", front-vehicle.drl): the ego must still be able to slow to that
     * vehicle's speed this far behind it, braking at the anticipation deceleration.
     */
    private static final double FOLLOWING_GAP_METERS = 12.0;

    public DecisionActuator(SimulationConfig config) {
        this.config = config;
    }

    public void applyDecision(WorldState worldState, DrivingDecision decision) {
        EgoVehicleState ego = worldState.getEgo();

        applyLaneDecision(worldState, decision);

        DrivingAction action = decision.getSpeedAction();
        switch (action) {
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
            default -> ego.setAccelerationMps2(0);
        }

        double recommendedSpeed = decision.getRecommendedSpeedKmh();
        boolean mandatoryStop = decision.getPriority() >= com.one2drive.projeto1.decision.DecisionPriority.LEGAL_MANDATORY_STOP;
        if (mandatoryStop) {
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
        if (action != DrivingAction.EMERGENCY_BRAKE && !Double.isNaN(recommendedSpeed)) {
            double current = ego.getSpeedKmh();
            if (current > recommendedSpeed + SPEED_DEAD_BAND_KMH) {
                if (current > recommendedSpeed + 2.0) {
                    ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
                } else {
                    ego.setAccelerationMps2(-Math.max(1.0, (current - recommendedSpeed) * 1.5));
                }
            } else if (current < recommendedSpeed - SPEED_DEAD_BAND_KMH) {
                if (action.isBraking()) {
                    // A braking decision may hold the speed but never raise it, whatever its target.
                    ego.setAccelerationMps2(0);
                } else {
                    double accel = (action == DrivingAction.RESUME || action == DrivingAction.ACCELERATE || current < 5.0)
                        ? config.getMaxAccelerationMps2()
                        : config.getMaxAccelerationMps2() * 0.5;
                    double dt = config.getTickMillis() / 1000.0;
                    double maxAccel = ((recommendedSpeed / 3.6) - ego.getSpeedMps()) / Math.max(dt, 0.01);
                    ego.setAccelerationMps2(Math.max(0.0, Math.min(accel, maxAccel)));
                }
            } else {
                // Inside the dead band: hold the recommended speed instead of hunting around it.
                // A braking decision may hold but never raise the speed.
                ego.setSpeedKmh(action.isBraking() ? Math.min(current, recommendedSpeed) : recommendedSpeed);
                ego.setAccelerationMps2(0);
            }
        }

        if (action == DrivingAction.STOP || action == DrivingAction.WAIT) {
            if (ego.getSpeedMps() < 2.0) {
                ego.setSpeedMps(0);
                ego.setAccelerationMps2(0);
            }
        }

        preserveIndicatorsAndState(ego, decision);
    }

    /**
     * Executes the lane decision. A maneuver already under way is left to run; a new one
     * starts only if the target lane can be entered, no continuous line is crossed and the
     * ego would not end up closer to the vehicle ahead in that lane than it can follow,
     * otherwise the decision records the refusal and the vehicle keeps its lane. The
     * speed decision applies either way: a lane change runs at whatever speed the speed
     * channel decided.
     */
    private void applyLaneDecision(WorldState worldState, DrivingDecision decision) {
        EgoVehicleState ego = worldState.getEgo();
        DrivingAction lane = decision.getLaneAction();
        if (!lane.isLaneManeuver() || ego.getLaneChangeState() != LaneChangeState.IDLE) {
            return;
        }
        Lane target = switch (lane) {
            case CHANGE_LANE_LEFT -> ego.getLane().left();
            case CHANGE_LANE_RIGHT -> ego.getLane().right();
            case RETURN_RIGHT -> ego.getLane() == Lane.LEFT ? Lane.CENTER : Lane.RIGHT;
            default -> ego.getLane();
        };
        if (target == ego.getLane()) {
            rejectLane(ego, decision, "already in the " + target + " lane");
            return;
        }
        if (!worldState.isLaneSafe(target, config.getBlindSpotRangeMeters(), 60.0)) {
            rejectLane(ego, decision, "target lane " + target + " is not safe to enter");
            return;
        }
        if (worldState.getMarkingBetween(ego.getLane(), target) == RoadMarkingType.CONTINUOUS) {
            rejectLane(ego, decision, "a continuous line forbids crossing into " + target);
            return;
        }
        String tooClose = tooCloseToFollow(worldState, target);
        if (tooClose != null) {
            rejectLane(ego, decision, tooClose);
            return;
        }
        requestLaneChange(ego, target);
        boolean left = target.centreMeters() < ego.getLane().centreMeters();
        ego.setIndicatorState(left ? IndicatorState.LEFT : IndicatorState.RIGHT);
        ego.setSteeringState(left ? SteeringState.LEFT : SteeringState.RIGHT);
    }

    /**
     * Why the ego cannot move into {@code target} behind the nearest vehicle ahead there, or
     * null if it can. The lane change takes {@link SimulationConfig#getLaneChangeSeconds()},
     * during which a slower vehicle draws closer; at the end the ego must still be able to
     * slow to its speed {@value #FOLLOWING_GAP_METERS} m behind it, braking at
     * {@link StoppingPolicy#ANTICIPATION_DECELERATION_MPS2}, as the following rules require.
     */
    private String tooCloseToFollow(WorldState worldState, Lane target) {
        EgoVehicleState ego = worldState.getEgo();
        return worldState.nearestFrontVehicleInLane(target, Double.MAX_VALUE)
            .map(vehicle -> {
                double gap = worldState.vehicleSignedDistance(vehicle);
                double egoMps = ego.getSpeedMps();
                double frontMps = Math.max(0.0, vehicle.getSpeedMps());
                double gapAfterManeuver = gap - Math.max(0.0, egoMps - frontMps) * config.getLaneChangeSeconds();
                double room = Math.max(0.0, gapAfterManeuver - FOLLOWING_GAP_METERS);
                double followableMps = Math.sqrt(frontMps * frontMps
                    + 2.0 * StoppingPolicy.ANTICIPATION_DECELERATION_MPS2 * room);
                return egoMps > followableMps
                    ? String.format(java.util.Locale.ROOT,
                        "vehicle %.0f m ahead in %s at %.0f km/h is too close to follow safely", gap, target, frontMps * 3.6)
                    : null;
            })
            .orElse(null);
    }

    /**
     * A maneuver the world does not allow is refused, and unless the speed channel is
     * already slowing the vehicle down it eases off as the safe reaction to a plan that
     * could not be executed. The rules decide again next cycle with the same facts.
     */
    private static void rejectLane(EgoVehicleState ego, DrivingDecision decision, String detail) {
        decision.rejectLane(detail);
        DrivingAction speed = decision.getSpeedAction();
        if (!speed.isBraking() && speed != DrivingAction.REDUCE_SPEED) {
            decision.apply(DrivingAction.REDUCE_SPEED, Math.max(0.0, ego.getSpeedKmh() - 10.0),
                com.one2drive.projeto1.domain.RiskLevel.HIGH,
                com.one2drive.projeto1.decision.DecisionPriority.COLLISION_AVOIDANCE,
                "Lane-change command rejected: " + detail,
                "Actuator safety guard",
                decision.getTimestamp(),
                java.util.List.of(detail));
        }
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
            if (decision.getSpeedAction() != DrivingAction.EMERGENCY_BRAKE) {
                ego.setIndicatorState(IndicatorState.OFF);
            }
            ego.setSteeringState(SteeringState.STRAIGHT);
        }

        ego.updateDrivingState(decision.getSpeedAction(), recommendedSpeed);
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
