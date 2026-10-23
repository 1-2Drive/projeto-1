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
    private long laneSettledAtMillis = -1L;

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

    /** When the vehicle settled into its current lane (EgoVehicleState), -1 if it never changed lane. */
    public long getLaneSettledAtMillis() {
        return laneSettledAtMillis;
    }

    public void setLaneSettledAtMillis(long laneSettledAtMillis) {
        this.laneSettledAtMillis = laneSettledAtMillis;
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

    /**
     * The lane the vehicle is physically in, from its lateral position. {@link #getLane()}
     * is the committed lane, which only follows a lane change once it settles
     * (SimulationEngine.settleManeuver, after the actuator): during the manoeuvre, and on
     * the tick it completes, the two differ. Rules about what the vehicle is about to hit
     * read this one; rules about the lane it drives in read the committed lane.
     */
    public Lane getPhysicalLane() {
        return Lane.fromLateralPosition(lateralPositionMeters);
    }

    /**
     * The lane the vehicle's path runs along: the target lane while a lane change is moving
     * (and on the tick it completes), the committed lane otherwise. A hazard in this lane is
     * in the vehicle's way whether it is already there or about to arrive, so the rules that
     * approach a hazard read this one; the committed lane only follows the manoeuvre once it
     * settles, and a hazard in the lane being left is no longer in the way.
     */
    public Lane getPathLane() {
        return laneChangeState == LaneChangeState.IDLE ? lane : targetLane;
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
