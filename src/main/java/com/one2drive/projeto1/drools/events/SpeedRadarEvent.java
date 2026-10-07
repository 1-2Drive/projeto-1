package com.one2drive.projeto1.drools.events;

/**
 * Perception event emitted when the vehicle's sensors detect an oncoming speed radar infrastructure object.
 */
public class SpeedRadarEvent {
    private final long timestamp;
    private final String radarId;
    private final double distanceMeters;
    private final double speedLimitKmh;

    public SpeedRadarEvent(long timestamp, String radarId, double distanceMeters, double speedLimitKmh) {
        this.timestamp = timestamp;
        this.radarId = radarId;
        this.distanceMeters = distanceMeters;
        this.speedLimitKmh = speedLimitKmh;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getRadarId() {
        return radarId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public double getSpeedLimitKmh() {
        return speedLimitKmh;
    }
}
