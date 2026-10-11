package com.one2drive.projeto1.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EgoVehicleStateTest {

    private static EgoVehicleState movingAt(double speedKmh, double accelerationMps2) {
        return new EgoVehicleState(0.0, speedKmh / 3.6, accelerationMps2, Lane.RIGHT);
    }

    @Test
    void reduceSpeedWhileAcceleratingReportsAccelerating() {
        EgoVehicleState ego = movingAt(50.7, 0.8);
        ego.updateDrivingState(DrivingAction.REDUCE_SPEED, 52.0);
        assertEquals(DrivingState.ACCELERATING, ego.getDrivingState());
    }

    @Test
    void reduceSpeedWhileHoldingReportsCruising() {
        EgoVehicleState ego = movingAt(52.0, 0.0);
        ego.updateDrivingState(DrivingAction.REDUCE_SPEED, 52.0);
        assertEquals(DrivingState.CRUISING, ego.getDrivingState());
    }

    @Test
    void reduceSpeedWhileBrakingHardReportsBraking() {
        EgoVehicleState ego = movingAt(56.0, -2.8);
        ego.updateDrivingState(DrivingAction.REDUCE_SPEED, 52.0);
        assertEquals(DrivingState.BRAKING, ego.getDrivingState());
    }

    @Test
    void reduceSpeedWhileEasingOffReportsDecelerating() {
        EgoVehicleState ego = movingAt(53.0, -0.6);
        ego.updateDrivingState(DrivingAction.REDUCE_SPEED, 52.0);
        assertEquals(DrivingState.DECELERATING, ego.getDrivingState());
    }

    @Test
    void brakeActionAlwaysReportsBraking() {
        EgoVehicleState ego = movingAt(30.0, 0.0);
        ego.updateDrivingState(DrivingAction.BRAKE, 20.0);
        assertEquals(DrivingState.BRAKING, ego.getDrivingState());
    }

    @Test
    void stoppedVehicleStaysStopped() {
        EgoVehicleState ego = movingAt(0.0, 0.0);
        ego.updateDrivingState(DrivingAction.MAINTAIN_SPEED, 0.0);
        assertEquals(DrivingState.STOPPED, ego.getDrivingState());
    }
}
