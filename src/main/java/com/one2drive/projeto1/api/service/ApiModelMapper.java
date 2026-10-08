package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiDecisionView;
import com.one2drive.projeto1.api.model.ApiRoadElement;
import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiTimelineEntry;
import com.one2drive.projeto1.api.model.ApiWorkingMemoryRow;
import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.explanation.TimelineEntry;
import com.one2drive.projeto1.simulation.RoadMapSnapshot;
import com.one2drive.projeto1.simulation.RoadRenderItem;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.simulation.SensorTelemetry;
import com.one2drive.projeto1.simulation.SimulationEngine;
import com.one2drive.projeto1.simulation.SimulationSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Component
public class ApiModelMapper {
    public ApiSimulationSnapshot mapSnapshot(SimulationEngine engine, SimulationSnapshot snapshot) {
        DrivingDecision decision = engine.getCurrentDecision();
        SimulationConfig config = engine.getConfig();
        List<ApiRoadElement> worldObjects = engine.getAllRoadItemsRelativeToEgo().stream()
            .map(this::mapRoadItem)
            .toList();

        List<ApiWorkingMemoryRow> workingMemory = engine.getWorkingMemoryFactViews().stream()
            .map(this::mapWorkingMemory)
            .toList();

        List<ApiTimelineEntry> allTimeline = engine.getTimelineEntries().stream()
            .map(this::mapTimeline)
            .toList();
        int timelineStart = Math.max(0, allTimeline.size() - 160);
        List<ApiTimelineEntry> timeline = allTimeline.subList(timelineStart, allTimeline.size());

        List<ApiTimelineEntry> reverseTimeline = new ArrayList<>(timeline);
        Collections.reverse(reverseTimeline);

        List<String> recentRules = reverseTimeline.stream()
            .filter(t -> t.type().contains("RULE"))
            .map(ApiTimelineEntry::message)
            .limit(20)
            .toList();

        List<String> recentFacts = reverseTimeline.stream()
            .filter(t -> t.type().contains("FACT"))
            .map(ApiTimelineEntry::message)
            .limit(20)
            .toList();

        String activeStage = inferActiveStage(timeline);

        return new ApiSimulationSnapshot(
            snapshot.simTimeMillis(),
            snapshot.status().name(),
            new ApiSimulationSnapshot.EgoVehicleView(
                snapshot.egoSpeedKmh(),
                snapshot.egoPositionMeters(),
                snapshot.egoLane(),
                engine.getWorldState().getEgo().getAccelerationMps2(),
                snapshot.egoLateralPositionMeters(),
                snapshot.egoTargetLane(),
                snapshot.egoLaneChangeState(),
                snapshot.egoLaneChangeProgress(),
                engine.getWorldState().getEgo().getDrivingState().name(),
                engine.getWorldState().getEgo().getIndicatorState().name(),
                engine.getWorldState().getEgo().getSteeringState().name(),
                engine.getWorldState().getEgo().getHeadingDegrees()
            ),
            new ApiSimulationSnapshot.EnvironmentView(
                engine.getWorldState().getWeatherState().getCondition().name(),
                engine.getWorldState().getWeatherState().getVisibilityFactor(),
                engine.getWorldState().getWeatherState().getGripFactor()
            ),
            new ApiSimulationSnapshot.SpeedAdvisorView(
                snapshot.egoSpeedKmh(),
                snapshot.legalLimitKmh(),
                finiteOrCurrent(snapshot.recommendedSpeedKmh(), snapshot.egoSpeedKmh()),
                snapshot.currentDecision()
            ),
            new ApiDecisionView(
                decision.getAction().name(),
                finiteOrCurrent(decision.getRecommendedSpeedKmh(), snapshot.egoSpeedKmh()),
                decision.getRiskLevel().name(),
                decision.getReason(),
                decision.getSourceRule(),
                decision.getTimestamp(),
                decision.getEvidence()
            ),
            snapshot.sensorValues(),
            worldObjects,
            workingMemory,
            timeline,
            recentRules,
            recentFacts,
            activeStage,
            (int) engine.getConfig().getTickMillis(),
            snapshot.detectedObjectIds(),
            snapshot.cameraDetectedObjectIds(),
            snapshot.radarDetectedObjectIds(),
            new ApiSimulationSnapshot.SensorRangesView(
                config.getCameraRangeMeters(),
                config.getRadarRangeMeters(),
                config.getBlindSpotRangeMeters(),
                config.getCameraRateMillis(),
                config.getRadarRateMillis()
            ),
            demoPhases(engine),
            maneuver(engine, snapshot),
            sensors(engine),
            InferenceNarrator.summarise(engine, snapshot),
            roadMap(engine.roadMapSnapshot()),
            new ApiSimulationSnapshot.RoadMarksView(
                engine.getWorldState().getLeftMarking().name(),
                engine.getWorldState().getRightMarking().name()
            )
        );
    }

    private ApiSimulationSnapshot.RoadMapView roadMap(RoadMapSnapshot map) {
        return new ApiSimulationSnapshot.RoadMapView(
            map.cells().length,
            Arrays.stream(Lane.values()).map(Lane::name).toList(),
            map.blockLengthMeters(),
            map.egoRow(),
            map.lap(),
            map.cells(),
            map.legend().stream()
                .map(entry -> new ApiSimulationSnapshot.MapLegendEntryView(
                    entry.code(), entry.template().getType().name(), entry.label(), entry.detail()))
                .toList()
        );
    }

