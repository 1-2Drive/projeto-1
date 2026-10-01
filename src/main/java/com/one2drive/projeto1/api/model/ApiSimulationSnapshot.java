package com.one2drive.projeto1.api.model;

import java.util.List;
import java.util.Map;

public record ApiSimulationSnapshot(
    long simulationTimeMillis,
    String status,
    EgoVehicleView egoVehicle,
    EnvironmentView environment,
    SpeedAdvisorView speedAdvisor,
    ApiDecisionView currentDecision,
    Map<String, String> sensorReadings,
    List<ApiRoadElement> worldObjects,
    List<ApiWorkingMemoryRow> workingMemory,
    List<ApiTimelineEntry> recentTimeline,
    List<String> recentRules,
    List<String> recentFacts,
    String activeStage,
    int tickMillis,
    List<String> detectedObjectIds,
    List<String> cameraDetectedObjectIds,
    List<String> radarDetectedObjectIds,
    SensorRangesView sensorRanges,
    List<DemoPhaseView> demoPhases,
    ManeuverView maneuver,
    SensorTelemetryView sensors,
    InferenceSummaryView inference
) {
    public record EgoVehicleView(
        double speedKmh,
        double positionMeters,
        String lane,
        double accelerationMps2,
        double lateralPositionMeters,
        String targetLane,
        String laneChangeState,
        double laneChangeProgress
    ) {
    }

    /** Physical execution state of the maneuver Drools selected. */
    public record ManeuverView(
        String state,
        String fromLane,
        String targetLane,
        double progress,
        double lateralPositionMeters,
        boolean indicatorsLeft,
        boolean indicatorsRight,
        boolean braking
    ) {
    }

    public record RadarTelemetryView(
        double rangeMeters,
        String targetId,
        double distanceMeters,
        double relativeSpeedKmh,
        double ttcSeconds,
        String status
    ) {
    }

    public record CameraDetectionView(String objectId, String label, double distanceMeters, String lane) {
    }

    public record CameraTelemetryView(double rangeMeters, List<CameraDetectionView> detections, String status) {
    }

    public record BlindSpotTelemetryView(
        double rangeMeters,
        boolean occupied,
        String targetId,
        double distanceMeters,
        String status
    ) {
    }

    public record EnvironmentTelemetryView(
        String condition,
        double effectiveRangeMeters,
        double gripPercent,
        boolean rain
    ) {
    }

    public record SensorTelemetryView(
        RadarTelemetryView frontRadar,
        CameraTelemetryView frontCamera,
        BlindSpotTelemetryView blindSpotLeft,
        BlindSpotTelemetryView blindSpotRight,
        EnvironmentTelemetryView environment
    ) {
    }

    /**
     * Presentation summary of the latest inference cycle. It restates evidence the
     * engine already produced and never derives a conclusion of its own.
     */
    public record InferenceSummaryView(
        String previousDecision,
        long decisionChangedAtMillis,
        List<String> situationSteps,
        ImportantEventView lastImportantEvent,
        String activeRule
    ) {
    }

    public record ImportantEventView(long timestampMillis, String category, String title, String detail) {
    }

    public record EnvironmentView(String weatherCondition, double visibilityFactor, double gripFactor) {
    }

    public record SpeedAdvisorView(double currentKmh, double legalLimitKmh, double recommendedKmh, String action) {
    }

    /**
     * Configured detection ranges, published so the UI can draw sensor zones from
     * the same configuration the backend uses instead of duplicating constants.
     */
    public record SensorRangesView(
        double cameraRangeMeters,
        double radarRangeMeters,
        double blindSpotRangeMeters,
        long cameraRateMillis,
        long radarRateMillis
    ) {
    }

    public record DemoPhaseView(String id, String label, String status) {
    }
}
