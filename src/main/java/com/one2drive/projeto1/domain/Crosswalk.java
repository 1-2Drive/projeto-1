package com.one2drive.projeto1.domain;

/**
 * A pedestrian crossing painted across the whole road. It is part of the road, not of
 * the pedestrian: it stays where it is after the pedestrian has crossed and is passed
 * like any other road element. Like a speed limit sign it occupies one map cell but
 * binds every lane, so the camera reports the next one ahead whatever lane it is in.
 */
public final class Crosswalk extends RoadElement {
    public Crosswalk(double positionMeters, Lane lane) {
        super(positionMeters, lane);
    }
}
