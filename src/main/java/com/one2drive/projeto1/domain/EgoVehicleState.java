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
}