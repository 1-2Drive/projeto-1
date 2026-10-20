package com.one2drive.projeto1.decision;

public final class DecisionPriority {
    public static final int COMFORT_PROGRESS = 10;
    public static final int SPEED_COMPLIANCE = 20;
    public static final int MANEUVER_SAFETY = 30;
    public static final int ROAD_HAZARD = 40;
    public static final int COLLISION_AVOIDANCE = 50;
    public static final int LEGAL_MANDATORY_STOP = 60;
    public static final int EMERGENCY_SAFETY = 70;

    private DecisionPriority() {
    }

    /** Emergencies and mandatory stops: they override every other speed proposal. */
    public static boolean isOverride(int priority) {
        return priority >= LEGAL_MANDATORY_STOP;
    }

    /** Speed ceilings: every one that holds binds, so the lowest decides. */
    public static boolean isSpeedCeiling(int priority) {
        return priority == COLLISION_AVOIDANCE || priority == ROAD_HAZARD || priority == SPEED_COMPLIANCE;
    }

    /** The rung a priority belongs to, in words, for the explanations. */
    public static String rungName(int priority) {
        return switch (priority) {
            case EMERGENCY_SAFETY -> "emergency safety";
            case LEGAL_MANDATORY_STOP -> "legal mandatory stop";
            case COLLISION_AVOIDANCE -> "collision avoidance";
            case ROAD_HAZARD -> "road hazard";
            case MANEUVER_SAFETY -> "maneuver safety";
            case SPEED_COMPLIANCE -> "speed compliance";
            case COMFORT_PROGRESS -> "comfort progress";
            default -> priority <= 0 ? "default" : "priority " + priority;
        };
    }
}
