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

    @Test
    void genericLaneChangeAndPotholeQuestionsResolveToTheActualManeuverNotTheLastUnrelatedTick() {
        SimulationSession session = new SimulationSession("s2", Instant.EPOCH, 0, "S5", "DROOLS", List.of());
        var vehicle = new ApiSimulationSnapshot.EgoVehicleView(70, 50, "CENTER", 0, 0,
            "RIGHT", "CHANGING", 0.5, "CRUISING", "RIGHT", "RIGHT", 0);
        var environment = new ApiSimulationSnapshot.EnvironmentView("CLEAR", 1, 1);
        var sensors = new ApiSimulationSnapshot.SensorTelemetryView(
            new ApiSimulationSnapshot.RadarTelemetryView(130, "none", Double.POSITIVE_INFINITY, 0,
                Double.POSITIVE_INFINITY, "CLEAR"),
            new ApiSimulationSnapshot.CameraTelemetryView(120, List.of(), "CLEAR"),
            new ApiSimulationSnapshot.BlindSpotTelemetryView(30, false, "none", Double.POSITIVE_INFINITY, "CLEAR"),
            new ApiSimulationSnapshot.BlindSpotTelemetryView(30, false, "none", Double.POSITIVE_INFINITY, "CLEAR"),
            new ApiSimulationSnapshot.EnvironmentTelemetryView("CLEAR", 120, 100, false));

        // The real avoidance decision: CHANGE_LANE_RIGHT, reason/evidence mention the pothole.
        var avoidDecision = new SessionSnapshot.DecisionSnapshot("CHANGE_LANE_RIGHT", "MAINTAIN_SPEED", "CHANGE_LANE_RIGHT",
            "Avoiding pothole by moving into the clear right lane", "Avoid pothole in centre lane by changing right", "Default",
            "Avoid pothole in centre lane by changing right", 70,
            List.of("pothole 49m ahead in own lane, right lane clear"), List.of(), List.of(), "lane=CENTER -> RIGHT");
        session.addSnapshot(new SessionSnapshot(2100, vehicle, environment, sensors, List.of(),
            List.of(new ApiWorkingMemoryRow("PotholeStateFact", "PotholeStateFact{potholeId='r1'}", "WORKING_MEMORY", 2100L, "-")),
            List.of(), avoidDecision, null));

        // A later, unrelated tick: the pothole is still tracked in working memory (passive sensor fact)
        // but the decision itself has nothing to do with lane change or the pothole anymore.
        var laterDecision = new SessionSnapshot.DecisionSnapshot("MAINTAIN_SPEED", "MAINTAIN_SPEED", "KEEP_LANE",
            "No restrictive condition detected", "Default maintain speed baseline", "Default", "Default lane decision",
            70, List.of("baseline speed=70.0 km/h"), List.of(), List.of(), "lane=RIGHT, steering=STRAIGHT");
        session.addSnapshot(new SessionSnapshot(5100, vehicle, environment, sensors, List.of(),
            List.of(new ApiWorkingMemoryRow("PotholeStateFact", "PotholeStateFact{potholeId='r1'}", "WORKING_MEMORY", 4500L, "-")),
            List.of(), laterDecision, null));

        var genericLaneChange = service.answer(session, "Why did the vehicle change lane?", null, "WHY");
        var potholeQuestion = service.answer(session, "Why did it avoid the pothole?", null, "WHY");

        assertEquals("CHANGE_LANE_RIGHT", genericLaneChange.selected());
        assertEquals(2100, genericLaneChange.timestampMillis());
        assertEquals("CHANGE_LANE_RIGHT", potholeQuestion.selected());
        assertEquals(2100, potholeQuestion.timestampMillis());
    }
}
