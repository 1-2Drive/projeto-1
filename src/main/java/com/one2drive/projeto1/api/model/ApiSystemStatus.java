package com.one2drive.projeto1.api.model;

/**
 * {@code drools} keeps its name for older clients: it reports whether the active inference
 * engine, whichever it is, is ACTIVE. {@code expertBackend} names that engine.
 */
public record ApiSystemStatus(
    String backend,
    String drools,
    String simulation,
    String expertBackend
) {
}
