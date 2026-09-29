package com.one2drive.projeto1.drools.events;

public class TTCEvent extends BaseEvent {
    private final double ttcSeconds;
    private final String targetId;

    public TTCEvent(long timestamp, String sourceId, double ttcSeconds, String targetId) {
        super(timestamp, sourceId);
        this.ttcSeconds = ttcSeconds;
        this.targetId = targetId;
    }

    public double getTtcSeconds() {
        return ttcSeconds;
    }

    public String getTargetId() {
        return targetId;
    }

    @Override
    public String toString() {
        return "TTCEvent{ttcSeconds=" + ttcSeconds + ", targetId='" + targetId + "'}";
    }
}
