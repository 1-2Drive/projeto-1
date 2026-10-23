package com.one2drive.projeto1.domain;

/**
 * Road lanes and their physical centre lines. {@code centreMeters} is the
 * authoritative lateral coordinate used by the vehicle model and the road view;
 * the enum only names which centre a lane corresponds to.
 */
public enum Lane {
    LEFT(-3.5),
    CENTER(0.0),
    RIGHT(3.5),
    ALL(Double.NaN);

    /** Metres from the road centre line. Negative is left, positive is right. */
    private final double centreMeters;

    Lane(double centreMeters) {
        this.centreMeters = centreMeters;
    }

    public double centreMeters() {
        return centreMeters;
    }

    /** The lane whose centre line is immediately to the left of this one. */
    public Lane left() {
        return switch (this) {
            case LEFT -> LEFT;
            case CENTER -> LEFT;
            case RIGHT -> CENTER;
            case ALL -> ALL;
        };
    }

    /** The lane whose centre line is immediately to the right of this one. */
    public Lane right() {
        return switch (this) {
            case LEFT -> CENTER;
            case CENTER -> RIGHT;
            case RIGHT -> RIGHT;
            case ALL -> ALL;
        };
    }

    /** The lane containing the given lateral offset, used to derive committed lane from physical position. */
    public static Lane fromLateralPosition(double lateralPositionMeters) {
        return lateralPositionMeters < -1.75 ? LEFT : lateralPositionMeters > 1.75 ? RIGHT : CENTER;
    }

    public boolean isPhysicalLane() {
        return this != ALL;
    }
}
