package com.one2drive.projeto1.inference;

import com.one2drive.projeto1.simulation.WorldState;

/** What a backend may read about the world when it refreshes the vehicle state for a cycle. */
public record DecisionInput(WorldState world, long timestampMillis, String scenarioId) {
}
