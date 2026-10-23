package com.one2drive.projeto1.simulation;

public interface SimulationClock {
    long nowMillis();

    void advanceBy(long millis);

    void reset();

    boolean isPseudo();
}
