package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.DrivingAction;

/**
 * What a speed decision does to the vehicle: speed it up, hold its speed, or slow it down.
 * The action alone does not say it: MAINTAIN_SPEED towards a target above the current speed
 * builds the speed up, and a REDUCE_SPEED already at its target holds it. This follows how
 * {@code DecisionActuator.applyDecision} turns the action, its priority and its target into
 * an acceleration, so "why not speed up?" is answered against what the actuator would do.
 */
public enum SpeedIntent {
    INCREASE,
    HOLD,
    REDUCE;

    /** Half-width of the band around the recommended speed in which the actuator just holds it. */
    public static final double DEAD_BAND_KMH = 0.5;
    /** Below this speed a stopping decision holds the vehicle stopped. */
    public static final double STANDSTILL_KMH = 0.6;

    public static SpeedIntent of(DrivingAction action, int priority, double targetKmh, double currentKmh) {
        SpeedIntent intent = DecisionPriority.isOverride(priority)
            ? underMandatoryStop(action, targetKmh, currentKmh)
            : tracking(action, targetKmh, currentKmh);
        // A stopped vehicle cannot slow down any further: the decision holds it.
        return intent == REDUCE && currentKmh < STANDSTILL_KMH ? HOLD : intent;
    }

    private static SpeedIntent underMandatoryStop(DrivingAction action, double targetKmh, double currentKmh) {
        return switch (action) {
            case STOP, WAIT, REDUCE_SPEED, EMERGENCY_BRAKE, BRAKE -> REDUCE;
            default -> !Double.isNaN(targetKmh) && currentKmh > targetKmh + DEAD_BAND_KMH
                ? REDUCE : base(action, targetKmh);
        };
    }

    private static SpeedIntent tracking(DrivingAction action, double targetKmh, double currentKmh) {
        if (action == DrivingAction.EMERGENCY_BRAKE || Double.isNaN(targetKmh)) {
            return base(action, targetKmh);
        }
        if (currentKmh > targetKmh + DEAD_BAND_KMH) {
            return REDUCE;
        }
        if (currentKmh < targetKmh - DEAD_BAND_KMH) {
            // A braking decision may hold the speed but never raise it, whatever its target.
            return action.isBraking() ? HOLD : INCREASE;
        }
        return HOLD;
    }

    /** The acceleration the action sets before the speed is tracked towards a target. */
    private static SpeedIntent base(DrivingAction action, double targetKmh) {
        return switch (action) {
            case EMERGENCY_BRAKE, BRAKE, STOP, WAIT -> REDUCE;
            case REDUCE_SPEED -> Double.isNaN(targetKmh) ? REDUCE : HOLD;
            case ACCELERATE, RESUME -> INCREASE;
            default -> HOLD;
        };
    }
}
