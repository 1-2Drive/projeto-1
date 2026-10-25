package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiSystemStatus;
import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.history.SessionEvent;
import com.one2drive.projeto1.history.SessionHistoryStore;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.SimulationSession;
import com.one2drive.projeto1.history.VehicleSample;
import com.one2drive.projeto1.explanation.TimelineEntry;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.RandomScenarioGenerator;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.simulation.SimulationEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private final SessionHistoryStore historyStore;
    private SimulationSession activeSession;
    private int recordedTimelineEntries;
    private ScheduledExecutorService scheduler;

    public SimulationRuntimeService(SimulationEngine simulationEngine,
                                    ScenarioLibrary scenarioLibrary,
                                     ApiModelMapper apiModelMapper,
                                     RealtimeSnapshotPublisher publisher,
                                     SimulationConfig config,
                                     SessionHistoryStore historyStore) {
        this.simulationEngine = simulationEngine;
        this.scenarioLibrary = scenarioLibrary;
        this.apiModelMapper = apiModelMapper;
        this.publisher = publisher;
        this.config = config;
        this.historyStore = historyStore;
        this.simulationEngine.setSessionSnapshotConsumer(this::recordTickSnapshot);
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
                ApiSimulationSnapshot snapshot;
                synchronized (this) {
                    simulationEngine.tick();
                    snapshot = snapshot();
                }
                // Outside the lock: sending to the clients must never hold up API calls.
                publisher.publish(snapshot);
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
            simulationEngine.getBackendError() == null ? "ACTIVE" : "ERROR",
            simulationEngine.getStatus().name(),
            simulationEngine.getBackendType().name()
        );
    }

    public synchronized void start() {
        if (simulationEngine.getStatus() == com.one2drive.projeto1.simulation.SimulationStatus.STOPPED) {
            activeSession = createSession();
            recordedTimelineEntries = 0;
            activeSession.addEvent(simulationEngine.snapshot().simTimeMillis(), "SESSION", "Session started");
            activeSession.addEvent(simulationEngine.snapshot().simTimeMillis(), "WORLD_SNAPSHOT",
                String.join("; ", simulationEngine.worldDescription()));
        }
        simulationEngine.start();
        publisher.publish(snapshot());
    }

    public synchronized void pause() {
        simulationEngine.pause();
        publisher.publish(snapshot());
    }

    public synchronized void resume() {
        if (activeSession == null) {
            activeSession = createSession();
            recordedTimelineEntries = 0;
            activeSession.addEvent(simulationEngine.snapshot().simTimeMillis(), "SESSION", "Session started from paused simulation");
        }
        simulationEngine.start();
        publisher.publish(snapshot());
    }

    public synchronized String stop() {
        boolean wasActive = activeSession != null;
        simulationEngine.stop();
        String stoppedSessionId = null;
        try {
            if (wasActive) {
                long now = simulationEngine.snapshot().simTimeMillis();
                recordNewTimelineEvents();
                activeSession.includeDurationThrough(now);
                activeSession.addEvent(now, "SESSION", "Session stopped");
                activeSession.finish(Instant.now());
                historyStore.save(activeSession);
                stoppedSessionId = activeSession.id();
                activeSession = null;
            }
        } finally {
            publisher.publish(snapshot());
        }
        return stoppedSessionId;
    }

    public synchronized void reset() {
        finalizeActiveSession("Session finalized by RESET");
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

    /** @return the id of the loaded scenario, which for {@code RANDOM} names the seed drawn */
    public synchronized String loadScenario(String scenarioId) {
        finalizeActiveSession("Session finalized by scenario load");
        ScenarioDefinition scenario = findScenario(scenarioId)
            .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + scenarioId));
        simulationEngine.reset(scenario);
        publisher.publish(snapshot());
        return scenario.getId();
    }

    /** A scenario of the library, or a random one: {@code RANDOM} draws a new seed, {@code RANDOM-<seed>} repeats one. */
    private Optional<ScenarioDefinition> findScenario(String scenarioId) {
        return scenarioLibrary.findById(scenarioId).or(() -> RandomScenarioGenerator.forId(scenarioId));
    }

    /**
     * Makes {@code type} the deciding engine. The current scenario is reloaded, as a reset
     * would, so the new engine starts from an empty working memory.
     */
    public synchronized void switchBackend(ExpertBackendType type) {
        finalizeActiveSession("Session finalized by engine switch");
        ScenarioDefinition scenario = findScenario(simulationEngine.getScenarioId())
            .orElseGet(() -> scenarioLibrary.getScenarios().stream().findFirst().orElseThrow());
        simulationEngine.switchBackend(type, scenario);
        publisher.publish(snapshot());
    }

    public synchronized void setRandomObjectFrequency(int frequency) {
        simulationEngine.setRandomObjectFrequency(frequency);
        publisher.publish(snapshot());
    }

    public SimulationEngine engine() {
        return simulationEngine;
    }

    public synchronized void broadcastSnapshot() {
        recordNewTimelineEvents();
        publisher.publish(snapshot());
    }

    public synchronized List<SimulationSession> sessions() { return historyStore.list(); }

    public synchronized SimulationSession session(String id) {
        return historyStore.get(id).orElseThrow(() -> new NoSuchElementException("Session not found: " + id));
    }

    private SimulationSession createSession() {
        SimulationSession session = new SimulationSession(UUID.randomUUID().toString(), Instant.now(),
            simulationEngine.snapshot().simTimeMillis(), simulationEngine.getScenarioId(),
            simulationEngine.getBackendType().name(), simulationEngine.worldDescription());
        session.setInitialWorldObjects(simulationEngine.currentWorldDescription());
        return session;
    }

    private synchronized void recordTickSnapshot(SessionSnapshot snapshot) {
        if (activeSession == null || simulationEngine.getStatus() != com.one2drive.projeto1.simulation.SimulationStatus.RUNNING) return;
        if (snapshot == null) return;
        activeSession.addSample(sample(snapshot));
        activeSession.includeDurationThrough(snapshot.simulationTimeMillis());
        recordNewTimelineEvents();
        if (snapshot.timeline().stream().anyMatch(entry -> entry.type().equals("DECISION"))) {
            activeSession.addSnapshot(snapshot);
        }
        activeSession.addEvent(snapshot.simulationTimeMillis(), "VEHICLE_SAMPLE",
            "Vehicle " + snapshot.vehicle().speedKmh() + " km/h in " + snapshot.vehicle().lane()
                + " lane at " + snapshot.vehicle().positionMeters() + " m");
    }

    private VehicleSample sample(SessionSnapshot snapshot) {
        var v = snapshot.vehicle();
        var d = snapshot.decision();
        return new VehicleSample(snapshot.simulationTimeMillis(), v.speedKmh(), v.positionMeters(), v.lane(),
            v.accelerationMps2(), v.lateralPositionMeters(), v.targetLane(), v.laneChangeState(),
            v.laneChangeProgress(), v.drivingState(), v.indicatorState(), v.steeringState(), v.headingDegrees(),
            d.action(), d.reason(), d.sourceRule(), d.recommendedSpeedKmh(), snapshot.environment(),
            snapshot.sensors(), snapshot.worldObjects(), snapshot.workingMemory());
    }

    private void finalizeActiveSession(String reason) {
        if (activeSession == null) return;
        long now = simulationEngine.snapshot().simTimeMillis();
        recordNewTimelineEvents();
        activeSession.addEvent(now, "SESSION", reason);
        activeSession.includeDurationThrough(now);
        activeSession.finish(Instant.now());
        historyStore.save(activeSession);
        activeSession = null;
    }

    private void recordNewTimelineEvents() {
        if (activeSession == null) return;
        List<TimelineEntry> entries = simulationEngine.getTimelineEntries();
        for (int i = recordedTimelineEntries; i < entries.size(); i++) {
            TimelineEntry entry = entries.get(i);
            activeSession.addEvent(entry.getTimestamp(), entry.getType().name(), entry.getMessage());
        }
        recordedTimelineEntries = entries.size();
    }

    public synchronized List<String> currentWorldDescription() { return simulationEngine.worldDescription(); }
}
