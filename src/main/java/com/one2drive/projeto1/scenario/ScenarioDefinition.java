package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.domain.WeatherCondition;

import java.util.ArrayList;
import java.util.List;

public class ScenarioDefinition {
    private String id;
    private String name;
    private double initialSpeedKmh;
    private WeatherCondition initialWeather;
    private List<ScenarioRoadElementDefinition> roadElements = new ArrayList<>();
    private List<ScenarioActorDefinition> actors = new ArrayList<>();
    private List<ScenarioTimedAction> timedActions = new ArrayList<>();

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

    public List<ScenarioRoadElementDefinition> getRoadElements() {
        return roadElements;
    }

    public void setRoadElements(List<ScenarioRoadElementDefinition> roadElements) {
        this.roadElements = roadElements;
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
}
