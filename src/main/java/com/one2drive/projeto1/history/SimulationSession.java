package com.one2drive.projeto1.history;

import com.one2drive.projeto1.api.model.ApiRoadElement;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete in-memory recording of one simulation run, serialized at STOP/reset. */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
    isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
public final class SimulationSession {
    private String id;
    private Instant startedAt;
    private Instant stoppedAt;
    private String scenarioId;
    private String backend;
    private long startedAtSimulationMillis;
    private long durationMillis;
    private List<SessionEvent> events = new ArrayList<>();
    private List<VehicleSample> vehicleSamples = new ArrayList<>();
    private List<SessionSnapshot> decisionSnapshots = new ArrayList<>();
    private List<String> initialWorld = new ArrayList<>();
    private List<ApiRoadElement> initialWorldObjects = new ArrayList<>();
    private Map<String, List<Long>> decisionEventSequences = new LinkedHashMap<>();
    private long sequence;

    public SimulationSession() { }

    public SimulationSession(String id, Instant startedAt, long startedAtSimulationMillis,
                             String scenarioId, String backend,
                             List<String> initialWorld) {
        this.id = id;
        this.startedAt = startedAt;
        this.scenarioId = scenarioId;
        this.backend = backend;
        this.startedAtSimulationMillis = startedAtSimulationMillis;
        this.initialWorld.addAll(initialWorld);
    }

    public synchronized void addEvent(long simTime, String type, String message) {
        events.add(new SessionEvent(sequence++, simTime, Instant.now(), type, message));
    }

    public synchronized void addSample(VehicleSample sample) { vehicleSamples.add(sample); }

    public synchronized void addSnapshot(SessionSnapshot snapshot) {
        decisionSnapshots.add(snapshot);
        long previousDecisionTime = decisionSnapshots.size() > 1
            ? decisionSnapshots.get(decisionSnapshots.size() - 2).simulationTimeMillis() : startedAtSimulationMillis;
        decisionEventSequences.put(snapshot.simulationTimeMillis() + ":" + snapshot.decision().action(), events.stream()
            .filter(event -> event.simulationTimeMillis() == snapshot.simulationTimeMillis()
                || (event.simulationTimeMillis() > previousDecisionTime
                    && event.simulationTimeMillis() <= snapshot.simulationTimeMillis()
                    && (event.type().equals("WORLD_CHANGE") || event.type().equals("PERCEPTION_CHANGE"))))
            .map(SessionEvent::sequence).toList());
    }

    public synchronized void finish(Instant stoppedAt) { this.stoppedAt = stoppedAt; }
    public synchronized void setInitialWorldObjects(List<ApiRoadElement> objects) {
        initialWorldObjects = new ArrayList<>(objects);
    }

    public String id() { return id; }
    public Instant startedAt() { return startedAt; }
    public synchronized Instant stoppedAt() { return stoppedAt; }
    public String scenarioId() { return scenarioId; }
    public String backend() { return backend; }
    public long startedAtSimulationMillis() { return startedAtSimulationMillis; }
    public synchronized long durationMillis() {
        return durationMillis;
    }
    public synchronized void includeDurationThrough(long simulationTimeMillis) {
        if (simulationTimeMillis > startedAtSimulationMillis) {
            durationMillis = Math.max(durationMillis, simulationTimeMillis - startedAtSimulationMillis);
        }
    }
    public synchronized List<SessionEvent> events() { return List.copyOf(events); }
    public synchronized List<VehicleSample> vehicleSamples() { return List.copyOf(vehicleSamples); }
    public synchronized List<SessionSnapshot> decisionSnapshots() { return List.copyOf(decisionSnapshots); }
    public synchronized List<String> initialWorld() { return List.copyOf(initialWorld); }
    public synchronized List<ApiRoadElement> initialWorldObjects() { return List.copyOf(initialWorldObjects); }
    public synchronized Map<String, List<Long>> decisionEventSequences() { return Map.copyOf(decisionEventSequences); }

    public String getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public synchronized Instant getStoppedAt() { return stoppedAt; }
    public String getScenarioId() { return scenarioId; }
    public String getBackend() { return backend; }
    public long getStartedAtSimulationMillis() { return startedAtSimulationMillis; }
    public synchronized List<SessionEvent> getEvents() { return List.copyOf(events); }
    public synchronized List<VehicleSample> getVehicleSamples() { return List.copyOf(vehicleSamples); }
    public synchronized List<SessionSnapshot> getDecisionSnapshots() { return List.copyOf(decisionSnapshots); }
    public synchronized List<String> getInitialWorld() { return List.copyOf(initialWorld); }
    public synchronized List<ApiRoadElement> getInitialWorldObjects() { return List.copyOf(initialWorldObjects); }
    public synchronized Map<String, List<Long>> getDecisionEventSequences() { return Map.copyOf(decisionEventSequences); }
}
