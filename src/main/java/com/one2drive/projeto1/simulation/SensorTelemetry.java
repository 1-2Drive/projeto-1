package com.one2drive.projeto1.simulation;

import java.util.List;

/**
 * Structured view of what the sensors currently report, built from the real event
 * objects the sensor manager produced during the tick. It carries measured values
 * only; it does not derive any vehicle behaviour.
 */
public record SensorTelemetry(
    RadarView frontRadar,
    CameraView frontCamera,
    BlindSpotView blindSpotLeft,
    BlindSpotView blindSpotRight,
    EnvironmentView environment
) {

    /** {@code status} is a descriptive presentation mapping of the measured values. */
    public record RadarView(
        double rangeMeters,
        String targetId,
        double distanceMeters,
        double relativeSpeedKmh,
        double ttcSeconds,
        String status,
        double speedRadarDistanceMeters,
        double speedRadarLimitKmh
    ) {
        public static RadarView empty(double rangeMeters) {
            return new RadarView(rangeMeters, "none", Double.POSITIVE_INFINITY, 0.0, Double.POSITIVE_INFINITY, "CLEAR", Double.POSITIVE_INFINITY, 0.0);
        }
    }

    /**
     * One object the front camera currently perceives. {@code lane} lets the UI show
     * which lane the object sits in, which is what makes avoidance legible.
     */
    public record CameraDetection(String objectId, String label, double distanceMeters, String lane, String source) {
    }

    public record CameraView(double rangeMeters, List<CameraDetection> detections, String status) {
        public static CameraView empty(double rangeMeters) {
            return new CameraView(rangeMeters, List.of(), "CLEAR");
        }
    }

    public record BlindSpotView(double rangeMeters, boolean occupied, String targetId, double distanceMeters, String status) {
        public static BlindSpotView empty(double rangeMeters) {
            return new BlindSpotView(rangeMeters, false, "none", Double.POSITIVE_INFINITY, "CLEAR");
        }
    }

    /**
     * @param effectiveRangeMeters visibility scaled against the configured camera range
     * @param gripPercent          road grip expressed as a percentage of nominal
     */
    public record EnvironmentView(String condition, double effectiveRangeMeters, double gripPercent, boolean rain) {
    }

    public static SensorTelemetry empty(SimulationConfigView config) {
        return new SensorTelemetry(
            RadarView.empty(config.radarRangeMeters()),
            CameraView.empty(config.cameraRangeMeters()),
            BlindSpotView.empty(config.blindSpotRangeMeters()),
            BlindSpotView.empty(config.blindSpotRangeMeters()),
            new EnvironmentView("CLEAR", config.cameraRangeMeters(), 100.0, false)
        );
    }

    /** Minimal projection of the configuration needed to describe empty telemetry. */
    public record SimulationConfigView(
        double radarRangeMeters,
        double cameraRangeMeters,
        double blindSpotRangeMeters
    ) {
    }
}
