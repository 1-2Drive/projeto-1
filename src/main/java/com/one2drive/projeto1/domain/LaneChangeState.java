package com.one2drive.projeto1.domain;

/**
 * Execution lifecycle of a lateral maneuver.
 *
 * <p>Drools decides <em>whether</em> a maneuver should happen. This enum only
 * describes how far the physical execution of that decision has progressed, so
 * the vehicle never teleports between lanes and never oscillates.
 */
public enum LaneChangeState {
    /** Settled in a lane, no maneuver requested. */
    IDLE,
    /** Drools requested a lane change; execution has not started moving yet. */
    REQUESTED,
    /** The vehicle is physically moving between lane centres. */
    CHANGING,
    /** The target lane centre was reached; settles back to IDLE. */
    COMPLETED;

    public boolean isActive() {
        return this == REQUESTED || this == CHANGING;
    }
}