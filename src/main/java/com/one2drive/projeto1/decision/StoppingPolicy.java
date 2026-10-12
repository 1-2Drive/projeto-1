package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.config.SimulationConfig;

/**
 * Distances the rules use to bring the vehicle to rest before a stop line. The rules decide
 * what to do; this only keeps the figures and the arithmetic in one place.
 */
public final class StoppingPolicy {
    /** Deceleration a mandatory stop is executed with: the vehicle's comfortable braking. */
    public static final double COMFORT_DECELERATION_MPS2 =
        SimulationConfig.defaultConfig().getComfortableDecelerationMps2();

    /** Gentler deceleration from which the vehicle stops gaining speed towards a stop. */
    public static final double ANTICIPATION_DECELERATION_MPS2 = 2.0;

    /**
     * Time that passes between the world changing and the brakes answering: one camera
     * reading plus the cycles it takes the decision to reach the actuator.
     */
    public static final double REACTION_TIME_SECONDS = 0.5;

    /**
     * Where the vehicle comes to rest before a red traffic light, measured from the light to
     * the vehicle's reference point. There is no explicit stop line in the world, so this is
     * it: it leaves the whole vehicle, drawn around that point, short of the light.
     */
    public static final double RED_LIGHT_STOP_LINE_OFFSET_METERS = 18.0;

    /** How far short of the stop line a stopped vehicle is still considered to be at it. */
    public static final double STOP_LINE_TOLERANCE_METERS = 3.0;

    private StoppingPolicy() {
    }

    /**
     * Distance needed to come to rest from {@code speedKmh}: the ground covered while
     * reacting, plus the braking distance at {@code decelerationMps2}, plus the buffer that
     * must be left at the end.
     */
    public static double requiredDistanceMeters(double speedKmh, double decelerationMps2, double stopBufferMeters) {
        double speedMps = Math.max(0.0, speedKmh) / 3.6;
        return speedMps * REACTION_TIME_SECONDS
            + speedMps * speedMps / (2.0 * decelerationMps2)
            + stopBufferMeters;
    }

    /** Braking distance alone, with no reaction time and no buffer. */
    public static double brakingDistanceMeters(double speedKmh, double decelerationMps2) {
        double speedMps = Math.max(0.0, speedKmh) / 3.6;
        return speedMps * speedMps / (2.0 * decelerationMps2);
    }
}
