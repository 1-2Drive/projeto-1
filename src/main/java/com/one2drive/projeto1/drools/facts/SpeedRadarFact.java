package com.one2drive.projeto1.drools.facts;

/**
 * Tracks the state of the nearest oncoming speed radar infrastructure object in working memory.
 */
public class SpeedRadarFact {
    private String radarId;
    private double distanceMeters;
    private double speedLimitKmh;
    private long timestamp;

    public SpeedRadarFact(String radarId, double distanceMeters, double speedLimitKmh, long timestamp) {
        this.radarId = radarId;
        this.distanceMeters = distanceMeters;
        this.speedLimitKmh = speedLimitKmh;
        this.timestamp = timestamp;
    }

    public String getRadarId() {
        return radarId;
    }

    public void setRadarId(String radarId) {
        this.radarId = radarId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public double getSpeedLimitKmh() {
        return speedLimitKmh;
    }

    public void setSpeedLimitKmh(double speedLimitKmh) {
        this.speedLimitKmh = speedLimitKmh;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "SpeedRadarFact{radarId='" + radarId + "', distanceMeters=" + distanceMeters +
            ", speedLimitKmh=" + speedLimitKmh + "}";
    }
}
