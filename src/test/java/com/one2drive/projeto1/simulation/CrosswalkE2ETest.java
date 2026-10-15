package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Crosswalk;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crosswalk is part of the road, not of the pedestrian: it appears on the block the
 * pedestrian crosses at, spans every lane, and outlives the pedestrian until the car has
 * left it well behind, like any other passed road element.
 */
class CrosswalkE2ETest {

    private static SimulationEngine runningEngine() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("CROSSWALK");
        scenario.setName("Pedestrian crossing at a crosswalk");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        SimulationEngine engine = BackendTestSupport.engine(scenario, ExpertBackendType.DROOLS);
        engine.start();
        for (int i = 0; i < 10; i++) {
            engine.tick();
        }
        return engine;
    }

    private static String addPedestrian(SimulationEngine engine, double metersAhead) {
        ScenarioActorDefinition pedestrian = new ScenarioActorDefinition();
        pedestrian.setType(ScenarioActorType.PEDESTRIAN);
        pedestrian.setLane(Lane.LEFT);
        pedestrian.setPositionMeters(metersAhead);
        pedestrian.setSpeedKmh(6.0);
        String id = engine.addActorAheadAndGetId(pedestrian);
        engine.tick();
        return id;
    }

    private static List<RoadElement> crosswalks(SimulationEngine engine) {
        return engine.getWorldState().getRoadElements().stream().filter(Crosswalk.class::isInstance).toList();
    }

    @Test
    @DisplayName("adding a pedestrian paints one crosswalk on its block")
    void pedestrianGetsACrosswalkOnItsBlock() {
        SimulationEngine engine = runningEngine();
        String pedestrianId = addPedestrian(engine, 60.0);

        List<RoadElement> crosswalks = crosswalks(engine);
        assertEquals(1, crosswalks.size(), "one crosswalk for the pedestrian");
        RoadMap map = engine.getWorldState().getRoadMap();
        double pedestrianPosition = engine.getWorldState().findActorById(pedestrianId).orElseThrow().getPositionMeters();
        int row = map.rowAt(pedestrianPosition);
        assertEquals(row, map.rowAt(crosswalks.get(0).getPositionMeters()), "crosswalk on the pedestrian's block");
        // One cell, in the centre, like a sign; it binds every lane through perception.
        assertEquals(Lane.CENTER, crosswalks.get(0).getLane());
        int code = map.get(row, Lane.CENTER);
        assertTrue(code != MapLegend.FREE, "the block is marked in the map");
        assertEquals("Crosswalk", map.legend().entry(code).orElseThrow().label());

        // A second pedestrian on the same block shares the crosswalk.
        addPedestrian(engine, 60.0 - engine.getWorldState().getEgo().getSpeedMps() * 0.2);
        assertEquals(1, crosswalks(engine).size(), "no second crosswalk on the same block");
    }

    @Test
    @Timeout(60)
    @DisplayName("the crosswalk outlives the pedestrian and only leaves once far behind the car")
    void crosswalkStaysAfterThePedestrianHasCrossed() {
        SimulationEngine engine = runningEngine();
        String pedestrianId = addPedestrian(engine, 60.0);
        double crosswalkPosition = crosswalks(engine).get(0).getPositionMeters();

        int ticks = 0;
        while (engine.getWorldState().findActorById(pedestrianId).isPresent() && ticks++ < 3000) {
            engine.tick();
        }
        assertTrue(engine.getWorldState().findActorById(pedestrianId).isEmpty(), "pedestrian crossed and left the world");
        assertEquals(1, crosswalks(engine).size(), "crosswalk still on the road after the pedestrian is gone");

        ticks = 0;
        while (!crosswalks(engine).isEmpty() && ticks++ < 3000) {
            engine.tick();
        }
        assertTrue(crosswalks(engine).isEmpty(), "crosswalk removed once passed and far behind");
        double behind = engine.getWorldState().getEgo().getPositionMeters() - crosswalkPosition;
        assertTrue(behind > SimulationEngine.RENDER_BEHIND_METERS,
            "removed only after it slid out of the rendered window, was " + behind + " m behind");
    }
}
