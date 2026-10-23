package com.one2drive.projeto1.domain;

public class LaneChangeRequest {
    private final LaneChangeDirection direction;
    private boolean active;

    public LaneChangeRequest(LaneChangeDirection direction) {
        this.direction = direction;
        this.active = true;
    }

    public LaneChangeDirection getDirection() {
        return direction;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
