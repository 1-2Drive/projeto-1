package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.LaneChangeDirection;

public class LaneChangeRequestEvent extends BaseEvent {
    private final LaneChangeDirection direction;

    public LaneChangeRequestEvent(long timestamp, String sourceId, LaneChangeDirection direction) {
        super(timestamp, sourceId);
        this.direction = direction;
    }

    public LaneChangeDirection getDirection() {
        return direction;
    }

    @Override
    public String toString() {
        return "LaneChangeRequestEvent{direction=" + direction + "}";
    }
}
