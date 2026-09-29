package com.one2drive.projeto1.drools.facts;

public class ProcessedEvent {
    private final Object event;
    private final long timestamp;

    public ProcessedEvent(Object event, long timestamp) {
        this.event = event;
        this.timestamp = timestamp;
    }

    public Object getEvent() {
        return event;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
