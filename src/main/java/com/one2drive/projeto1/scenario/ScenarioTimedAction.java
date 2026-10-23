package com.one2drive.projeto1.scenario;

public class ScenarioTimedAction {
    private long triggerTimeMillis;
    private ScenarioTimedActionType type;
    private ScenarioRoadElementDefinition roadElement;
    private ScenarioActorDefinition actor;
    private Double value;

    public long getTriggerTimeMillis() {
        return triggerTimeMillis;
    }

    public void setTriggerTimeMillis(long triggerTimeMillis) {
        this.triggerTimeMillis = triggerTimeMillis;
    }

    public ScenarioTimedActionType getType() {
        return type;
    }

    public void setType(ScenarioTimedActionType type) {
        this.type = type;
    }

    public ScenarioRoadElementDefinition getRoadElement() {
        return roadElement;
    }

    public void setRoadElement(ScenarioRoadElementDefinition roadElement) {
        this.roadElement = roadElement;
    }

    public ScenarioActorDefinition getActor() {
        return actor;
    }

    public void setActor(ScenarioActorDefinition actor) {
        this.actor = actor;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
}
