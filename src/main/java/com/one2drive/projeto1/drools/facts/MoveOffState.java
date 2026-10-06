package com.one2drive.projeto1.drools.facts;

/**
 * Whether the vehicle is moving off after a standstill. It is a fact rather than a
 * per-cycle condition so the intent survives cycles in which another proposal wins
 * briefly, such as a driver lane change, and only ends when a hazard or hold wins or
 * the target speed is reached.
 */
public class MoveOffState {
    private boolean active;
    private long timestamp;

    public MoveOffState(boolean active, long timestamp) {
        this.active = active;
        this.timestamp = timestamp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "MoveOffState{active=" + active + "}";
    }
}
