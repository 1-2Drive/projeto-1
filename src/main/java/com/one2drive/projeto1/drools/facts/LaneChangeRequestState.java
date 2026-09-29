package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.LaneChangeDirection;

public class LaneChangeRequestState {
    private boolean active;
    private LaneChangeDirection direction;
    private long timestamp;

    public LaneChangeRequestState(boolean active, LaneChangeDirection direction, long timestamp) {
        this.active = active;
        this.direction = direction;
        this.timestamp = timestamp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LaneChangeDirection getDirection() {
        return direction;
    }

    public void setDirection(LaneChangeDirection direction) {
        this.direction = direction;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "LaneChangeRequestState{active=" + active + ", direction=" + direction + "}";
    }
}