    /** Physical maneuver state; indicators and brake lights are presentation of it, not policy. */
    private ApiSimulationSnapshot.ManeuverView maneuver(SimulationEngine engine, SimulationSnapshot snapshot) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        LaneChangeState state = ego.getLaneChangeState();
        boolean left = ego.getIndicatorState() == com.one2drive.projeto1.domain.IndicatorState.LEFT
            || (state.isActive() && ego.getTargetLane().centreMeters() < ego.getLateralPositionMeters());
        boolean right = ego.getIndicatorState() == com.one2drive.projeto1.domain.IndicatorState.RIGHT
            || (state.isActive() && ego.getTargetLane().centreMeters() > ego.getLateralPositionMeters());
        double acceleration = ego.getAccelerationMps2();
        return new ApiSimulationSnapshot.ManeuverView(
            snapshot.egoLaneChangeState(),
            snapshot.egoLane(),
            snapshot.egoTargetLane(),
            snapshot.egoLaneChangeProgress(),
            snapshot.egoLateralPositionMeters(),
            left,
            right,
            acceleration < -0.1,
            ego.getSteeringState().name()
        );
    }

    private ApiSimulationSnapshot.SensorTelemetryView sensors(SimulationEngine engine) {
        SensorTelemetry telemetry = engine.getSensorTelemetry();
        return new ApiSimulationSnapshot.SensorTelemetryView(
            new ApiSimulationSnapshot.RadarTelemetryView(
                telemetry.frontRadar().rangeMeters(),
                telemetry.frontRadar().targetId(),
                telemetry.frontRadar().distanceMeters(),
                telemetry.frontRadar().relativeSpeedKmh(),
                telemetry.frontRadar().ttcSeconds(),
                telemetry.frontRadar().status(),
                telemetry.frontRadar().speedRadarDistanceMeters(),
                telemetry.frontRadar().speedRadarLimitKmh()
            ),
            new ApiSimulationSnapshot.CameraTelemetryView(
                telemetry.frontCamera().rangeMeters(),
                telemetry.frontCamera().detections().stream()
                    .map(d -> new ApiSimulationSnapshot.CameraDetectionView(
                        d.objectId(), d.label(), d.distanceMeters(), d.lane()))
                    .toList(),
                telemetry.frontCamera().status()
            ),
            blindSpot(telemetry.blindSpotLeft()),
            blindSpot(telemetry.blindSpotRight()),
            new ApiSimulationSnapshot.EnvironmentTelemetryView(
                telemetry.environment().condition(),
                telemetry.environment().effectiveRangeMeters(),
                telemetry.environment().gripPercent(),
                telemetry.environment().rain()
            )
        );
    }

    private ApiSimulationSnapshot.BlindSpotTelemetryView blindSpot(SensorTelemetry.BlindSpotView view) {
        return new ApiSimulationSnapshot.BlindSpotTelemetryView(
            view.rangeMeters(), view.occupied(), view.targetId(), view.distanceMeters(), view.status());
    }

    private ApiRoadElement mapRoadItem(RoadRenderItem item) {
        return new ApiRoadElement(item.id(), item.type(), item.positionMeters(), item.lane(), item.details(), item.worldPositionMeters());
    }

    private ApiWorkingMemoryRow mapWorkingMemory(WorkingMemoryFactView view) {
        return new ApiWorkingMemoryRow(
            view.type(),
            view.value(),
            view.source(),
            view.timestamp(),
            view.stream()
        );
    }

    private ApiTimelineEntry mapTimeline(TimelineEntry entry) {
        return new ApiTimelineEntry(entry.getTimestamp(), entry.getType().name(), entry.getMessage());
    }

    private String inferActiveStage(List<ApiTimelineEntry> timeline) {
        if (timeline.isEmpty()) {
            return "WORLD";
        }
        String latest = timeline.get(timeline.size() - 1).type();
        if (latest.contains("FACT")) {
            return "WORKING_MEMORY";
        }
        if (latest.contains("RULE")) {
            return "RULES";
        }
        if (latest.contains("DECISION")) {
            return "DECISION";
        }
        if (latest.contains("ACTUATOR")) {
            return "ACTUATOR";
        }
        if (latest.contains("MANEUVER")) {
            return "ACTUATOR";
        }
        if (latest.contains("PERCEPTION")) {
            return "SENSORS";
        }
        if (latest.contains("SENSOR")) {
            return "SENSORS";
        }
        return "SENSORS";
    }

    private double finiteOrCurrent(double candidate, double currentSpeed) {
        return Double.isFinite(candidate) ? candidate : currentSpeed;
    }

    /**
     * Presentation phases are only meaningful for the scripted demo scenario, so an
     * empty list keeps the manual workflow free of demo chrome.
     */
    private List<ApiSimulationSnapshot.DemoPhaseView> demoPhases(SimulationEngine engine) {
        if (!"S14_PRESENTATION_DEMO".equals(engine.getScenarioId())) {
            return List.of();
        }

        List<String> ids = List.of("normal", "speed-limit", "weather", "ttc", "pedestrian");
        List<String> labels = List.of("Normal driving", "Speed limit", "Weather", "TTC safety", "Pedestrian");
        Set<String> evidence = engine.getDemoEvidence();
        String activeId = ids.stream().filter(id -> !evidence.contains(id)).findFirst().orElse(null);

        return IntStream.range(0, ids.size())
            .mapToObj(index -> {
                String id = ids.get(index);
                String status = evidence.contains(id) ? "DONE" : id.equals(activeId) ? "ACTIVE" : "PENDING";
                return new ApiSimulationSnapshot.DemoPhaseView(id, labels.get(index), status);
            })
            .toList();
    }
}
