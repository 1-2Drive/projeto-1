package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.domain.WeatherCondition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScenarioDefinition {
    private String id;
    private String name;
    private double initialSpeedKmh;
    private WeatherCondition initialWeather;
    /**
     * Road map: one row per block (rows go forward along the road), one column per lane
     * (LEFT, CENTER, RIGHT). 0 is free, any other number is a code from the map legend.
     * Null means an empty road of the configured default length.
     */
    private int[][] map;
    /** Extra or replaced legend codes for this scenario, on top of the default legend. */
    private Map<Integer, ScenarioRoadElementDefinition> mapLegend = new LinkedHashMap<>();
    private List<ScenarioActorDefinition> actors = new ArrayList<>();
    private List<ScenarioRoadElementDefinition> roadElements = new ArrayList<>();
    private List<ScenarioTimedAction> timedActions = new ArrayList<>();
    private double initialPositionMeters = 0.0;
    private com.one2drive.projeto1.domain.Lane initialLane;
    private com.one2drive.projeto1.domain.RoadMarkingType leftMarking = com.one2drive.projeto1.domain.RoadMarkingType.DASHED;
    private com.one2drive.projeto1.domain.RoadMarkingType rightMarking = com.one2drive.projeto1.domain.RoadMarkingType.DASHED;

    public com.one2drive.projeto1.domain.Lane getInitialLane() {
        return initialLane;
    }

    public void setInitialLane(com.one2drive.projeto1.domain.Lane initialLane) {
        this.initialLane = initialLane;
    }

    public com.one2drive.projeto1.domain.RoadMarkingType getLeftMarking() {
        return leftMarking;
    }

    public void setLeftMarking(com.one2drive.projeto1.domain.RoadMarkingType leftMarking) {
        this.leftMarking = leftMarking;
    }

    public com.one2drive.projeto1.domain.RoadMarkingType getRightMarking() {
        return rightMarking;
    }

    public void setRightMarking(com.one2drive.projeto1.domain.RoadMarkingType rightMarking) {
        this.rightMarking = rightMarking;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getInitialSpeedKmh() {
        return initialSpeedKmh;
    }

    public void setInitialSpeedKmh(double initialSpeedKmh) {
        this.initialSpeedKmh = initialSpeedKmh;
    }

    public WeatherCondition getInitialWeather() {
        return initialWeather;
    }

    public void setInitialWeather(WeatherCondition initialWeather) {
        this.initialWeather = initialWeather;
    }

    public int[][] getMap() {
        return map;
    }

    public void setMap(int[][] map) {
        this.map = map;
    }

    public Map<Integer, ScenarioRoadElementDefinition> getMapLegend() {
        return mapLegend;
    }

    public void setMapLegend(Map<Integer, ScenarioRoadElementDefinition> mapLegend) {
        this.mapLegend = mapLegend;
    }

    /** Number of map cells holding a road element. */
    public int occupiedCellCount() {
        if (map == null) {
            return 0;
        }
        int count = 0;
        for (int[] row : map) {
            for (int code : row) {
                if (code != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public List<ScenarioActorDefinition> getActors() {
        return actors;
    }

    public void setActors(List<ScenarioActorDefinition> actors) {
        this.actors = actors;
    }

    public List<ScenarioTimedAction> getTimedActions() {
        return timedActions;
    }

    public void setTimedActions(List<ScenarioTimedAction> timedActions) {
        this.timedActions = timedActions;
    }

    public List<ScenarioRoadElementDefinition> getRoadElements() {
        return roadElements;
    }

    public void setRoadElements(List<ScenarioRoadElementDefinition> roadElements) {
        this.roadElements = roadElements;
    }

    public double getInitialPositionMeters() {
        return initialPositionMeters;
    }

    public void setInitialPositionMeters(double initialPositionMeters) {
        this.initialPositionMeters = initialPositionMeters;
    }
}
