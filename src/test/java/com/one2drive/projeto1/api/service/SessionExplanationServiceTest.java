package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiTimelineEntry;
import com.one2drive.projeto1.api.model.ApiWhyAnswer;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.Alternative;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.AlternativeStatus;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.Branch;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.ChannelWhy;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.FlowStep;
import com.one2drive.projeto1.decision.LaneChangeCheck;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.DecisionSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.LaneCheckSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.ProposalSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.SubGoalSnapshot;
import com.one2drive.projeto1.history.SimulationSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionExplanationServiceTest {
    private static final String POTHOLE_AVOID = "Avoid pothole in right lane by changing left";
    private static final String POTHOLE_SLOW = "Pothole ahead reduce speed";
    private static final String CRUISE = "Build back up to the scenario cruise speed when no sign governs the road";

    private final SessionExplanationService service = new SessionExplanationService();

    @Test
    void whyExplainsBothChannelsFromTheRulesThatDecided() {
        ApiWhyAnswer answer = explain(potholeAvoidance());

        ChannelWhy speed = why(answer, "LONGITUDINAL");
        assertEquals("MAINTAIN_SPEED", speed.action());
        assertEquals("speeds up from 42 to 50 km/h", speed.effect(), "MAINTAIN_SPEED towards a higher target builds the speed up");
        assertEquals(CRUISE, speed.decidingRule().rule());
        assertTrue(speed.conditions().contains("cruise=50.0 km/h, limit=120.0 km/h, current=42.0 km/h"));

        ChannelWhy lane = why(answer, "LATERAL");
        assertTrue(lane.relevant());
        assertEquals("moves from the RIGHT to the CENTER lane", lane.effect());
        assertEquals(POTHOLE_AVOID, lane.decidingRule().rule());
        assertEquals("road hazard", lane.decidingRule().rung());
        assertTrue(lane.summary().contains("Avoiding pothole by moving into the clear left lane"));
        assertTrue(lane.conditions().contains("pothole 18.0m ahead in own lane, left lane clear"));
        assertTrue(lane.conditions().contains("Lane safety (left): safe to change left"));
        assertTrue(lane.arbitration().contains("only lane proposal"));
    }

    @Test
    void whyNotListsTheAlternativesNotTakenWithTheNotApplicableOnesLast() {
        ApiWhyAnswer answer = explain(potholeAvoidance());

        List<String> speedOptions = answer.whyNot().stream().filter(a -> a.channel().equals("LONGITUDINAL"))
            .map(Alternative::option).toList();
        List<String> laneOptions = answer.whyNot().stream().filter(a -> a.channel().equals("LATERAL"))
            .map(Alternative::option).toList();
        assertEquals(List.of("HOLD", "REDUCE"), speedOptions, "the taken INCREASE is not an alternative");
        assertEquals(List.of("KEEP_LANE", "CHANGE_LANE_RIGHT", "RETURN_RIGHT"), laneOptions);

        assertEquals(AlternativeStatus.NOT_APPLICABLE, alternative(answer, "CHANGE_LANE_RIGHT").status());
        assertEquals(AlternativeStatus.NOT_APPLICABLE, alternative(answer, "RETURN_RIGHT").status());

        Alternative stay = alternative(answer, "KEEP_LANE");
        assertEquals(AlternativeStatus.CONDITION_NOT_MET, stay.status());
        assertTrue(stay.summary().contains(POTHOLE_AVOID));
        assertTrue(stay.requiredConditions().stream().anyMatch(c -> c.text().equals("No lane rule proposes a maneuver")
            && Boolean.FALSE.equals(c.holds())));

        Alternative slowDown = alternative(answer, "REDUCE");
        assertEquals(AlternativeStatus.CONDITION_NOT_MET, slowDown.status());
        assertTrue(slowDown.requiredConditions().stream().anyMatch(c -> c.text().startsWith("Is a pothole in the vehicle's lane")));
        assertTrue(slowDown.prevailing().contains(CRUISE));
    }

    @Test
    void speedingUpWasOverriddenByALowerSpeedCeiling() {
        var decision = decision("REDUCE_SPEED", POTHOLE_SLOW, 40, 30.0, "KEEP_LANE", "Default lane decision", 0,
            List.of(proposal(POTHOLE_SLOW, "REDUCE_SPEED", "LONGITUDINAL", 40, 30.0, "WON", "",
                    "Pothole ahead requires speed reduction", "distance=18.0m"),
                proposal(CRUISE, "MAINTAIN_SPEED", "LONGITUDINAL", 20, 50.0, "LOST_TO", POTHOLE_SLOW,
                    "Road is clear; building back up to the scenario cruise speed", "cruise=50.0 km/h")),
            List.of(check(Lane.LEFT, Lane.RIGHT, true, true, false, true), check(Lane.RIGHT, Lane.RIGHT, true, false, false, true)),
            "Pothole ahead requires speed reduction", "No lane rule holds");
        ApiWhyAnswer answer = explain(snapshot(5000, 48, "RIGHT", "IDLE", decision, List.of(POTHOLE_SLOW, CRUISE)));

        assertEquals("slows down from 48 to 30 km/h", why(answer, "LONGITUDINAL").effect());
        assertTrue(why(answer, "LONGITUDINAL").arbitration().contains("lowest of 2 ceilings"));
        assertFalse(why(answer, "LATERAL").relevant(), "an untouched default lane decision is not explained at length");

        Alternative speedUp = alternative(answer, "INCREASE");
        assertEquals(AlternativeStatus.OVERRIDDEN_BY_PRIORITY, speedUp.status());
        assertTrue(speedUp.summary().contains("Every speed ceiling binds"));
        assertTrue(speedUp.blockingRules().stream().anyMatch(r -> r.rule().equals(POTHOLE_SLOW) && r.priority() == 40));
        assertTrue(speedUp.blockingRules().stream().anyMatch(r -> r.rule().equals(CRUISE) && r.rung().equals("speed compliance")));

        Alternative left = alternative(answer, "CHANGE_LANE_LEFT");
        assertEquals(AlternativeStatus.CONDITION_NOT_MET, left.status());
        assertTrue(left.summary().contains("not safe"));
        assertTrue(left.requiredConditions().stream().anyMatch(c -> c.text().contains("left side occupied")
            && Boolean.FALSE.equals(c.holds())), "the failing sub-goal of safeToChangeLane is named");
        assertTrue(left.requiredConditions().stream().anyMatch(c -> c.text().contains(POTHOLE_SLOW)),
            "the pothole question records the hazard the speed channel saw");

        FlowStep potholeLane = step(answer, "LATERAL", "F12");
        assertEquals(Branch.NO, potholeLane.branch());
        assertTrue(potholeLane.takenLabel().contains(POTHOLE_SLOW));
        FlowStep potholeSpeed = step(answer, "LONGITUDINAL", "L12");
        assertEquals(Branch.YES, potholeSpeed.branch());
        assertTrue(potholeSpeed.decisive());
        FlowStep cruise = step(answer, "LONGITUDINAL", "R03C");
        assertEquals(Branch.YES, cruise.branch(), "a ceiling does not end the path: the base question is still asked");
        assertFalse(cruise.decisive());
        assertTrue(step(answer, "LONGITUDINAL", "DV").takenLabel().contains("lowest"));
    }

    @Test
    void aLaneChangeVetoedByAMandatoryStopIsExplainedAsOverridden() {
        String hold = "Hold at red traffic light";
        String avoid = "Avoid obstacle in centre lane by changing left";
        var decision = decision("STOP", hold, 60, 0.0, "KEEP_LANE", "Arbitration veto", 50,
            List.of(proposal(hold, "STOP", "LONGITUDINAL", 60, 0.0, "WON", "", "Red light ahead: stop", "distance=12m"),
                proposal(avoid, "CHANGE_LANE_LEFT", "LATERAL", 50, null, "VETOED_BY", hold, "Avoiding obstacle", "obstacle 30m")),
            List.of(check(Lane.LEFT, Lane.CENTER, true, true, true, true)), "Red light ahead: stop",
            "Lane change vetoed: Red light ahead: stop");
        ApiWhyAnswer answer = explain(snapshot(9000, 30, "CENTER", "IDLE", decision, List.of(hold, avoid)));

        assertTrue(why(answer, "LATERAL").summary().contains("vetoed"));
        assertTrue(why(answer, "LATERAL").arbitration().contains("vetoes lane changes"));

        Alternative left = alternative(answer, "CHANGE_LANE_LEFT");
        assertEquals(AlternativeStatus.OVERRIDDEN_BY_PRIORITY, left.status());
        assertTrue(left.blockingRules().stream().anyMatch(r -> r.rule().equals(hold)));

        assertEquals(Branch.YES, step(answer, "LATERAL", "FE").branch());
        FlowStep obstacle = step(answer, "LATERAL", "F13");
        assertEquals(Branch.NOT_REACHED, obstacle.branch());
        assertEquals("VETOED_BY", obstacle.rules().get(0).outcome());

        assertTrue(step(answer, "LONGITUDINAL", "VS").decisive());
        assertEquals(Branch.NOT_REACHED, step(answer, "LONGITUDINAL", "L12").branch(),
            "after a mandatory stop won, the speed is settled");
    }

    @Test
    void aMandatoryStopThatBrakesAtTheCurrentSpeedIsADemandToBrakeNotATargetSpeed() {
        String red = "Red traffic light progressive approach";
        // Under a mandatory stop REDUCE_SPEED brakes whatever its target, here min(speed, limit).
        var decision = decision("REDUCE_SPEED", red, 60, 70.0, "KEEP_LANE", "Default lane decision", 0,
            List.of(proposal(red, "REDUCE_SPEED", "LONGITUDINAL", 60, 70.0, "WON", "",
                "Red traffic light ahead; decelerating to stop before the line", "distance=80m")),
            List.of(), "Red traffic light ahead; decelerating to stop before the line", "No lane rule holds");
        ApiWhyAnswer answer = explain(snapshot(1300, 70, "RIGHT", "IDLE", decision, List.of(red)));

        assertEquals("slows down from 70 km/h", why(answer, "LONGITUDINAL").effect());
        Alternative hold = alternative(answer, "HOLD");
        assertTrue(hold.summary().contains("requires braking now (REDUCE_SPEED, legal mandatory stop)"), hold.summary());
        assertFalse(hold.summary().contains("below the current"), hold.summary());
    }

    @Test
    void slowingDownNamesTheStopRuleThatOnlyHoldsTheSpeed() {
        String red = "Red traffic light progressive approach";
        var decision = decision("MAINTAIN_SPEED", red, 60, 69.0, "KEEP_LANE", "Default lane decision", 0,
            List.of(proposal(red, "MAINTAIN_SPEED", "LONGITUDINAL", 60, 69.0, "WON", "",
                "Red traffic light ahead; decelerating to stop before the line", "distance=110m")),
            List.of(), "Red traffic light ahead; decelerating to stop before the line", "No lane rule holds");
        ApiWhyAnswer answer = explain(snapshot(1400, 69, "RIGHT", "IDLE", decision, List.of(red)));

        assertEquals("keeps its speed of 69 km/h", why(answer, "LONGITUDINAL").effect());
        assertTrue(alternative(answer, "REDUCE").summary().contains("'" + red + "' held, but only to hold the speed"));
    }

    @Test
    void aSpeedCeilingAboveTheCurrentSpeedIsWhatTheVehicleBuildsUpTo() {
        String trend = "Approaching trend from sliding distance window";
        var decision = decision("REDUCE_SPEED", trend, 50, 25.0, "KEEP_LANE", "Default lane decision", 0,
            List.of(proposal(trend, "REDUCE_SPEED", "LONGITUDINAL", 50, 25.0, "WON", "", "Front distance trend decreasing quickly",
                    "distance trend=-4.0m"),
                proposal(CRUISE, "MAINTAIN_SPEED", "LONGITUDINAL", 20, 50.0, "LOST_TO", trend, "Road is clear", "cruise=50.0 km/h")),
            List.of(), "Front distance trend decreasing quickly", "No lane rule holds");
        ApiWhyAnswer answer = explain(snapshot(8000, 11, "RIGHT", "IDLE", decision, List.of(trend, CRUISE)));

        ChannelWhy speed = why(answer, "LONGITUDINAL");
        assertEquals("speeds up from 11 to 25 km/h", speed.effect());
        assertTrue(speed.summary().contains("the lowest speed ceiling, '" + trend + "', still allows up to 25 km/h"), speed.summary());
    }

    @Test
    void anEmergencyEndsTheSpeedPathAndLosersStayVisible() {
        String ttc = "Emergency brake when TTC is immediately critical";
        String closing = "Reduce speed when closing front vehicle";
        var decision = decision("EMERGENCY_BRAKE", ttc, 70, 0.0, "KEEP_LANE", "Default lane decision", 0,
            List.of(proposal(ttc, "EMERGENCY_BRAKE", "LONGITUDINAL", 70, 0.0, "WON", "", "Immediate TTC critical condition", "TTC=1.1s"),
                proposal(closing, "REDUCE_SPEED", "LONGITUDINAL", 50, 35.0, "LOST_TO", ttc, "Closing in", "distance=14m")),
            List.of(), "Immediate TTC critical condition", "No lane rule holds");
        ApiWhyAnswer answer = explain(snapshot(3000, 50, "RIGHT", "IDLE", decision, List.of(ttc, closing)));

        assertEquals("brakes hard from 50 km/h", why(answer, "LONGITUDINAL").effect());
        assertTrue(why(answer, "LONGITUDINAL").arbitration().contains("override every other speed proposal"));
        assertTrue(step(answer, "LONGITUDINAL", "V10").decisive());
        assertEquals(Branch.NOT_REACHED, step(answer, "LONGITUDINAL", "V10B").branch(),
            "the persistent-TTC question is only asked when the immediate one did not hold");
        FlowStep follow = step(answer, "LONGITUDINAL", "L09");
        assertEquals(Branch.NOT_REACHED, follow.branch());
        assertEquals("LOST_TO", follow.rules().get(0).outcome());
        assertEquals(AlternativeStatus.CONDITION_NOT_MET, alternative(answer, "HOLD").status());
    }

    @Test
    void aLaneChangeTheActuatorRefusedIsAFailedExecutionCondition() {
        String allow = "Allow lane change left when lane safe";
        String compliant = "Maintain speed when compliant with detected limit";
        var decision = decision("REDUCE_SPEED", "Actuator safety guard", 50, 40.0, "KEEP_LANE", "Actuator safety guard", 30,
            List.of(proposal(allow, "CHANGE_LANE_LEFT", "LATERAL", 30, null, "REJECTED_BY",
                    "actuator: target lane CENTER is not safe to enter", "Lane change left is safe", "left lane clear"),
                proposal(compliant, "MAINTAIN_SPEED", "LONGITUDINAL", 10, 50.0, "WON", "", "Vehicle is compliant with legal limit", "speed=50")),
            List.of(check(Lane.LEFT, Lane.RIGHT, true, true, true, true)), "Lane-change command rejected",
            "Lane change rejected by the actuator: target lane CENTER is not safe to enter");
        ApiWhyAnswer answer = explain(snapshot(7000, 50, "RIGHT", "IDLE", decision, List.of(allow, compliant)));

        assertTrue(why(answer, "LATERAL").summary().contains("actuator refused"));
        Alternative left = alternative(answer, "CHANGE_LANE_LEFT");
        assertEquals(AlternativeStatus.CONDITION_NOT_MET, left.status());
        assertTrue(left.requiredConditions().stream().anyMatch(c -> c.text().startsWith("Execution check")
            && Boolean.FALSE.equals(c.holds())));
        assertEquals(Branch.NO, step(answer, "LATERAL", "G2").branch());
        assertEquals(AlternativeStatus.CONDITION_NOT_MET, alternative(answer, "HOLD").status());
        assertTrue(alternative(answer, "HOLD").summary().contains("could not be executed"));
    }

    @Test
    void slowingDownIsNotApplicableToAStoppedVehicle() {
        String hold = "Red traffic light requires full stop and hold";
        var decision = decision("STOP", hold, 60, 0.0, "KEEP_LANE", "Default lane decision", 0,
            List.of(proposal(hold, "STOP", "LONGITUDINAL", 60, 0.0, "WON", "", "Red light: hold", "stopped at the line")),
            List.of(), "Red light: hold", "No lane rule holds");
        ApiWhyAnswer answer = explain(snapshot(4000, 0, "RIGHT", "IDLE", decision, List.of(hold)));

        assertEquals("stays stopped", why(answer, "LONGITUDINAL").effect());
        List<Alternative> speed = answer.whyNot().stream().filter(a -> a.channel().equals("LONGITUDINAL")).toList();
        assertEquals(List.of("INCREASE", "REDUCE"), speed.stream().map(Alternative::option).toList());
        assertEquals(AlternativeStatus.NOT_APPLICABLE, speed.get(1).status());
    }

    @Test
    void prologVariantRulesFollowTheSameFlowchart() {
        String brake = "Brake for pedestrian proximity (brake)";
        var decision = decision("BRAKE", brake, 60, 20.0, "KEEP_LANE", "Default lane decision", 0,
            List.of(proposal(brake, "BRAKE", "LONGITUDINAL", 60, 20.0, "WON", "", "Pedestrian ahead in path; prepare stop",
                "pedestrian(p1, 40.0)")),
            List.of(), "Pedestrian ahead in path; prepare stop", "No lane rule holds");
        SimulationSession session = new SimulationSession("p", Instant.EPOCH, 0, "S8", "PROLOG", List.of());
        session.addSnapshot(snapshot(2000, 45, "RIGHT", "IDLE", decision, List.of(brake)));

        ApiWhyAnswer answer = service.answer(session, 2000L);

        assertEquals("PROLOG", answer.engine());
        FlowStep pedestrian = step(answer, "LONGITUDINAL", "V08");
        assertEquals(Branch.YES, pedestrian.branch());
        assertTrue(pedestrian.decisive());
        assertTrue(why(answer, "LONGITUDINAL").conditions().contains("pedestrian(p1, 40.0)"));
    }

    @Test
    void aSnapshotSavedBeforeThePerChannelFieldsFallsBackToItsProposals() {
        var legacy = new DecisionSnapshot("CHANGE_LANE_LEFT", "MAINTAIN_SPEED", "CHANGE_LANE_LEFT", "Avoiding pothole",
            POTHOLE_AVOID, "Maintain speed when compliant with detected limit", POTHOLE_AVOID, 42,
            List.of("pothole 18 m ahead"),
            List.of(proposal(POTHOLE_AVOID, "CHANGE_LANE_LEFT", "LATERAL", 40, null, "WON", "", "Avoiding pothole", "pothole 18 m ahead"),
                proposal("Maintain speed when compliant with detected limit", "MAINTAIN_SPEED", "LONGITUDINAL", 10, 42.0,
                    "WON", "", "Vehicle is compliant with legal limit", "speed=42")),
            null, "steer left", null, null, null, null, null, null);
        ApiWhyAnswer answer = explain(snapshot(1000, 42, "RIGHT", "IDLE", legacy, List.of()));

        assertEquals("Vehicle is compliant with legal limit", why(answer, "LONGITUDINAL").decidingRule().reason());
        assertEquals(List.of("speed=42"), why(answer, "LONGITUDINAL").conditions());
        assertEquals(40, why(answer, "LATERAL").decidingRule().priority());
        assertTrue(alternative(answer, "CHANGE_LANE_RIGHT").status() == AlternativeStatus.NOT_APPLICABLE);
    }

    @Test
    void theRequestedTimestampSelectsTheDecisionInForceAtThatTime() {
        SimulationSession session = new SimulationSession("t", Instant.EPOCH, 0, "S1", "DROOLS", List.of());
        session.addSnapshot(potholeAvoidance());
        var later = decision("MAINTAIN_SPEED", "Default maintain speed baseline", 10, 50.0, "KEEP_LANE",
            "Default lane decision", 0, List.of(proposal("Default maintain speed baseline", "MAINTAIN_SPEED", "LONGITUDINAL",
                10, 50.0, "WON", "", "No restrictive condition detected", "baseline speed=50.0 km/h")),
            List.of(), "No restrictive condition detected", "No lane rule holds");
        session.addSnapshot(snapshot(20000, 50, "CENTER", "IDLE", later, List.of()));

        assertEquals(14400, service.answer(session, 15000L).timestampMillis());
        assertEquals(20000, service.answer(session, null).timestampMillis());
        assertEquals(14400, service.answer(session, 100L).timestampMillis(), "before the first decision: the first one");
        assertEquals("keeps its speed of 50 km/h", why(service.answer(session, null), "LONGITUDINAL").effect());
        assertTrue(why(service.answer(session, null), "LONGITUDINAL").summary().contains("no rule found a condition"));
    }

    @Test
    void anEmptySessionHasNothingToExplain() {
        SimulationSession session = new SimulationSession("e", Instant.EPOCH, 0, "S1", "DROOLS", List.of());
        ApiWhyAnswer answer = service.answer(session, null);
        assertTrue(answer.why().isEmpty());
        assertTrue(answer.whyNot().isEmpty());
        assertTrue(answer.how().isEmpty());
    }

    // ---- fixtures -----------------------------------------------------------------------

    /** The ego in the RIGHT lane steers left around a pothole while building back up to its cruise speed. */
    private static SessionSnapshot potholeAvoidance() {
        var decision = decision("MAINTAIN_SPEED", CRUISE, 20, 50.0, "CHANGE_LANE_LEFT", POTHOLE_AVOID, 40,
            List.of(proposal(POTHOLE_AVOID, "CHANGE_LANE_LEFT", "LATERAL", 40, null, "WON", "",
                    "Avoiding pothole by moving into the clear left lane", "pothole 18.0m ahead in own lane, left lane clear"),
                proposal(CRUISE, "MAINTAIN_SPEED", "LONGITUDINAL", 20, 50.0, "WON", "",
                    "Road is clear; building back up to the scenario cruise speed",
                    "cruise=50.0 km/h, limit=120.0 km/h, current=42.0 km/h")),
            List.of(check(Lane.LEFT, Lane.RIGHT, true, true, true, true), check(Lane.RIGHT, Lane.RIGHT, true, false, false, true)),
            "Road is clear; building back up to the scenario cruise speed", "Avoiding pothole by moving into the clear left lane");
        return snapshot(14400, 42, "RIGHT", "IDLE", decision, List.of(POTHOLE_AVOID, CRUISE));
    }

    private ApiWhyAnswer explain(SessionSnapshot snapshot) {
        SimulationSession session = new SimulationSession("s", Instant.EPOCH, 0, "S1", "DROOLS", List.of());
        session.addSnapshot(snapshot);
        return service.answer(session, snapshot.simulationTimeMillis());
    }

    private static SessionSnapshot snapshot(long time, double speed, String lane, String laneChangeState,
                                            DecisionSnapshot decision, List<String> fired) {
        var vehicle = new ApiSimulationSnapshot.EgoVehicleView(speed, 100, lane, 0, 0, lane, laneChangeState, 0,
            "CRUISING", "OFF", "STRAIGHT", 0);
        var sensors = new ApiSimulationSnapshot.SensorTelemetryView(
            new ApiSimulationSnapshot.RadarTelemetryView(130, "none", Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY, "CLEAR"),
            new ApiSimulationSnapshot.CameraTelemetryView(120,
                List.of(new ApiSimulationSnapshot.CameraDetectionView("p1", "Pothole", 18.0, lane)), "DETECTED"),
            new ApiSimulationSnapshot.BlindSpotTelemetryView(30, false, "none", Double.POSITIVE_INFINITY, "CLEAR"),
            new ApiSimulationSnapshot.BlindSpotTelemetryView(30, false, "none", Double.POSITIVE_INFINITY, "NO LANE"),
            new ApiSimulationSnapshot.EnvironmentTelemetryView("CLEAR", 120, 100, false));
        List<ApiTimelineEntry> timeline = fired.stream()
            .map(rule -> new ApiTimelineEntry(time, "RULE_FIRED", "RULE FIRED \"" + rule + "\"")).toList();
        return new SessionSnapshot(time, vehicle, new ApiSimulationSnapshot.EnvironmentView("CLEAR", 1, 1), sensors,
            List.of(), List.of(), timeline, decision, null);
    }

    private static DecisionSnapshot decision(String speedAction, String speedRule, int speedPriority, double target,
                                             String laneAction, String laneRule, int lanePriority,
                                             List<ProposalSnapshot> proposals, List<LaneCheckSnapshot> checks,
                                             String speedReason, String laneReason) {
        boolean maneuver = !laneAction.equals("KEEP_LANE");
        String headline = maneuver ? laneAction : speedAction;
        return new DecisionSnapshot(headline, speedAction, laneAction, maneuver ? laneReason : speedReason,
            maneuver ? laneRule : speedRule, speedRule, laneRule, target, List.of(), proposals, checks, "actuator",
            speedReason, laneReason, speedPriority, lanePriority,
            proposals.stream().filter(p -> p.rule().equals(speedRule)).findFirst().map(ProposalSnapshot::evidence).orElse(List.of()),
            proposals.stream().filter(p -> p.rule().equals(laneRule)).findFirst().map(ProposalSnapshot::evidence).orElse(List.of()));
    }

    private static ProposalSnapshot proposal(String rule, String action, String channel, int priority, Double speed,
                                             String outcome, String detail, String reason, String evidence) {
        return new ProposalSnapshot(rule, action, channel, priority, speed, outcome, detail, reason, List.of(evidence), "");
    }

    /** A lane check exactly as the engine records it, from the sub-goals of safeToChangeLane. */
    private static LaneCheckSnapshot check(Lane side, Lane ego, boolean idle, boolean line, boolean clear, boolean room) {
        LaneChangeCheck check = new LaneChangeCheck(side, ego, idle, line, clear, room);
        return new LaneCheckSnapshot(side.name(), check.allowed(), check.explanation(),
            check.subGoals().stream().map(g -> new SubGoalSnapshot(g.goal(), g.holds(), g.detail())).toList());
    }

    private static ChannelWhy why(ApiWhyAnswer answer, String channel) {
        return answer.why().stream().filter(w -> w.channel().equals(channel)).findFirst().orElseThrow();
    }

    private static Alternative alternative(ApiWhyAnswer answer, String option) {
        return answer.whyNot().stream().filter(a -> a.option().equals(option)).findFirst()
            .orElseThrow(() -> new AssertionError("no alternative " + option + " in " + answer.whyNot()));
    }

    private static FlowStep step(ApiWhyAnswer answer, String channel, String nodeId) {
        return answer.how().stream().filter(f -> f.channel().equals(channel)).findFirst().orElseThrow()
            .steps().stream().filter(s -> s.nodeId().equals(nodeId)).findFirst()
            .orElseThrow(() -> new AssertionError("no step " + nodeId));
    }
}
