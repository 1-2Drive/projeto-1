package com.one2drive.projeto1.drools.events;

/**
 * Derived event: the instant the vehicle came to rest at a stop sign's line. The rules insert
 * it, and the 1.2 s full-stop hold is then read with Allen's {@code after} against the speed
 * readings that follow, instead of a start time kept on the stop sign state.
 */
public class StoppedAtStopLineEvent extends BaseEvent {
    private final String signId;

    public StoppedAtStopLineEvent(long timestamp, String signId) {
        super(timestamp, "rules");
        this.signId = signId;
    }

    public String getSignId() {
        return signId;
    }

    @Override
    public String toString() {
        return "StoppedAtStopLineEvent{signId=" + signId + ", timestamp=" + getTimestamp() + "}";
    }
}
