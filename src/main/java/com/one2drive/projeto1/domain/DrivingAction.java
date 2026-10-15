package com.one2drive.projeto1.domain;

public enum DrivingAction {
    CONTINUE,
    MAINTAIN_SPEED,
    ACCELERATE,
    REDUCE_SPEED,
    BRAKE,
    EMERGENCY_BRAKE,
    STOP,
    WAIT,
    KEEP_LANE,
    CHANGE_LANE_LEFT,
    CHANGE_LANE_RIGHT,
    RETURN_RIGHT,
    RESUME;

    /** The vehicle control this action drives; proposals are arbitrated per channel. */
    public DecisionChannel channel() {
        return switch (this) {
            case KEEP_LANE, CHANGE_LANE_LEFT, CHANGE_LANE_RIGHT, RETURN_RIGHT -> DecisionChannel.LATERAL;
            default -> DecisionChannel.LONGITUDINAL;
        };
    }

    /** A lateral action that moves the vehicle to another lane. */
    public boolean isLaneManeuver() {
        return channel() == DecisionChannel.LATERAL && this != KEEP_LANE;
    }

    /** A longitudinal action that may hold or lower the speed but never raise it. */
    public boolean isBraking() {
        return this == BRAKE || this == STOP || this == WAIT || this == EMERGENCY_BRAKE;
    }
}
