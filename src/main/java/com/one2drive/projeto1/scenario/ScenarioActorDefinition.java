package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.domain.Lane;

public class ScenarioActorDefinition {
    private ScenarioActorType type;
    private double positionMeters;
    private Lane lane;
    private double speedKmh;

    public ScenarioActorType getType() {
        return type;
    }

    public void setType(ScenarioActorType type) {
        this.type = type;
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

    public double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(double speedKmh) {
        this.speedKmh = speedKmh;
    }
}
