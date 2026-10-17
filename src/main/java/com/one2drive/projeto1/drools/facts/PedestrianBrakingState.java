package com.one2drive.projeto1.drools.facts;

/**
 * Where the vehicle is in its approach to a pedestrian ahead (pedestrian.drl). Braking
 * starts above the 2.0 m/s2 curve and only ends below the 1.4 m/s2 curve; from then on
 * the vehicle holds its speed, so it never speeds up again towards the pedestrian, until
 * the curve catches it and it brakes again, or the approach is over. Without this memory
 * the vehicle rode the upper curve, a brake and a hold alternating at every tick.
 */
public class PedestrianBrakingState {
    public enum Phase {
        /** No approach in progress: the curves alone decide. */
        NONE,
        /** Braking since the upper curve was crossed, until the lower one is. */
        BRAKING,
        /** Under the lower curve after braking: holding the speed. */
        HOLDING
    }

    private Phase phase;
    private long timestamp;

    public PedestrianBrakingState(Phase phase, long timestamp) {
        this.phase = phase;
        this.timestamp = timestamp;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public boolean isBraking() {
        return phase == Phase.BRAKING;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "PedestrianBrakingState{phase=" + phase + "}";
    }
}
