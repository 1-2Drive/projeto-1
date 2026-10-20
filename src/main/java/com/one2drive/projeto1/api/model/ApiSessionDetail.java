package com.one2drive.projeto1.api.model;

import com.one2drive.projeto1.history.SessionEvent;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.VehicleSample;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiSessionDetail(String id, Instant startedAt, Instant stoppedAt, long durationMillis,
                               long startedAtSimulationMillis,
                               String scenarioId, String backend, List<String> initialWorld,
                               List<ApiRoadElement> initialWorldObjects, List<SessionEvent> events,
                               List<VehicleSample> vehicleSamples, List<SessionSnapshot> decisionSnapshots,
                               Map<String, List<Long>> decisionEventSequences) { }
