package com.one2drive.projeto1.domain;

public class EgoVehicleState {
    private double positionMeters;
    private double speedMps;
    private double accelerationMps2;
    private Lane lane;

    public EgoVehicleState(double positionMeters, double speedMps, double accelerationMps2, Lane lane) {
        this.positionMeters = positionMeters;
        this.speedMps = speedMps;
        this.accelerationMps2 = accelerationMps2;
        this.lane = lane;
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
