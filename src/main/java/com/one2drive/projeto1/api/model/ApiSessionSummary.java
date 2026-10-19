package com.one2drive.projeto1.api.model;

import java.time.Instant;

public record ApiSessionSummary(String id, Instant startedAt, Instant stoppedAt, long durationMillis,
                                long startedAtSimulationMillis,
                                String scenarioId, String backend, int eventCount, int decisionCount) { }
