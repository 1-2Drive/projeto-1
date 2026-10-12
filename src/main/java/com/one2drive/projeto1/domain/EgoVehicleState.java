package com.one2drive.projeto1.domain;

/**
 * Longitudinal and lateral state of the ego vehicle.
 *
 * <p>{@code positionMeters} is longitudinal, {@code lateralPositionMeters} is
 * lateral and is the authoritative coordinate the road view renders. The
 * {@code lane} enum names the committed lane centre; it is only used for rule
 * matching and sensing, never as a substitute for lateral position.
 */
public class EgoVehicleState {
    private double positionMeters;
    private double speedMps;
    private double accelerationMps2;
    private Lane lane;
    private double lateralPositionMeters;
    private Lane targetLane;
    private LaneChangeState laneChangeState;
    private double laneChangeProgress;
    private double laneChangeOriginLateralMeters;
    private DrivingState drivingState;
    private IndicatorState indicatorState;
    private SteeringState steeringState;
    private double targetSpeedKmh;
    private double headingDegrees;

    public EgoVehicleState(double positionMeters, double speedMps, double accelerationMps2, Lane lane) {
        this.positionMeters = positionMeters;
        this.speedMps = speedMps;
        this.accelerationMps2 = accelerationMps2;
        this.lane = lane;
        this.lateralPositionMeters = lane.centreMeters();
        this.targetLane = lane;
        this.laneChangeState = LaneChangeState.IDLE;
        this.laneChangeProgress = 0.0;
        this.laneChangeOriginLateralMeters = this.lateralPositionMeters;
        this.drivingState = speedMps > 0.1 ? DrivingState.CRUISING : DrivingState.STOPPED;
        this.indicatorState = IndicatorState.OFF;
        this.steeringState = SteeringState.STRAIGHT;
        this.targetSpeedKmh = speedMps * 3.6;
        this.headingDegrees = 0.0;
    }

    public double getPositionMeters() {
        return positionMeters;
    }

    public void setPositionMeters(double positionMeters) {
        this.positionMeters = positionMeters;
    }

    public double getSpeedMps() {
        return speedMps;
    }

    public void setSpeedMps(double speedMps) {
        this.speedMps = speedMps;
    }

    public double getAccelerationMps2() {
        return accelerationMps2;
    }

    public void setAccelerationMps2(double accelerationMps2) {
        this.accelerationMps2 = accelerationMps2;
    }

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public double getLateralPositionMeters() {
        return lateralPositionMeters;
    }

    public void setLateralPositionMeters(double lateralPositionMeters) {
        this.lateralPositionMeters = lateralPositionMeters;
    }

    public Lane getTargetLane() {
        return targetLane;
    }

    public void setTargetLane(Lane targetLane) {
        this.targetLane = targetLane;
    }

    public LaneChangeState getLaneChangeState() {
        return laneChangeState;
    }

    public void setLaneChangeState(LaneChangeState laneChangeState) {
        this.laneChangeState = laneChangeState;
    }

    /** 0 at the start of the maneuver, 1 when the target lane centre is reached. */
    public double getLaneChangeProgress() {
        return laneChangeProgress;
    }

    public void setLaneChangeProgress(double laneChangeProgress) {
        this.laneChangeProgress = laneChangeProgress;
    }

    public double getLaneChangeOriginLateralMeters() {
        return laneChangeOriginLateralMeters;
    }

    public void setLaneChangeOriginLateralMeters(double laneChangeOriginLateralMeters) {
        this.laneChangeOriginLateralMeters = laneChangeOriginLateralMeters;
    }

    public double getSpeedKmh() {
        return speedMps * 3.6;
    }

    public void setSpeedKmh(double speedKmh) {
        this.speedMps = speedKmh / 3.6;
    }

    public void advance(double dtSeconds) {
        speedMps = Math.max(0, speedMps + accelerationMps2 * dtSeconds);
        positionMeters += speedMps * dtSeconds;
    }

    public DrivingState getDrivingState() {
        return drivingState;
    }

    public void setDrivingState(DrivingState drivingState) {
        this.drivingState = drivingState;
    }

    public IndicatorState getIndicatorState() {
        return indicatorState;
    }

    public void setIndicatorState(IndicatorState indicatorState) {
        this.indicatorState = indicatorState;
    }

    public SteeringState getSteeringState() {
        return steeringState;
    }

    public void setSteeringState(SteeringState steeringState) {
        this.steeringState = steeringState;
    }

    public double getTargetSpeedKmh() {
        return targetSpeedKmh;
    }

    public void setTargetSpeedKmh(double targetSpeedKmh) {
        this.targetSpeedKmh = targetSpeedKmh;
    }

    public double getHeadingDegrees() {
        return headingDegrees;
    }

    public void setHeadingDegrees(double headingDegrees) {
        this.headingDegrees = headingDegrees;
    }

    public void updateDrivingState(DrivingAction action, double recommendedSpeedKmh) {
        if (action == DrivingAction.STOP || action == DrivingAction.EMERGENCY_BRAKE || action == DrivingAction.WAIT) {
            if (speedMps < 0.1) {
                drivingState = DrivingState.STOPPED_FOR_HAZARD;
                return;
            }
        }
        if (speedMps < 0.05) {
            if (action == DrivingAction.ACCELERATE || action == DrivingAction.RESUME) {
                drivingState = DrivingState.RESUMING;
            } else if (drivingState != DrivingState.STOPPED_FOR_HAZARD) {
                drivingState = DrivingState.STOPPED;
            }
            return;
        }

        // Braking actions always read as BRAKING; everything else reports what the
        // car is physically doing, so the HUD never says DECELERATING while it accelerates.
        if (action == DrivingAction.EMERGENCY_BRAKE || action == DrivingAction.BRAKE) {
            drivingState = DrivingState.BRAKING;
        } else if (accelerationMps2 < -1.0) {
            drivingState = DrivingState.BRAKING;
        } else if (accelerationMps2 < -0.2) {
            drivingState = DrivingState.DECELERATING;
        } else if (accelerationMps2 > 0.2 || action == DrivingAction.ACCELERATE || action == DrivingAction.RESUME) {
            drivingState = DrivingState.ACCELERATING;
        } else {
            drivingState = DrivingState.CRUISING;
        }
    }
}