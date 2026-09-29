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
}
