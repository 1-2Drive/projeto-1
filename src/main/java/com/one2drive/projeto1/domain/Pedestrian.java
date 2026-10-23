package com.one2drive.projeto1.domain;

public class Pedestrian extends DynamicActor {
    private double lateralPositionMeters;
    private double lateralSpeedMps;
    private double targetLateralMeters;
    private PedestrianCrossingState crossingState;
    private boolean crossing;

    public Pedestrian(double positionMeters, Lane lane, double speedMps) {
        super(positionMeters, lane, 0.0);
        this.lateralPositionMeters = lane != null ? lane.centreMeters() : 0.0;
        this.lateralSpeedMps = Math.abs(speedMps);
        // Cross the whole road to the far side: from the left lane towards the right
        // sidewalk, from the centre or right lane towards the left sidewalk. Always heading
        // left made a pedestrian in the left lane step straight off the road, so it could
        // never cross towards a vehicle in the other lanes.
        this.targetLateralMeters = lane == Lane.LEFT ? 5.5 : -5.5;
        this.crossingState = Math.abs(speedMps) > 0.01 ? PedestrianCrossingState.CROSSING : PedestrianCrossingState.WAITING;
        this.crossing = Math.abs(speedMps) > 0.01;
    }

    public Pedestrian(double positionMeters, double initialLateralMeters, double targetLateralMeters, double walkingSpeedMps) {
        super(positionMeters, Lane.fromLateralPosition(initialLateralMeters), 0.0);
        this.lateralPositionMeters = initialLateralMeters;
        this.targetLateralMeters = targetLateralMeters;
        this.lateralSpeedMps = Math.abs(walkingSpeedMps);
        this.crossingState = PedestrianCrossingState.CROSSING;
        this.crossing = true;
    }

    public double getLateralPositionMeters() {
        return lateralPositionMeters;
    }

    public void setLateralPositionMeters(double lateralPositionMeters) {
        this.lateralPositionMeters = lateralPositionMeters;
        setLane(Lane.fromLateralPosition(lateralPositionMeters));
    }

    public double getLateralSpeedMps() {
        return lateralSpeedMps;
    }

    public void setLateralSpeedMps(double lateralSpeedMps) {
        this.lateralSpeedMps = lateralSpeedMps;
    }

    public double getTargetLateralMeters() {
        return targetLateralMeters;
    }

    public void setTargetLateralMeters(double targetLateralMeters) {
        this.targetLateralMeters = targetLateralMeters;
    }

    public PedestrianCrossingState getCrossingState() {
        return crossingState;
    }

    public void setCrossingState(PedestrianCrossingState crossingState) {
        this.crossingState = crossingState;
    }

    public boolean isCrossing() {
        return crossing;
    }

    public void setCrossing(boolean crossing) {
        this.crossing = crossing;
    }

    /** Walking across the road and not yet at the far side: the crossing is occupied. */
    public boolean isCrossingInProgress() {
        return crossing && crossingState == PedestrianCrossingState.CROSSING;
    }

    public boolean isInRoadway() {
        return crossingState != PedestrianCrossingState.CROSSED && Math.abs(lateralPositionMeters) <= 5.25;
    }

    @Override
    public void advance(double dtSeconds) {
        if (!isActive() || !crossing || crossingState != PedestrianCrossingState.CROSSING) {
            return;
        }

        double diff = targetLateralMeters - lateralPositionMeters;
        double step = Math.copySign(Math.min(Math.abs(diff), lateralSpeedMps * dtSeconds), diff);
        lateralPositionMeters += step;
        setLane(Lane.fromLateralPosition(lateralPositionMeters));

        if (Math.abs(targetLateralMeters - lateralPositionMeters) < 0.05) {
            lateralPositionMeters = targetLateralMeters;
            crossingState = PedestrianCrossingState.CROSSED;
            crossing = false;
        }
    }

    @Override
    public String toString() {
        return "Pedestrian{id='" + getId() + "', positionMeters=" + getPositionMeters()
            + ", lateralPositionMeters=" + lateralPositionMeters
            + ", lane=" + getLane()
            + ", crossingState=" + crossingState + ", active=" + isActive() + "}";
    }
}
