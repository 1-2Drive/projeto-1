package com.one2drive.projeto1.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DecisionPriority;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSnapshotTest {

    @Test
    void recordsEachChannelAndALaneChangeTheActuatorRefused() {
        DecisionContext context = new DecisionContext();
        context.beginCycle(1000L, 50.0);
        context.propose(DrivingAction.CHANGE_LANE_LEFT, Double.NaN, RiskLevel.LOW, DecisionPriority.MANEUVER_SAFETY,
            "Lane change left is safe", "Allow lane change left when lane safe", List.of("left lane clear"));
        context.propose(DrivingAction.MAINTAIN_SPEED, 50.0, RiskLevel.LOW, DecisionPriority.COMFORT_PROGRESS,
            "Vehicle is compliant with legal limit", "Maintain speed when compliant with detected limit",
            List.of("speed=50.0 km/h, limit=50.0 km/h"));
        DrivingDecision decision = new DrivingDecision();
        context.applyTo(decision);
        decision.rejectLane("target lane CENTER is not safe to enter");

        SessionSnapshot.DecisionSnapshot snapshot = SessionSnapshot.DecisionSnapshot.from(decision, "action=MAINTAIN_SPEED");

        assertEquals("KEEP_LANE", snapshot.laneAction());
        assertEquals("Actuator safety guard", snapshot.laneSourceRule());
        assertTrue(snapshot.proposals().stream().anyMatch(p -> p.outcome().equals("REJECTED_BY")
            && p.detail().contains("not safe to enter")));
        assertEquals("Vehicle is compliant with legal limit", snapshot.speedReason());
        assertEquals(DecisionPriority.COMFORT_PROGRESS, snapshot.speedPriority());
        assertEquals(List.of("speed=50.0 km/h, limit=50.0 km/h"), snapshot.speedEvidence());
        assertTrue(snapshot.laneEvidence().get(0).contains("proposed CHANGE_LANE_LEFT"));
    }

    @Test
    void aSnapshotSavedBeforeThePerChannelFieldsStillLoads() throws Exception {
        String saved = """
            {"action":"MAINTAIN_SPEED","speedAction":"MAINTAIN_SPEED","laneAction":"KEEP_LANE",
             "reason":"No restrictive condition detected","sourceRule":"Default maintain speed baseline",
             "speedSourceRule":"Default maintain speed baseline","laneSourceRule":"Default lane decision",
             "recommendedSpeedKmh":50.0,"evidence":["baseline speed=50.0 km/h"],"proposals":[],"laneChecks":[],
             "actuatorResult":"action=MAINTAIN_SPEED"}
            """;

        SessionSnapshot.DecisionSnapshot snapshot = new ObjectMapper().readValue(saved, SessionSnapshot.DecisionSnapshot.class);

        assertNull(snapshot.speedPriority());
        assertNull(snapshot.speedReason());
        assertEquals(List.of(), snapshot.speedEvidence());
        assertEquals(List.of(), snapshot.laneEvidence());
        assertEquals("Default lane decision", snapshot.laneSourceRule());
    }
}
