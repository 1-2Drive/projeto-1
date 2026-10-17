package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arbitration on its own: what the two channels make of a cycle's proposals. */
class DecisionContextTest {

    private static DrivingDecision arbitrate(DecisionContext context) {
        DrivingDecision decision = new DrivingDecision();
        context.applyTo(decision);
        return decision;
    }

    private static DecisionProposal proposal(DrivingDecision decision, String rule) {
        return decision.getProposals().stream().filter(p -> p.getSourceRule().equals(rule)).findFirst().orElseThrow();
    }

    private static void propose(DecisionContext ctx, DrivingAction action, double speed, int priority, String rule) {
        ctx.propose(action, speed, RiskLevel.MODERATE, priority, rule + " reason", rule, List.of());
    }

    @Test
    @DisplayName("speed ceilings combine by minimum whatever their rung")
    void ceilingsCombineByMinimum() {
        DecisionContext ctx = new DecisionContext();
        ctx.beginCycle(1000, 70);
        propose(ctx, DrivingAction.REDUCE_SPEED, 60, DecisionPriority.COLLISION_AVOIDANCE, "front vehicle");
        propose(ctx, DrivingAction.REDUCE_SPEED, 45, DecisionPriority.ROAD_HAZARD, "grip");
        propose(ctx, DrivingAction.MAINTAIN_SPEED, 90, DecisionPriority.COMFORT_PROGRESS, "limit");
        DrivingDecision d = arbitrate(ctx);

        assertEquals(45, d.getRecommendedSpeedKmh(), 1e-9);
        assertEquals("grip", d.getSpeedSourceRule());
        assertEquals(DecisionProposal.Outcome.LOST_TO, proposal(d, "front vehicle").getOutcome());
        assertEquals("grip", proposal(d, "front vehicle").getDetail());
    }

    @Test
    @DisplayName("the base speed is capped by the ceilings, not replaced by them")
    void ceilingsCapTheBase() {
        DecisionContext ctx = new DecisionContext();
        ctx.beginCycle(1000, 0);
        propose(ctx, DrivingAction.CONTINUE, 30, DecisionPriority.MANEUVER_SAFETY, "move off");
        propose(ctx, DrivingAction.MAINTAIN_SPEED, 90, DecisionPriority.SPEED_COMPLIANCE, "cruise");
        DrivingDecision d = arbitrate(ctx);
        assertEquals(DrivingAction.CONTINUE, d.getSpeedAction(), "a ceiling above the base does not replace it");
        assertEquals(30, d.getRecommendedSpeedKmh(), 1e-9);

        ctx.beginCycle(1100, 0);
        propose(ctx, DrivingAction.CONTINUE, 30, DecisionPriority.MANEUVER_SAFETY, "move off");
        propose(ctx, DrivingAction.REDUCE_SPEED, 20, DecisionPriority.COLLISION_AVOIDANCE, "front vehicle");
        d = arbitrate(ctx);
        assertEquals("front vehicle", d.getSpeedSourceRule(), "a ceiling below the base binds");
        assertEquals(20, d.getRecommendedSpeedKmh(), 1e-9);
    }

    @Test
    @DisplayName("a mandatory stop keeps the action but a lower ceiling still sets the speed")
    void mandatoryStopIsCappedByALowerCeiling() {
        DecisionContext ctx = new DecisionContext();
        ctx.beginCycle(1000, 50);
        propose(ctx, DrivingAction.MAINTAIN_SPEED, 45, DecisionPriority.LEGAL_MANDATORY_STOP, "red light approach");
        propose(ctx, DrivingAction.BRAKE, 20, DecisionPriority.COLLISION_AVOIDANCE, "obstacle ahead");
        DrivingDecision d = arbitrate(ctx);

        assertEquals(DrivingAction.MAINTAIN_SPEED, d.getSpeedAction());
        assertEquals(DecisionPriority.LEGAL_MANDATORY_STOP, d.getPriority());
        assertEquals("red light approach", d.getSpeedSourceRule());
        assertEquals(20, d.getRecommendedSpeedKmh(), 1e-9);
        assertEquals(DecisionProposal.Outcome.CAPPED, proposal(d, "obstacle ahead").getOutcome());
        assertTrue(d.getSpeedEvidence().stream().anyMatch(e -> e.contains("capped at 20 km/h by obstacle ahead")));
    }

