package com.one2drive.projeto1.config;

public class SimulationConfig {
    /** Default red time of a traffic light, counted from the moment the vehicle has stopped at it. */
    public static final long RED_HOLD_AFTER_STOP_MS = 5000;

    private final long tickMillis;
    /** Length of one road map block (one matrix row). */
    private final double mapBlockLengthMeters;
    /** Rows of the empty map used when a scenario does not define one. */
    private final int defaultMapRows;
    private final double maxAccelerationMps2;
    private final double comfortableDecelerationMps2;
    private final double emergencyDecelerationMps2;
    private final double defaultCruiseSpeedKmh;
    private final double laneChangeSeconds;
    private final double laneChangeMetersPerSecond;

    private final double radarRangeMeters;
    private final double cameraRangeMeters;
    private final double blindSpotRangeMeters;
    private final double environmentRangeMeters;

    private final long radarRateMillis;
    private final long cameraRateMillis;
    private final long blindSpotRateMillis;
    private final long environmentRateMillis;
    private final long speedSensorRateMillis;

    private final double distanceNoiseStdMeters;

    private final double yellowLightSeconds;
    private final long redHoldAfterStopMillis;

    public SimulationConfig(long tickMillis,
                            double mapBlockLengthMeters,
                            int defaultMapRows,
                            double maxAccelerationMps2,
                            double comfortableDecelerationMps2,
                            double emergencyDecelerationMps2,
                            double defaultCruiseSpeedKmh,
                            double laneChangeSeconds,
                            double laneChangeMetersPerSecond,
                            double radarRangeMeters,
                            double cameraRangeMeters,
                            double blindSpotRangeMeters,
                            double environmentRangeMeters,
                            long radarRateMillis,
                            long cameraRateMillis,
                            long blindSpotRateMillis,
                            long environmentRateMillis,
                            long speedSensorRateMillis,
                            double distanceNoiseStdMeters,
                            double yellowLightSeconds,
                            long redHoldAfterStopMillis) {
        this.tickMillis = tickMillis;
        this.mapBlockLengthMeters = mapBlockLengthMeters;
        this.defaultMapRows = defaultMapRows;
        this.maxAccelerationMps2 = maxAccelerationMps2;
        this.comfortableDecelerationMps2 = comfortableDecelerationMps2;
        this.emergencyDecelerationMps2 = emergencyDecelerationMps2;
        this.defaultCruiseSpeedKmh = defaultCruiseSpeedKmh;
        this.laneChangeSeconds = laneChangeSeconds;
        this.laneChangeMetersPerSecond = laneChangeMetersPerSecond;
        this.radarRangeMeters = radarRangeMeters;
        this.cameraRangeMeters = cameraRangeMeters;
        this.blindSpotRangeMeters = blindSpotRangeMeters;
        this.environmentRangeMeters = environmentRangeMeters;
        this.radarRateMillis = radarRateMillis;
        this.cameraRateMillis = cameraRateMillis;
        this.blindSpotRateMillis = blindSpotRateMillis;
        this.environmentRateMillis = environmentRateMillis;
        this.speedSensorRateMillis = speedSensorRateMillis;
        this.distanceNoiseStdMeters = distanceNoiseStdMeters;
        this.yellowLightSeconds = yellowLightSeconds;
        this.redHoldAfterStopMillis = redHoldAfterStopMillis;
    }

    public static SimulationConfig defaultConfig() {
        return new SimulationConfig(
            100,
            5.0,
            100,
            1.6,
            2.8,
            8.5,
            90,
            3.0,
            1.2,
            130,
            120,
            30,
            200,
            100,
            200,
            200,
            400,
            100,
            0.7,
            3.0,
            RED_HOLD_AFTER_STOP_MS
        );
    }

    public double getLaneChangeSeconds() {
        return laneChangeSeconds;
    }

    public double getLaneChangeMetersPerSecond() {
        return laneChangeMetersPerSecond;
    }

    public long getTickMillis() {
        return tickMillis;
    }

    public double getMapBlockLengthMeters() {
        return mapBlockLengthMeters;
    }

    public int getDefaultMapRows() {
        return defaultMapRows;
    }

    public double getMaxAccelerationMps2() {
        return maxAccelerationMps2;
    }

    public double getComfortableDecelerationMps2() {
        return comfortableDecelerationMps2;
    }

    public double getEmergencyDecelerationMps2() {
        return emergencyDecelerationMps2;
    }

    public double getDefaultCruiseSpeedKmh() {
        return defaultCruiseSpeedKmh;
    }

    public double getRadarRangeMeters() {
        return radarRangeMeters;
    }

    public double getCameraRangeMeters() {
        return cameraRangeMeters;
    }

    public double getBlindSpotRangeMeters() {
        return blindSpotRangeMeters;
    }

    public double getEnvironmentRangeMeters() {
        return environmentRangeMeters;
    }

    public long getRadarRateMillis() {
        return radarRateMillis;
    }

    public long getCameraRateMillis() {
        return cameraRateMillis;
    }

    public long getBlindSpotRateMillis() {
        return blindSpotRateMillis;
    }

    public long getEnvironmentRateMillis() {
        return environmentRateMillis;
    }

    public long getSpeedSensorRateMillis() {
        return speedSensorRateMillis;
    }

    public double getDistanceNoiseStdMeters() {
        return distanceNoiseStdMeters;
    }

    /** How long a traffic light stays yellow before it turns red. */
    public double getYellowLightSeconds() {
        return yellowLightSeconds;
    }

    /**
     * How long a traffic light that came from yellow stays red once the vehicle is at rest
     * at its stop line. The time the vehicle takes to get there does not count.
     */
    public long getRedHoldAfterStopMillis() {
        return redHoldAfterStopMillis;
    }
}
