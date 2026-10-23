package com.one2drive.projeto1.sensors;

public abstract class AbstractSensor<T> implements Sensor<T> {
    private final String id;
    private final long updateRateMillis;
    private final double rangeMeters;

    protected AbstractSensor(String id, long updateRateMillis, double rangeMeters) {
        this.id = id;
        this.updateRateMillis = updateRateMillis;
        this.rangeMeters = rangeMeters;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public long updateRateMillis() {
        return updateRateMillis;
    }

    @Override
    public double rangeMeters() {
        return rangeMeters;
    }
}