    @Test
    @DisplayName("a mandatory stop vetoes a lane maneuver only while braking to stop")
    void maneuverIsVetoedOnlyWhileBrakingToStop() {
        DecisionContext ctx = new DecisionContext();
        ctx.beginCycle(1000, 50);
        propose(ctx, DrivingAction.STOP, 0, DecisionPriority.LEGAL_MANDATORY_STOP, "pedestrian");
        propose(ctx, DrivingAction.CHANGE_LANE_LEFT, Double.NaN, DecisionPriority.COLLISION_AVOIDANCE, "avoid obstacle");
        DrivingDecision d = arbitrate(ctx);
        assertEquals(DrivingAction.KEEP_LANE, d.getLaneAction());
        assertEquals(DecisionProposal.Outcome.VETOED_BY, proposal(d, "avoid obstacle").getOutcome());
        assertEquals("pedestrian", proposal(d, "avoid obstacle").getDetail());

        ctx.beginCycle(1100, 50);
        propose(ctx, DrivingAction.REDUCE_SPEED, 40, DecisionPriority.LEGAL_MANDATORY_STOP, "red light approach");
        propose(ctx, DrivingAction.CHANGE_LANE_LEFT, Double.NaN, DecisionPriority.COLLISION_AVOIDANCE, "avoid obstacle");
        d = arbitrate(ctx);
        assertEquals(DrivingAction.CHANGE_LANE_LEFT, d.getLaneAction(), "slowing for a far stop does not forbid the maneuver");
        assertEquals(DecisionProposal.Outcome.WON, proposal(d, "avoid obstacle").getOutcome());
        assertEquals(DrivingAction.CHANGE_LANE_LEFT, d.getAction(), "the maneuver is the headline while it executes");
        assertEquals(DrivingAction.REDUCE_SPEED, d.getSpeedAction());
    }

    @Test
    @DisplayName("lane proposals: highest rung, then return right over change over keep")
    void laneTieBreaks() {
        DecisionContext ctx = new DecisionContext();
        ctx.beginCycle(1000, 50);
        propose(ctx, DrivingAction.CHANGE_LANE_LEFT, Double.NaN, DecisionPriority.MANEUVER_SAFETY, "overtake");
        propose(ctx, DrivingAction.KEEP_LANE, Double.NaN, DecisionPriority.MANEUVER_SAFETY, "block right");
        propose(ctx, DrivingAction.CHANGE_LANE_RIGHT, Double.NaN, DecisionPriority.ROAD_HAZARD, "avoid pothole");
        DrivingDecision d = arbitrate(ctx);
        assertEquals("avoid pothole", d.getLaneSourceRule(), "the higher rung wins");

        ctx.beginCycle(1100, 50);
        propose(ctx, DrivingAction.KEEP_LANE, Double.NaN, DecisionPriority.MANEUVER_SAFETY, "block right");
        propose(ctx, DrivingAction.CHANGE_LANE_LEFT, Double.NaN, DecisionPriority.MANEUVER_SAFETY, "overtake");
        d = arbitrate(ctx);
        assertEquals("overtake", d.getLaneSourceRule(), "KEEP_LANE is only the explained default");

        ctx.beginCycle(1200, 50);
        propose(ctx, DrivingAction.CHANGE_LANE_LEFT, Double.NaN, DecisionPriority.MANEUVER_SAFETY, "overtake");
        propose(ctx, DrivingAction.RETURN_RIGHT, Double.NaN, DecisionPriority.MANEUVER_SAFETY, "keep right");
        d = arbitrate(ctx);
        assertEquals("keep right", d.getLaneSourceRule(), "returning right is the more conservative maneuver");
    }
}
