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

    /**
     * Where the stop line of a stop sign lies, measured back from the sign. The vehicle is
     * drawn around its reference point, so stopping at the sign itself leaves the sign under
     * the car; this keeps the nose of the car just short of it.
     */
    public static final double STOP_SIGN_OFFSET_METERS = 15.0;

    /** Stretch before a speed limit sign over which the vehicle already holds the new limit. */
    public static final double SPEED_LIMIT_APPROACH_BUFFER_METERS = 8.0;

    /**
     * The vehicle comes to rest this far before an obstacle in its lane: outside the 8 m
     * emergency zone, so it can still steer away once a lane opens.
     */
    public static final double OBSTACLE_STOP_OFFSET_METERS = 10.0;

    /**
     * The vehicle comes to rest this far before a closed barrier in its path, as before an
     * obstacle: outside the emergency zone, so it can still steer away once a lane opens.
     */
    public static final double BARRIER_STOP_OFFSET_METERS = 10.0;

    /** Speed a pothole is driven over at when it cannot be gone round (road-hazards.drl). */
    public static final double POTHOLE_CROSSING_SPEED_KMH = 30.0;

    /** How long a fixed hazard is along the road: one road block (RoadMap default). */
    public static final double HAZARD_LENGTH_METERS = 5.0;

    /**
     * The gap the following rules keep behind a vehicle ahead ("Keep safe following distance
     * behind front vehicle", front-vehicle.drl).
     */
    public static final double FOLLOWING_GAP_METERS = 12.0;

    /**
     * How long the rules assume a lane change takes. SimulationConfig.laneChangeSeconds, the
     * time the actuator really takes, must not exceed it.
     */
    public static final double LANE_CHANGE_SECONDS = 3.0;

    /**
     * Time from the start of a lane change until the vehicle is clear of the lane it leaves:
     * two thirds of {@link #LANE_CHANGE_SECONDS}, when the eased lateral motion
     * (WorldPhysicsEngine.advanceLaneChange) has covered three quarters of the way to the
     * next lane.
     */
    public static final double LANE_EXIT_SECONDS = 2.0;

    private StoppingPolicy() {
    }

    /**
     * Whether a lane change started now takes the vehicle out of its lane before it reaches
     * a hazard {@code distanceMeters} ahead: the ground covered while reacting and until the
     * vehicle is clear of the lane, plus the hazard's own length. Closer than that the
     * manoeuvre would carry the vehicle over the hazard, so it is not a way past it.
     */
    public static boolean roomToLeaveLane(double speedKmh, double distanceMeters) {
        double speedMps = Math.max(0.0, speedKmh) / 3.6;
        return distanceMeters >= speedMps * (REACTION_TIME_SECONDS + LANE_EXIT_SECONDS) + HAZARD_LENGTH_METERS;
    }

    /**
     * Fastest speed from which slowing at {@code decelerationMps2} over {@code roomMeters}
     * ends at {@code targetKmh}: the braking curve the approach rules hold the vehicle on.
     */
    public static double curveSpeedKmh(double targetKmh, double decelerationMps2, double roomMeters) {
        double targetMps = Math.max(0.0, targetKmh) / 3.6;
        return Math.sqrt(targetMps * targetMps + 2.0 * decelerationMps2 * Math.max(0.0, roomMeters)) * 3.6;
    }

    /**
     * Distance needed to slow from {@code speedKmh} to {@code targetKmh}: the ground covered
     * while reacting, plus the braking at {@code decelerationMps2}. Nothing when already at
     * or under the target.
     */
    public static double slowingDistanceMeters(double speedKmh, double targetKmh, double decelerationMps2) {
        double speedMps = Math.max(0.0, speedKmh) / 3.6;
        double targetMps = Math.min(speedMps, Math.max(0.0, targetKmh) / 3.6);
        return speedMps * REACTION_TIME_SECONDS
            + (speedMps * speedMps - targetMps * targetMps) / (2.0 * decelerationMps2);
    }

    /**
     * Ground the ego gains on a vehicle ahead while it sheds a closing speed of
     * {@code relativeSpeedMps} at {@code decelerationMps2}, reaction time included. Larger
     * than the gap, comfortable braking would carry the ego past that vehicle.
     */
    public static double closingDistanceMeters(double relativeSpeedMps, double decelerationMps2) {
        double closing = Math.max(0.0, relativeSpeedMps);
        return closing * REACTION_TIME_SECONDS + closing * closing / (2.0 * decelerationMps2);
    }

    /**
     * Whether the ego, moving into the lane of a vehicle {@code gapMeters} ahead that it is
     * closing in on at {@code relativeSpeedMps}, can still follow that vehicle: during the
     * lane change the gap shrinks, and at its end the ego must be able to slow to the
     * vehicle's speed {@link #FOLLOWING_GAP_METERS} behind it, braking at the anticipation
     * deceleration. With no vehicle (an infinite gap) there is always room.
     */
    public static boolean roomToFollow(double egoSpeedKmh, double gapMeters, double relativeSpeedMps) {
        double egoMps = Math.max(0.0, egoSpeedKmh) / 3.6;
        double frontMps = Math.max(0.0, egoMps - relativeSpeedMps);
        double gapAfterManeuver = gapMeters - Math.max(0.0, relativeSpeedMps) * LANE_CHANGE_SECONDS;
        double room = Math.max(0.0, gapAfterManeuver - FOLLOWING_GAP_METERS);
        return egoMps <= Math.sqrt(frontMps * frontMps + 2.0 * ANTICIPATION_DECELERATION_MPS2 * room);
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

    /**
     * Fastest the vehicle may still be going {@code distanceMeters} before a sign that posts
     * {@code limitKmh}, so that slowing gently from there brings it to the limit
     * {@link #SPEED_LIMIT_APPROACH_BUFFER_METERS} before the sign. Far from the sign this is
     * well above any speed, so nothing slows down early.
     */
    public static double approachSpeedKmh(double limitKmh, double distanceMeters) {
        double limitMps = Math.max(0.0, limitKmh) / 3.6;
        double room = Math.max(0.0, distanceMeters - SPEED_LIMIT_APPROACH_BUFFER_METERS);
        return Math.sqrt(limitMps * limitMps + 2.0 * ANTICIPATION_DECELERATION_MPS2 * room) * 3.6;
    }

    /** Braking distance alone, with no reaction time and no buffer. */
    public static double brakingDistanceMeters(double speedKmh, double decelerationMps2) {
        double speedMps = Math.max(0.0, speedKmh) / 3.6;
        return speedMps * speedMps / (2.0 * decelerationMps2);
    }
}
