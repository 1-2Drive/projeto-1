package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiTimelineEntry;
import com.one2drive.projeto1.api.model.ApiWorkingMemoryRow;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.SimulationSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionExplanationServiceTest {
    private final SessionExplanationService service = new SessionExplanationService();

    @Test
    void whyAndWhyNotUseTheSelectedHistoricalSnapshotOnly() {
        SimulationSession session = new SimulationSession("s", Instant.EPOCH, 0, "S1", "DROOLS", List.of());
        var vehicle = new ApiSimulationSnapshot.EgoVehicleView(42, 100, "RIGHT", 0, 3.5,
            "RIGHT", "IDLE", 0, "CRUISING", "LEFT", "STRAIGHT", 0);
        var environment = new ApiSimulationSnapshot.EnvironmentView("CLEAR", 1, 1);
        var sensors = new ApiSimulationSnapshot.SensorTelemetryView(
            new ApiSimulationSnapshot.RadarTelemetryView(130, "none", Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, "CLEAR"),
            new ApiSimulationSnapshot.CameraTelemetryView(120, List.of(), "CLEAR"),
            new ApiSimulationSnapshot.BlindSpotTelemetryView(30, false, "none", Double.POSITIVE_INFINITY, "CLEAR"),
            new ApiSimulationSnapshot.BlindSpotTelemetryView(30, true, "vehicle", 4, "OCCUPIED"),
            new ApiSimulationSnapshot.EnvironmentTelemetryView("CLEAR", 120, 100, false));
        var decision = new SessionSnapshot.DecisionSnapshot("CHANGE_LANE_LEFT", "MAINTAIN_SPEED", "CHANGE_LANE_LEFT",
            "Avoiding pothole", "Avoid pothole in right lane by changing left", "Comfort", "Avoid pothole in right lane by changing left",
            42, List.of("pothole 18 m ahead"), List.of(new SessionSnapshot.ProposalSnapshot(
                "Avoid pothole in right lane by changing left", "CHANGE_LANE_LEFT", "LATERAL", 40,
                null, "WON", "", "Avoiding pothole", List.of("pothole 18 m ahead"), "distance within threshold")),
            List.of(), "steer left");
        session.addSnapshot(new SessionSnapshot(14400, vehicle, environment, sensors, List.of(),
            List.of(new ApiWorkingMemoryRow("PotholeStateFact", "Pothole 18 m ahead", "WORKING_MEMORY", 14400L, "-")),
            List.of(new ApiTimelineEntry(14400, "PERCEPTION_CHANGE", "Camera detected pothole 18 m ahead"),
                new ApiTimelineEntry(14400, "RULE_FIRED", "Avoid pothole in right lane by changing left")), decision, null));
        var laterVehicle = new ApiSimulationSnapshot.EgoVehicleView(0, 400, "LEFT", 0, 1.5,
            "LEFT", "IDLE", 0, "STOPPED", "OFF", "STRAIGHT", 0);
        session.addSnapshot(new SessionSnapshot(37200, laterVehicle, environment, sensors, List.of(), List.of(), List.of(),
            new SessionSnapshot.DecisionSnapshot("STOP", "STOP", "KEEP_LANE", "stopped", "stop rule", "stop rule",
                "default", 0, List.of(), List.of(), List.of(), "brake"), null));

        var why = service.answer(session, "Why did you move left?", 14400L, "WHY");
        var whyNot = service.answer(session, "Why did you move left instead of braking?", 14400L, "WHY_NOT");
        var how = service.answer(session, "How did it decide to change lane?", 14400L, "HOW");

        assertEquals(14400, why.timestampMillis());
        assertTrue(why.observations().stream().anyMatch(s -> s.contains("pothole")));
        assertTrue(why.rules().stream().anyMatch(s -> s.contains("Avoid pothole in right lane by changing left")));
        assertTrue(whyNot.whyNot().stream().anyMatch(s -> s.contains("BRAKE was not proposed")));
        assertEquals("CHANGE_LANE_LEFT", why.selected());

        assertEquals(6, how.how().size());
        assertEquals("WORLD", how.how().get(0).stage());
        assertEquals("SENSORS", how.how().get(1).stage());
        assertEquals("WORKING MEMORY", how.how().get(2).stage());
        assertTrue(how.how().get(3).stage().startsWith("RULE EVALUATION"));
        assertEquals("DECISION / ARBITRATION", how.how().get(4).stage());
        assertEquals("ACTUATOR", how.how().get(5).stage());
        assertTrue(how.how().get(3).evidence().stream().anyMatch(e -> e.contains("Avoid pothole in right lane by changing left")));
        assertTrue(how.how().get(4).evidence().stream().anyMatch(e -> e.contains("CHANGE_LANE_LEFT")));
    }
}
