package com.one2drive.projeto1.history;

import java.time.Instant;

/** An ordered, simulation-clock event; wall-clock time is retained for audit correlation. */
public record SessionEvent(long sequence, long simulationTimeMillis, Instant recordedAt,
                           String type, String message) {
}
