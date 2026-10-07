package com.one2drive.projeto1.domain;

/**
 * A fixed roadside or overhead speed radar / camera infrastructure device.
 * It monitors vehicle speed against a legal limit. It is NOT a collision hazard
 * or obstacle and must never trigger avoidance lane changes or emergency stops.
 */
public class SpeedRadar extends RoadElement {
    private double speedLimitKmh;
    private boolean passedRecorded;

    public SpeedRadar(double positionMeters, Lane lane, double speedLimitKmh) {
        super(positionMeters, lane);
        this.speedLimitKmh = speedLimitKmh;
        this.passedRecorded = false;
    }

    public double getSpeedLimitKmh() {
        return speedLimitKmh;
    }

    public void setSpeedLimitKmh(double speedLimitKmh) {
        this.speedLimitKmh = speedLimitKmh;
    }

    public boolean isPassedRecorded() {
        return passedRecorded;
    }

    public void setPassedRecorded(boolean passedRecorded) {
        this.passedRecorded = passedRecorded;
    }
}
