package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.DrivingState;
import com.one2drive.projeto1.domain.IndicatorState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RoadMarkingType;

public class VehicleStateFact {
    private double speedKmh;
    private double positionMeters;
    private Lane lane;
    private LaneChangeState laneChangeState;
    private Lane targetLane;
    private long timestamp;
    private DrivingState drivingState;
    private IndicatorState indicatorState;
    private double lateralPositionMeters;
    private RoadMarkingType leftMarking;
    private RoadMarkingType rightMarking;
    private boolean rightLaneBlockedAhead;
    private boolean leftAdjacentLaneSafe;
    private boolean rightAdjacentLaneSafe;
    private boolean presentationDemo;

    public VehicleStateFact(double speedKmh, double positionMeters, Lane lane, long timestamp) {
        this.speedKmh = speedKmh;
        this.positionMeters = positionMeters;
        this.lane = lane;
        this.laneChangeState = LaneChangeState.IDLE;
        this.targetLane = lane;
        this.timestamp = timestamp;
        this.drivingState = speedKmh > 0.1 ? DrivingState.CRUISING : DrivingState.STOPPED;
        this.indicatorState = IndicatorState.OFF;
        this.lateralPositionMeters = lane != null ? lane.centreMeters() : 0.0;
        this.leftMarking = RoadMarkingType.DASHED;
        this.rightMarking = RoadMarkingType.DASHED;
        this.rightLaneBlockedAhead = false;
        this.leftAdjacentLaneSafe = false;
        this.rightAdjacentLaneSafe = false;
        this.presentationDemo = false;
    }

    public boolean isPresentationDemo() {
        return presentationDemo;
    }

    public void setPresentationDemo(boolean presentationDemo) {
        this.presentationDemo = presentationDemo;
    }

    public boolean isRightLaneBlockedAhead() {
        return rightLaneBlockedAhead;
    }

    public void setRightLaneBlockedAhead(boolean rightLaneBlockedAhead) {
        this.rightLaneBlockedAhead = rightLaneBlockedAhead;
    }

    public boolean isLeftAdjacentLaneSafe() { return leftAdjacentLaneSafe; }
    public void setLeftAdjacentLaneSafe(boolean safe) { this.leftAdjacentLaneSafe = safe; }
    public boolean isRightAdjacentLaneSafe() { return rightAdjacentLaneSafe; }
    public void setRightAdjacentLaneSafe(boolean safe) { this.rightAdjacentLaneSafe = safe; }

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

    public double getLateralPositionMeters() {
        return lateralPositionMeters;
    }

    public void setLateralPositionMeters(double lateralPositionMeters) {
        this.lateralPositionMeters = lateralPositionMeters;
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

    @Override
    public String toString() {
        return "VehicleStateFact{speedKmh=" + speedKmh +
            ", positionMeters=" + positionMeters +
            ", lane=" + lane +
            ", laneChangeState=" + laneChangeState +
            ", targetLane=" + targetLane +
            ", drivingState=" + drivingState +
            ", indicator=" + indicatorState + "}";
    }
}
