package com.one2drive.projeto1.api.config;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.simulation.SimulationEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationCoreConfig {
    @Bean
    public SimulationConfig simulationConfig() {
        return SimulationConfig.defaultConfig();
    }

    @Bean
    public ScenarioLibrary scenarioLibrary() {
        return ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
    }

    @Bean(destroyMethod = "close")
    public SimulationEngine simulationEngine(SimulationConfig config, ScenarioLibrary scenarioLibrary) {
        ScenarioDefinition defaultScenario = scenarioLibrary.findById("S1")
            .orElseGet(() -> scenarioLibrary.getScenarios().stream().findFirst().orElseThrow());
        // The scheduler is realtime, while the KIE clock follows simulation ticks.
        // This keeps CEP windows deterministic across pause, reset, and slow clients.
        return new SimulationEngine(config, defaultScenario, true, decision -> {
        });
    }
}
