package com.one2drive.projeto1.inference;

import java.util.Locale;

/** The rule engines the simulation can decide with. Only one is active at a time. */
public enum ExpertBackendType {
    DROOLS("Drools"),
    PROLOG("SWI-Prolog");

    private final String label;

    ExpertBackendType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts the enum name in any case, e.g. "prolog" from a URL or a property. */
    public static ExpertBackendType parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
