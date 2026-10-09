package com.one2drive.projeto1.drools.facts;

/**
 * Tracks the state of the nearest oncoming speed radar infrastructure object in working memory.
 */
public class SpeedRadarFact {
    private String radarId;
    private double distanceMeters;
    private double speedLimitKmh;
    private long timestamp;
    private boolean passed;
    private boolean detected;

    public SpeedRadarFact(String radarId, double distanceMeters, double speedLimitKmh, long timestamp) {
        this(radarId, distanceMeters, speedLimitKmh, timestamp, false, false);
    }

    public SpeedRadarFact(String radarId, double distanceMeters, double speedLimitKmh, long timestamp, boolean passed) {
        this(radarId, distanceMeters, speedLimitKmh, timestamp, passed, false);
    }

    public SpeedRadarFact(String radarId, double distanceMeters, double speedLimitKmh, long timestamp,
                          boolean passed, boolean detected) {
        this.radarId = radarId;
        this.distanceMeters = distanceMeters;
        this.speedLimitKmh = speedLimitKmh;
        this.timestamp = timestamp;
        this.passed = passed;
        this.detected = detected;
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

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public boolean isDetected() { return detected; }
    public void setDetected(boolean detected) { this.detected = detected; }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "SpeedRadarFact{radarId='" + radarId + "', distanceMeters=" + distanceMeters +
            ", speedLimitKmh=" + speedLimitKmh + ", detected=" + detected + ", passed=" + passed + "}";
    }
}
