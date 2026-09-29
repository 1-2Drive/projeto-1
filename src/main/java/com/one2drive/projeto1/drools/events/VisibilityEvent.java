package com.one2drive.projeto1.drools.events;

public class VisibilityEvent extends BaseEvent {
    private final double visibilityFactor;

    public VisibilityEvent(long timestamp, String sourceId, double visibilityFactor) {
        super(timestamp, sourceId);
        this.visibilityFactor = visibilityFactor;
    }

    public double getVisibilityFactor() {
        return visibilityFactor;
    }

    @Override
    public String toString() {
        return "VisibilityEvent{visibilityFactor=" + visibilityFactor + "}";
    }
}
