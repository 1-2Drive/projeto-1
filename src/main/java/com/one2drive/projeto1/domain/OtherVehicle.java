package com.one2drive.projeto1.domain;

public class OtherVehicle extends DynamicActor {
    /** The speed the vehicle drives at when nothing makes it slow down. */
    private double cruiseSpeedMps;

    public OtherVehicle(double positionMeters, Lane lane, double speedMps) {
        super(positionMeters, lane, speedMps);
        this.cruiseSpeedMps = speedMps;
    }

    /** A speed set from outside (scenario, API) is also the speed the vehicle keeps to. */
    @Override
    public void setSpeedMps(double speedMps) {
        super.setSpeedMps(speedMps);
        this.cruiseSpeedMps = speedMps;
    }

    public double getCruiseSpeedMps() {
        return cruiseSpeedMps;
    }

    /** The speed the vehicle is going at right now, as the world physics changes it. */
    public void setCurrentSpeedMps(double speedMps) {
        super.setSpeedMps(speedMps);
    }
}
