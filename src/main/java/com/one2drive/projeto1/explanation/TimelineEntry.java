package com.one2drive.projeto1.explanation;

public class TimelineEntry {
    private final long timestamp;
    private final TimelineEntryType type;
    private final String message;

    public TimelineEntry(long timestamp, TimelineEntryType type, String message) {
        this.timestamp = timestamp;
        this.type = type;
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public TimelineEntryType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return timestamp + " " + type + " " + message;
    }
}
