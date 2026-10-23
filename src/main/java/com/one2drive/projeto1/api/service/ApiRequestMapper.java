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
    /**
     * The slowest vehicle the API places; the UI's "Add vehicle" form asks for the same.
     * Scenarios and tests still create slower or stopped vehicles. Pedestrians keep 0 to 6 km/h.
     */
    public static final double MIN_VEHICLE_SPEED_KMH = 6.0;

    public ScenarioRoadElementDefinition toRoadElement(ApiCreateRoadElementRequest request) {
        ScenarioRoadElementDefinition definition = new ScenarioRoadElementDefinition();
        definition.setType(ScenarioRoadElementType.valueOf(request.getType().trim().toUpperCase()));
        definition.setLane(laneOf(definition.getType(), request.getLane()));
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

    /**
     * Speed limits, stop signs, traffic lights and crosswalks govern the whole road, so a request
     * for one needs no lane: the road map stores it in its centre cell, and the lane is only where it
     * is stored. Every other element stands in the lane it names.
     */
    private static Lane laneOf(ScenarioRoadElementType type, String requested) {
        boolean given = requested != null && !requested.isBlank() && !"ALL".equalsIgnoreCase(requested.trim());
        boolean roadWide = type == ScenarioRoadElementType.SPEED_LIMIT || type == ScenarioRoadElementType.STOP_SIGN
            || type == ScenarioRoadElementType.TRAFFIC_LIGHT || type == ScenarioRoadElementType.CROSSWALK;
        if (!given) {
            if (roadWide) {
                return Lane.CENTER;
            }
            throw new IllegalArgumentException("A " + type + " needs a lane");
        }
        return Lane.valueOf(requested.trim().toUpperCase());
    }

    public ScenarioActorDefinition toActor(ApiCreateActorRequest request) {
        ScenarioActorDefinition definition = new ScenarioActorDefinition();
        definition.setType(ScenarioActorType.valueOf(request.getType().trim().toUpperCase()));
        if (definition.getType() != ScenarioActorType.PEDESTRIAN && request.getSpeedKmh() < MIN_VEHICLE_SPEED_KMH) {
            throw new IllegalArgumentException("A vehicle needs a speed of at least " + MIN_VEHICLE_SPEED_KMH + " km/h");
        }
        definition.setLane(Lane.valueOf(request.getLane().trim().toUpperCase()));
        definition.setPositionMeters(request.getDistanceAheadMeters());
        definition.setSpeedKmh(request.getSpeedKmh());
        return definition;
    }
}
