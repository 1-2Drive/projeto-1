package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineE2ETest {

    @Test
    @Timeout(12)
    void simulationLoopProducesDroolsDecisionAndTimeline() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        var scenario = library.findById("S2").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.start();
            for (int i = 0; i < 12; i++) {
                engine.tick();
            }

            SimulationSnapshot snapshot = engine.snapshot();
            assertTrue(snapshot.egoPositionMeters() > 10.0);
            assertFalse(snapshot.timeline().isEmpty());
            assertTrue(snapshot.timeline().stream().anyMatch(line -> line.contains("RULE FIRED")));
            assertTrue(snapshot.currentDecision().equals(DrivingAction.REDUCE_SPEED.name())
                || snapshot.currentDecision().equals(DrivingAction.MAINTAIN_SPEED.name()));
            assertTrue(snapshot.workingMemoryFacts().stream().anyMatch(f -> f.contains("DrivingDecision")));
        }
    }

    @Test
    @Timeout(12)
    void runningSimulationAcceptsDynamicRoadObjectAndReacts() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        var scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.start();
            for (int i = 0; i < 6; i++) {
                engine.tick();
            }

            ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
            sign.setType(ScenarioRoadElementType.SPEED_LIMIT);
            sign.setLane(Lane.CENTER);
            sign.setPositionMeters(40.0);
            sign.setSpeedLimitKmh(50);
            engine.addRoadElementAhead(sign);

            for (int i = 0; i < 20; i++) {
                engine.tick();
            }

            SimulationSnapshot snapshot = engine.snapshot();
            assertTrue(snapshot.legalLimitKmh() <= 50.0);
            assertTrue(snapshot.currentDecision().equals(DrivingAction.REDUCE_SPEED.name())
                || snapshot.currentDecision().equals(DrivingAction.MAINTAIN_SPEED.name()));
            assertTrue(snapshot.timeline().stream().anyMatch(line -> line.contains("SpeedLimitDetectedEvent") || line.contains("RULE FIRED")));
        }
    }

    @Test
    @Timeout(12)
    void weatherFactorMutationUpdatesWorldAndDecisionContext() {
        ScenarioLibrary library = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        var scenario = library.findById("S1").orElseThrow();

        try (SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {
        })) {
            engine.start();
            engine.tick();

            engine.setWeatherFactors(0.55, 0.55);
            for (int i = 0; i < 8; i++) {
                engine.tick();
            }

            SimulationSnapshot snapshot = engine.snapshot();
            assertTrue(engine.getWorldState().getWeatherState().getVisibilityFactor() <= 0.56);
            assertTrue(engine.getWorldState().getWeatherState().getGripFactor() <= 0.56);
            assertTrue(engine.getWorldState().getWeatherState().getCondition() == WeatherCondition.RAIN);
            assertTrue(snapshot.currentDecision().equals(DrivingAction.REDUCE_SPEED.name())
                || snapshot.currentDecision().equals(DrivingAction.BRAKE.name())
                || snapshot.currentDecision().equals(DrivingAction.MAINTAIN_SPEED.name()));
            assertTrue(snapshot.timeline().stream().anyMatch(line -> line.contains("WeatherEvent") || line.contains("RULE FIRED")));
        }
    }
}
