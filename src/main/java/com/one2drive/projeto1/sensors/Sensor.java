package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.simulation.WorldState;

import java.util.List;

public interface Sensor<T> {
    String id();

    long updateRateMillis();

    double rangeMeters();

    List<T> observe(WorldState worldState, long timestampMillis);
}
