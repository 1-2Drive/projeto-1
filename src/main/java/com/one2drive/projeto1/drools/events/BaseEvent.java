package com.one2drive.projeto1.drools.events;

public abstract class BaseEvent {
    private final long timestamp;
    private final String sourceId;

    protected BaseEvent(long timestamp, String sourceId) {
        this.timestamp = timestamp;
        this.sourceId = sourceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSourceId() {
        return sourceId;
    }
}
