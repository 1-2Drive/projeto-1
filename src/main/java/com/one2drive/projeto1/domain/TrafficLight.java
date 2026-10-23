package com.one2drive.projeto1.domain;

public class TrafficLight extends TrafficControl {
    private TrafficLightState state;
    private boolean cycling;
    private long phaseElapsedMillis;

    public TrafficLight(double positionMeters, Lane lane, TrafficLightState state) {
        super(positionMeters, lane);
        setState(state);
    }

    public TrafficLightState getState() {
        return state;
    }

    /**
     * Shows the given state from now on. A yellow light turns red when the yellow time is
     * over; a red light, whether it came from yellow or was set red, turns green once the
     * vehicle has waited at it for the red time. A green light stays green until it is changed.
     */
    public void setState(TrafficLightState state) {
        this.state = state;
        this.phaseElapsedMillis = 0L;
        this.cycling = state != TrafficLightState.GREEN;
    }

    /** Whether this light is on its way to green: yellow or red. */
    public boolean isCycling() {
        return cycling;
    }

    /**
     * Time counted in the current phase. In yellow it is the time since the light turned
     * yellow. In red it is the time the vehicle has been at rest at the stop line: zero
     * while the vehicle is still approaching or braking.
     */
    public long getPhaseElapsedMillis() {
        return phaseElapsedMillis;
    }

    /**
     * Lets simulated time pass for the cycle. The durations belong to the simulation, not to
     * the light.
     *
     * @param vehicleStoppedAtLine whether the vehicle is at rest at this light's stop line;
     *                             the red time only counts while it is
     * @return whether the light changed state
     */
    public boolean advance(long dtMillis, long yellowMillis, long redHoldAfterStopMillis, boolean vehicleStoppedAtLine) {
        if (!cycling) {
            return false;
        }
        if (state == TrafficLightState.YELLOW) {
            phaseElapsedMillis += dtMillis;
            if (phaseElapsedMillis >= yellowMillis) {
                state = TrafficLightState.RED;
                phaseElapsedMillis = 0L;
                return true;
            }
            return false;
        }
        if (state == TrafficLightState.RED) {
            // A vehicle that has not stopped yet, or that moved again, has not waited at all.
            phaseElapsedMillis = vehicleStoppedAtLine ? phaseElapsedMillis + dtMillis : 0L;
            if (phaseElapsedMillis >= redHoldAfterStopMillis) {
                state = TrafficLightState.GREEN;
                phaseElapsedMillis = 0L;
                cycling = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + " state=" + state;
    }
}
