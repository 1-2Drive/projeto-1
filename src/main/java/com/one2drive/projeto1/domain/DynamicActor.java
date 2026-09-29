package com.one2drive.projeto1.domain;

import java.util.UUID;

public abstract class DynamicActor {
    private final String id;
    private double positionMeters;
    private Lane lane;
    private double speedMps;
    private boolean active;

    protected DynamicActor(double positionMeters, Lane lane, double speedMps) {
        this.id = UUID.randomUUID().toString();
        this.positionMeters = positionMeters;
        this.lane = lane;
        this.speedMps = speedMps;
        this.active = true;
    }

    public String getId() {
        return id;
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

    public double getSpeedMps() {
        return speedMps;
    }

    public void setSpeedMps(double speedMps) {
        this.speedMps = speedMps;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void advance(double dtSeconds) {
        this.positionMeters += this.speedMps * dtSeconds;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "', positionMeters=" + positionMeters +
            ", lane=" + lane + ", speedMps=" + speedMps + ", active=" + active + "}";
    }
}
