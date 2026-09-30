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
    List<DemoPhaseView> demoPhases
) {
    public record EgoVehicleView(double speedKmh, double positionMeters, String lane, double accelerationMps2) {
    }

    public record EnvironmentView(String weatherCondition, double visibilityFactor, double gripFactor) {
    }

    public record SpeedAdvisorView(double currentKmh, double legalLimitKmh, double recommendedKmh, String action) {
    }

    public record DemoPhaseView(String id, String label, String status) {
    }
}
