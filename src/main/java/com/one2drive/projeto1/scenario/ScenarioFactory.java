package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.StopSign;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.domain.WeatherState;
import com.one2drive.projeto1.domain.YieldSign;
import com.one2drive.projeto1.simulation.MapLegend;
import com.one2drive.projeto1.simulation.RoadMap;
import com.one2drive.projeto1.simulation.WorldState;

public final class ScenarioFactory {
    private ScenarioFactory() {
    }

    public static WorldState buildWorld(ScenarioDefinition definition, SimulationConfig config) {
        Lane lane = definition.getInitialLane() == null ? Lane.CENTER : definition.getInitialLane();
        EgoVehicleState ego = new EgoVehicleState(0, definition.getInitialSpeedKmh() / 3.6, 0, lane);
        WeatherState weather = toWeatherState(definition.getInitialWeather());
        WorldState worldState = new WorldState(ego, weather, buildRoadMap(definition, config));
        if (definition.getLeftMarking() != null) {
            worldState.setLeftMarking(definition.getLeftMarking());
        }
        if (definition.getRightMarking() != null) {
            worldState.setRightMarking(definition.getRightMarking());
        }

        for (ScenarioActorDefinition actor : definition.getActors()) {
            worldState.addActor(createActor(actor));
        }
        return worldState;
    }

    /** The scenario's matrix with its legend, or an empty road of the default length. */
    public static RoadMap buildRoadMap(ScenarioDefinition definition, SimulationConfig config) {
        MapLegend legend = MapLegend.defaults().withOverrides(definition.getMapLegend());
        if (definition.getMap() == null) {
            return RoadMap.empty(config.getDefaultMapRows(), config.getMapBlockLengthMeters(), legend);
        }
        try {
            return new RoadMap(definition.getMap(), config.getMapBlockLengthMeters(), legend);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Scenario " + definition.getId() + ": " + e.getMessage(), e);
        }
    }

    public static WeatherState toWeatherState(WeatherCondition condition) {
        if (condition == WeatherCondition.RAIN) {
            return new WeatherState(WeatherCondition.RAIN, 0.7, 0.65);
        }
        return new WeatherState(WeatherCondition.CLEAR, 1.0, 1.0);
    }

    public static com.one2drive.projeto1.domain.RoadElement createRoadElement(ScenarioRoadElementDefinition definition) {
        Lane lane = definition.getLane() == null ? Lane.CENTER : definition.getLane();
        return switch (definition.getType()) {
            case SPEED_LIMIT -> new SpeedLimitSign(definition.getPositionMeters(), lane, definition.getSpeedLimitKmh());
            case STOP_SIGN -> new StopSign(definition.getPositionMeters(), lane);
            case YIELD_SIGN -> new YieldSign(definition.getPositionMeters(), lane);
            case TRAFFIC_LIGHT -> new TrafficLight(
                definition.getPositionMeters(),
                lane,
                definition.getTrafficLightState() == null ? TrafficLightState.RED : definition.getTrafficLightState()
            );
            case POTHOLE -> new Pothole(
                definition.getPositionMeters(),
                lane,
                definition.getSeverity() == null ? HazardSeverity.MEDIUM : definition.getSeverity()
            );
            case STATIC_OBSTACLE -> new StaticObstacle(
                definition.getPositionMeters(),
                lane,
                definition.getSeverity() == null ? HazardSeverity.HIGH : definition.getSeverity()
            );
            case BARRIER -> new Barrier(
                definition.getPositionMeters(),
                lane,
                definition.getBarrierOpen() == null || definition.getBarrierOpen()
            );
            case SPEED_RADAR -> new com.one2drive.projeto1.domain.SpeedRadar(
                definition.getPositionMeters(),
                lane,
                definition.getSpeedLimitKmh() > 0 ? definition.getSpeedLimitKmh() : 50.0
            );
        };
    }

    public static com.one2drive.projeto1.domain.DynamicActor createActor(ScenarioActorDefinition definition) {
        Lane lane = definition.getLane() == null ? Lane.CENTER : definition.getLane();
        double speedMps = definition.getSpeedKmh() / 3.6;
        return switch (definition.getType()) {
            case PEDESTRIAN -> new Pedestrian(definition.getPositionMeters(), lane, speedMps);
            case FRONT_VEHICLE, VEHICLE_LEFT, VEHICLE_RIGHT -> new OtherVehicle(definition.getPositionMeters(), lane, speedMps);
        };
    }
}
