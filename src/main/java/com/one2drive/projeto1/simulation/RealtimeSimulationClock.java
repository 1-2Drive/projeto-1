package com.one2drive.projeto1.simulation;

public class RealtimeSimulationClock implements SimulationClock {
    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public void advanceBy(long millis) {
        // no-op for realtime clock
    }

    @Override
    public boolean isPseudo() {
        return false;
    }
}
