package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;

public class VehicleStateFact {
    private double speedKmh;
    private double positionMeters;
    private Lane lane;
    private LaneChangeState laneChangeState;
    private Lane targetLane;
    private long timestamp;

    public VehicleStateFact(double speedKmh, double positionMeters, Lane lane, long timestamp) {
        this.speedKmh = speedKmh;
        this.positionMeters = positionMeters;
        this.lane = lane;
        this.laneChangeState = LaneChangeState.IDLE;
        this.targetLane = lane;
        this.timestamp = timestamp;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public double getPositionMeters() {
        return positionMeters;
    }

    public void setPositionMeters(double positionMeters) {
        this.positionMeters = positionMeters;
    }

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public LaneChangeState getLaneChangeState() {
        return laneChangeState;
    }

    public void setLaneChangeState(LaneChangeState laneChangeState) {
        this.laneChangeState = laneChangeState;
    }

    public Lane getTargetLane() {
        return targetLane;
    }

    public void setTargetLane(Lane targetLane) {
        this.targetLane = targetLane;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "VehicleStateFact{speedKmh=" + speedKmh +
            ", positionMeters=" + positionMeters +
            ", lane=" + lane +
            ", laneChangeState=" + laneChangeState +
            ", targetLane=" + targetLane + "}";
    }
}
