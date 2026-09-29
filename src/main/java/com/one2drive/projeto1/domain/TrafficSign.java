package com.one2drive.projeto1.domain;

public abstract class TrafficSign extends RoadElement {
    protected TrafficSign(double positionMeters, Lane lane) {
        super(positionMeters, lane);
    }
}
