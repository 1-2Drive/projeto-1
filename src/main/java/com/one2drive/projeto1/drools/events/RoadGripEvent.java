package com.one2drive.projeto1.drools.events;

public class RoadGripEvent extends BaseEvent {
    private final double gripFactor;

    public RoadGripEvent(long timestamp, String sourceId, double gripFactor) {
        super(timestamp, sourceId);
        this.gripFactor = gripFactor;
    }

    public double getGripFactor() {
        return gripFactor;
    }

    @Override
    public String toString() {
        return "RoadGripEvent{gripFactor=" + gripFactor + "}";
    }
}
