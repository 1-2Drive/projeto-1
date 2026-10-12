package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiSystemStatus;
import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.simulation.SimulationEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationRuntimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationRuntimeService.class);

    private final SimulationEngine simulationEngine;
    private final ScenarioLibrary scenarioLibrary;
    private final ApiModelMapper apiModelMapper;
    private final RealtimeSnapshotPublisher publisher;
    private final SimulationConfig config;
    private ScheduledExecutorService scheduler;

    public SimulationRuntimeService(SimulationEngine simulationEngine,
                                    ScenarioLibrary scenarioLibrary,
                                    ApiModelMapper apiModelMapper,
                                    RealtimeSnapshotPublisher publisher,
                                    SimulationConfig config) {
        this.simulationEngine = simulationEngine;
        this.scenarioLibrary = scenarioLibrary;
        this.apiModelMapper = apiModelMapper;
        this.publisher = publisher;
        this.config = config;
    }

    @PostConstruct
    void startLoop() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "simulation-api-loop");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            // A scheduled task that throws is never run again, which would freeze the
            // simulation silently: log the failure and keep ticking.
            try {
                synchronized (this) {
                    simulationEngine.tick();
                    publisher.publish(snapshot());
                }
            } catch (RuntimeException e) {
                LOGGER.error("Simulation tick failed", e);
            }
        }, 0, config.getTickMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stopLoop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public synchronized ApiSimulationSnapshot snapshot() {
        return apiModelMapper.mapSnapshot(simulationEngine, simulationEngine.snapshot());
    }

    public synchronized ApiSystemStatus systemStatus() {
        return new ApiSystemStatus(
            "CONNECTED",
            "ACTIVE",
            simulationEngine.getStatus().name()
        );
    }

    public synchronized void start() {
        simulationEngine.start();
        publisher.publish(snapshot());
    }

    public synchronized void pause() {
        simulationEngine.pause();
        publisher.publish(snapshot());
    }

    public synchronized void resume() {
        simulationEngine.start();
        publisher.publish(snapshot());
    }

    public synchronized void stop() {
        simulationEngine.stop();
        publisher.publish(snapshot());
    }

    public synchronized void reset() {
        ScenarioDefinition baseline = scenarioLibrary.findById("S1")
            .orElseGet(() -> scenarioLibrary.getScenarios().stream().findFirst().orElseThrow());
        simulationEngine.reset(baseline);
        publisher.publish(snapshot());
    }

    /**
     * Forwards a driver lane change request to Drools. The request is only a command:
     * the rules decide whether the target lane is safe before any maneuver starts.
     */
    public synchronized void requestLaneChange(LaneChangeDirection direction) {
        simulationEngine.requestLaneChange(direction);
        publisher.publish(snapshot());
    }

    public synchronized void loadScenario(String scenarioId) {
        ScenarioDefinition scenario = scenarioLibrary.findById(scenarioId)
            .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + scenarioId));
        simulationEngine.reset(scenario);
        publisher.publish(snapshot());
    }

    public SimulationEngine engine() {
        return simulationEngine;
    }

    public synchronized void broadcastSnapshot() {
        publisher.publish(snapshot());
    }
}
