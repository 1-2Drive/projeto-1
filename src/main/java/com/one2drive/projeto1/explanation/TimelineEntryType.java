package com.one2drive.projeto1.explanation;

public enum TimelineEntryType {
    SENSOR,
    /** Significant perception transition: object detected, lost, or changed range state. */
    PERCEPTION_CHANGE,
    RULE_ACTIVATED,
    RULE_CANCELLED,
    RULE_BEFORE_FIRE,
    RULE_FIRED,
    FACT_INSERTED,
    FACT_UPDATED,
    FACT_DELETED,
    INFO,
    DECISION,
    ACTUATOR,
    /** Maneuver lifecycle: requested, started, completed. */
    MANEUVER
}
