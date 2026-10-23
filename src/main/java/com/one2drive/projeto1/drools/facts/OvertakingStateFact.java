package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.Lane;

/**
 * Tracks an active overtaking or passing maneuver.
 * When the vehicle changes lane to pass an obstacle or slower vehicle, this fact tracks
 * the original lane until the target is passed and it is safe to return to the right.
 * It also keeps the id of the vehicle being passed: after the lane change the front radar
 * may be looking at a different vehicle, and the two must not be confused.
 */
public class OvertakingStateFact {
    public static final String NO_TARGET = "none";

    private boolean active;
    private Lane originalLane;
    private boolean passedTarget;
    private long timestamp;
    private String targetId = NO_TARGET;

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

    /** Id of the vehicle this overtake is passing, or {@link #NO_TARGET}. */
    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId == null ? NO_TARGET : targetId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "OvertakingStateFact{active=" + active + ", originalLane=" + originalLane + ", passedTarget=" + passedTarget + ", targetId='" + targetId + "'}";
    }
}
