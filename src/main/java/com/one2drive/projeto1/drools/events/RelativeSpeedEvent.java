package com.one2drive.projeto1.drools.events;

public class RelativeSpeedEvent extends BaseEvent {
    private final double relativeSpeedMps;
    private final String targetId;

    public RelativeSpeedEvent(long timestamp, String sourceId, double relativeSpeedMps, String targetId) {
        super(timestamp, sourceId);
        this.relativeSpeedMps = relativeSpeedMps;
        this.targetId = targetId;
    }

    public double getRelativeSpeedMps() {
        return relativeSpeedMps;
    }

    public String getTargetId() {
        return targetId;
    }

    @Override
    public String toString() {
        return "RelativeSpeedEvent{relativeSpeedMps=" + relativeSpeedMps + ", targetId='" + targetId + "'}";
    }
}
