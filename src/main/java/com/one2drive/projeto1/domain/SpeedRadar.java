package com.one2drive.projeto1.domain;

/**
 * A fixed roadside or overhead speed radar / camera infrastructure device.
 * It monitors vehicle speed against a legal limit. It is NOT a collision hazard
 * or obstacle and must never trigger avoidance lane changes or emergency stops.
 */
public final class SpeedRadar extends RoadElement {
    private double speedLimitKmh;

    public SpeedRadar(double positionMeters, Lane ignoredLane, double speedLimitKmh) {
        super(positionMeters, null);
        this.speedLimitKmh = speedLimitKmh;
    }

    public SpeedRadar(double positionMeters, double speedLimitKmh) {
        this(positionMeters, null, speedLimitKmh);
    }

    public double getSpeedLimitKmh() {
        return speedLimitKmh;
    }

    public void setSpeedLimitKmh(double speedLimitKmh) {
        this.speedLimitKmh = speedLimitKmh;
    }

}
