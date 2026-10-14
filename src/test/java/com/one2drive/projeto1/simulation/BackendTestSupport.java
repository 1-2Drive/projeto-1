package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.prolog.PrologTestHarness;
import com.one2drive.projeto1.scenario.ScenarioDefinition;

/**
 * Builds simulation engines for tests that run the same scenario on every backend
 * (JUnit {@code @EnumSource(ExpertBackendType.class)}).
 */
final class BackendTestSupport {

    private BackendTestSupport() {
    }

    /** An engine on {@code backend}, skipping the test when SWI-Prolog is missing. */
    static SimulationEngine engine(ScenarioDefinition scenario, ExpertBackendType backend) {
        return engine(SimulationConfig.defaultConfig(), scenario, backend);
    }

    static SimulationEngine engine(SimulationConfig config, ScenarioDefinition scenario, ExpertBackendType backend) {
        if (backend == ExpertBackendType.PROLOG) {
            PrologTestHarness.assumeSwiplAvailable();
        }
        return new SimulationEngine(config, scenario, true, d -> { }, backend);
    }
}
