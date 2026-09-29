package com.one2drive.projeto1.drools.events;

public class ReducedVisibilityEvent extends BaseEvent {
    private final double visibilityFactor;

    public ReducedVisibilityEvent(long timestamp, String sourceId, double visibilityFactor) {
        super(timestamp, sourceId);
        this.visibilityFactor = visibilityFactor;
    }

    public double getVisibilityFactor() {
        return visibilityFactor;
    }

    @Override
    public String toString() {
        return "ReducedVisibilityEvent{visibilityFactor=" + visibilityFactor + "}";
    }
}
