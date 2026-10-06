package com.one2drive.projeto1.domain;

import java.util.UUID;

public abstract class RoadElement {
    private String id;
    private double positionMeters;
    private Lane lane;
    private boolean active;

    protected RoadElement(double positionMeters, Lane lane) {
        this.id = UUID.randomUUID().toString();
        this.positionMeters = positionMeters;
        this.lane = lane;
        this.active = true;
    }

    public String getId() {
        return id;
    }

    /** Lets the road map name an element after the cell it occupies. */
    public void setId(String id) {
        this.id = id;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "', positionMeters=" + positionMeters +
            ", lane=" + lane + ", active=" + active + "}";
    }
}
