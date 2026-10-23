package com.one2drive.projeto1.domain;

/**
 * State machine representing the vehicle's driving behavior lifecycle.
 */
public enum DrivingState {
    STOPPED,
    ACCELERATING,
    CRUISING,
    DECELERATING,
    BRAKING,
    STOPPED_FOR_HAZARD,
    RESUMING;

    public boolean isMoving() {
        return this == ACCELERATING || this == CRUISING || this == DECELERATING || this == BRAKING || this == RESUMING;
    }

    public boolean isStopped() {
        return this == STOPPED || this == STOPPED_FOR_HAZARD;
    }
}
