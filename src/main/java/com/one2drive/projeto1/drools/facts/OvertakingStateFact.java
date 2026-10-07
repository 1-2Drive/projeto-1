package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

/**
 * Tracks an active overtaking or passing maneuver.
 * When the vehicle changes lane to pass an obstacle or slower vehicle, this fact tracks
 * the original lane until the target is passed and it is safe to return to the right.
 */
public class OvertakingStateFact {
    private boolean active;
    private Lane originalLane;
    private boolean passedTarget;
    private long timestamp;

    public OvertakingStateFact(boolean active, Lane originalLane, boolean passedTarget, long timestamp) {
        this.active = active;
        this.originalLane = originalLane;
        this.passedTarget = passedTarget;
        this.timestamp = timestamp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Lane getOriginalLane() {
        return originalLane;
    }

    public void setOriginalLane(Lane originalLane) {
        this.originalLane = originalLane;
    }

    public boolean isPassedTarget() {
        return passedTarget;
    }

    public void setPassedTarget(boolean passedTarget) {
        this.passedTarget = passedTarget;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "OvertakingStateFact{active=" + active + ", originalLane=" + originalLane + ", passedTarget=" + passedTarget + "}";
    }
}
