package com.one2drive.projeto1.drools.events;

public class ReducedGripEvent extends BaseEvent {
    private final double gripFactor;

    public ReducedGripEvent(long timestamp, String sourceId, double gripFactor) {
        super(timestamp, sourceId);
        this.gripFactor = gripFactor;
    }

    public double getGripFactor() {
        return gripFactor;
    }

    @Override
    public String toString() {
        return "ReducedGripEvent{gripFactor=" + gripFactor + "}";
    }
}
