package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiCreateActorRequest;
import com.one2drive.projeto1.api.model.ApiCreateRoadElementRequest;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.springframework.stereotype.Component;

@Component
public class ApiRequestMapper {
    public ScenarioRoadElementDefinition toRoadElement(ApiCreateRoadElementRequest request) {
        ScenarioRoadElementDefinition definition = new ScenarioRoadElementDefinition();
        definition.setType(ScenarioRoadElementType.valueOf(request.getType().trim().toUpperCase()));
        definition.setLane(definition.getType() == ScenarioRoadElementType.SPEED_RADAR
            ? null : Lane.valueOf(request.getLane().trim().toUpperCase()));
        definition.setPositionMeters(request.getDistanceAheadMeters());
        definition.setSpeedLimitKmh(request.getSpeedLimitKmh());

        if (request.getTrafficLightState() != null && !request.getTrafficLightState().isBlank()) {
            definition.setTrafficLightState(TrafficLightState.valueOf(request.getTrafficLightState().trim().toUpperCase()));
        }
        if (request.getSeverity() != null && !request.getSeverity().isBlank()) {
            definition.setSeverity(HazardSeverity.valueOf(request.getSeverity().trim().toUpperCase()));
        }
        definition.setBarrierOpen(request.getBarrierOpen());
        return definition;
    }

    public ScenarioActorDefinition toActor(ApiCreateActorRequest request) {
        ScenarioActorDefinition definition = new ScenarioActorDefinition();
        definition.setType(ScenarioActorType.valueOf(request.getType().trim().toUpperCase()));
        definition.setLane(Lane.valueOf(request.getLane().trim().toUpperCase()));
        definition.setPositionMeters(request.getDistanceAheadMeters());
        definition.setSpeedKmh(request.getSpeedKmh());
        return definition;
    }
}
