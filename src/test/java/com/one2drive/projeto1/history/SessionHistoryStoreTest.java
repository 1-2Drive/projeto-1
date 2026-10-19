package com.one2drive.projeto1.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.one2drive.projeto1.api.model.ApiRoadElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionHistoryStoreTest {
    @TempDir Path directory;

    @Test
    void completedSessionSurvivesReadAndKeepsOrderedEventsAndScenario() {
        SessionHistoryStore store = new SessionHistoryStore(directory.toString());
        SimulationSession session = new SimulationSession("session-1", Instant.parse("2026-10-19T12:00:00Z"),
            0, "S1", "DROOLS", List.of("roadMap=[[0,0,0]]"));
        session.setInitialWorldObjects(List.of(new ApiRoadElement("r1-RIGHT", "Pothole", 5,
            "RIGHT", "severity=HIGH", 5)));
        session.addEvent(100, "PERCEPTION_CHANGE", "Camera detected pothole");
        session.addEvent(100, "DECISION", "CHANGE_LANE_LEFT");
        session.includeDurationThrough(100);
        session.finish(Instant.parse("2026-10-19T12:00:01Z"));

        store.save(session);
        SimulationSession loaded = store.get("session-1").orElseThrow();

        assertEquals("S1", loaded.scenarioId());
        assertEquals("DROOLS", loaded.backend());
        assertEquals(100, loaded.durationMillis());
        assertEquals(List.of("PERCEPTION_CHANGE", "DECISION"),
            loaded.events().stream().map(SessionEvent::type).toList());
        assertEquals("Pothole", loaded.initialWorldObjects().get(0).type());
        assertTrue(loaded.stoppedAt() != null);
    }
}
