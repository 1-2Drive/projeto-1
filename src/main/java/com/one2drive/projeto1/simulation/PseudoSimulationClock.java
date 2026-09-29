package com.one2drive.projeto1.simulation;

public class PseudoSimulationClock implements SimulationClock {
    private long nowMillis;

    public PseudoSimulationClock(long initialMillis) {
        this.nowMillis = initialMillis;
    }

    @Override
    public long nowMillis() {
        return nowMillis;
    }

    @Override
    public void advanceBy(long millis) {
        this.nowMillis += millis;
    }

    @Override
    public boolean isPseudo() {
        return true;
    }
}
