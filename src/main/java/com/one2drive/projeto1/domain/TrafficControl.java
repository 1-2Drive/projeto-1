package com.one2drive.projeto1.domain;

public abstract class TrafficControl extends RoadElement {
    protected TrafficControl(double positionMeters, Lane lane) {
        super(positionMeters, lane);
    }
}
