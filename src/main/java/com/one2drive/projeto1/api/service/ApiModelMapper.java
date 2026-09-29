package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiDecisionView;
import com.one2drive.projeto1.api.model.ApiRoadElement;
import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiTimelineEntry;
import com.one2drive.projeto1.api.model.ApiWorkingMemoryRow;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.explanation.TimelineEntry;
import com.one2drive.projeto1.simulation.RoadRenderItem;
import com.one2drive.projeto1.simulation.SimulationEngine;
import com.one2drive.projeto1.simulation.SimulationSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ApiModelMapper {
    public ApiSimulationSnapshot mapSnapshot(SimulationEngine engine, SimulationSnapshot snapshot) {
        DrivingDecision decision = engine.getCurrentDecision();
        List<ApiRoadElement> worldObjects = engine.getAllRoadItemsRelativeToEgo().stream()
            .map(this::mapRoadItem)
            .toList();

        List<ApiWorkingMemoryRow> workingMemory = engine.getWorkingMemoryFactViews().stream()
            .map(this::mapWorkingMemory)
            .toList();

        List<ApiTimelineEntry> timeline = engine.getTimelineEntries().stream()
            .map(this::mapTimeline)
            .toList();

        List<String> recentRules = timeline.stream()
            .filter(t -> t.type().contains("RULE"))
            .map(ApiTimelineEntry::message)
            .limit(20)
            .toList();

        List<String> recentFacts = timeline.stream()
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
                engine.getWorldState().getEgo().getAccelerationMps2()
            ),
            new ApiSimulationSnapshot.EnvironmentView(
                engine.getWorldState().getWeatherState().getCondition().name(),
                engine.getWorldState().getWeatherState().getVisibilityFactor(),
                engine.getWorldState().getWeatherState().getGripFactor()
            ),
            new ApiSimulationSnapshot.SpeedAdvisorView(
                snapshot.egoSpeedKmh(),
                snapshot.legalLimitKmh(),
                snapshot.recommendedSpeedKmh(),
                snapshot.currentDecision()
            ),
            new ApiDecisionView(
                decision.getAction().name(),
                decision.getRecommendedSpeedKmh(),
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
            (int) engine.getConfig().getTickMillis()
        );
    }

    private ApiRoadElement mapRoadItem(RoadRenderItem item) {
        return new ApiRoadElement(item.id(), item.type(), item.positionMeters(), item.lane(), item.details());
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
        return "SENSORS";
    }
}
