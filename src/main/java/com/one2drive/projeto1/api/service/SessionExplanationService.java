package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiWhyAnswer;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.ChannelFlow;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.ChannelWhy;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.RuleRef;
import com.one2drive.projeto1.decision.SpeedIntent;
import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.SimulationSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.one2drive.projeto1.api.service.RecordedCycle.quote;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.ACTUATOR_GUARD;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.ARBITRATION_VETO;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_LANE_DECISION;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_SPEED_DECISION;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_SPEED_RULE;

/**
 * Explains one recorded decision of a saved session, per channel: why the speed and the lane
 * decisions were made, why each alternative was not, and how the cycle went through the
 * decision flowchart. Everything comes from the saved cycle (proposals and their arbitration
 * outcomes, lane-safety sub-goals, rule firings, perception); nothing is re-evaluated.
 */
@Service
public class SessionExplanationService {

    public ApiWhyAnswer answer(SimulationSession session, Long timestampMillis) {
        List<SessionSnapshot> snapshots = session.decisionSnapshots();
        if (snapshots.isEmpty()) {
            return new ApiWhyAnswer(session.id(), 0, session.backend(), null, List.of(), List.of(), List.of());
        }
        RecordedCycle cycle = new RecordedCycle(select(snapshots, timestampMillis));
        List<ChannelFlow> how = new FlowchartPathBuilder(cycle).build();
        return new ApiWhyAnswer(session.id(), cycle.snapshot().simulationTimeMillis(), session.backend(),
            new ApiWhyAnswer.Situation(cycle.speedKmh(), cycle.lane().name(), cycle.laneChangeState(),
                cycle.targetKmh(), cycle.decision().actuatorResult()),
            List.of(speedWhy(cycle), laneWhy(cycle)), new AlternativeExplainer(cycle, how).build(), how);
    }

    /** The snapshot at the timestamp, else the last one before it, else the first one. */
    private static SessionSnapshot select(List<SessionSnapshot> snapshots, Long timestampMillis) {
        if (timestampMillis == null) {
            return snapshots.get(snapshots.size() - 1);
        }
        SessionSnapshot selected = snapshots.get(0);
        for (SessionSnapshot snapshot : snapshots) {
            if (snapshot.simulationTimeMillis() <= timestampMillis) {
                selected = snapshot;
            }
        }
        return selected;
    }

    private static ChannelWhy speedWhy(RecordedCycle cycle) {
        String rule = cycle.speedRule();
        String effect = cycle.speedEffect();
        String summary = switch (rule) {
            case DEFAULT_SPEED_DECISION, DEFAULT_SPEED_RULE ->
                "The vehicle " + effect + " because no rule found a condition that requires speeding up or slowing down.";
            case ACTUATOR_GUARD ->
                "The vehicle " + effect + " because the actuator could not execute the lane change the rules chose.";
            // A speed ceiling above the current speed: the vehicle builds up to what it still allows.
            default -> cycle.speedIntent() == SpeedIntent.INCREASE && cycle.speedAction() == DrivingAction.REDUCE_SPEED
                ? "The vehicle " + effect + ": the lowest speed ceiling, " + quote(rule) + ", still allows up to "
                    + RecordedCycle.kmh(cycle.targetKmh()) + " km/h"
                    + (cycle.speedReason().isBlank() ? "." : " (" + cycle.speedReason() + ").")
                : "The vehicle " + effect + " because " + quote(rule) + " holds"
                    + (cycle.speedReason().isBlank() ? "." : ": " + cycle.speedReason() + ".");
        };
        return new ChannelWhy(DecisionChannel.LONGITUDINAL.name(), "Speed · longitudinal", cycle.speedAction().name(),
            effect, true, summary, cycle.decidingRef(DecisionChannel.LONGITUDINAL), cycle.speedEvidence(),
            cycle.speedArbitration(), alsoConsidered(cycle, DecisionChannel.LONGITUDINAL, rule));
    }

    private static ChannelWhy laneWhy(RecordedCycle cycle) {
        String rule = cycle.laneRule();
        DrivingAction action = cycle.laneAction();
        String effect = cycle.laneEffect();
        boolean relevant = !cycle.proposals(DecisionChannel.LATERAL).isEmpty() || action != DrivingAction.KEEP_LANE
            || !DEFAULT_LANE_DECISION.equals(rule);
        String summary = switch (rule) {
            case DEFAULT_LANE_DECISION -> "The vehicle " + effect + " because no lane rule found a reason to change lane.";
            case ARBITRATION_VETO ->
                "The vehicle " + effect + " because a lane change was proposed, but an emergency or mandatory stop vetoed it.";
            case ACTUATOR_GUARD -> "The vehicle " + effect + " because the actuator refused the lane change: "
                + cycle.rejectionDetail().orElse("no detail recorded") + ".";
            default -> "The vehicle " + effect + " because " + quote(rule) + " holds"
                + (cycle.laneReason().isBlank() ? "." : ": " + cycle.laneReason() + ".");
        };
        List<String> conditions = new ArrayList<>(cycle.laneEvidence());
        if (action.isLaneManeuver() && cycle.maneuverIdle()) {
            Lane side = action == DrivingAction.CHANGE_LANE_LEFT ? Lane.LEFT : Lane.RIGHT;
            cycle.laneCheck(side).ifPresent(check -> conditions.add(
                "Lane safety (" + side.name().toLowerCase() + "): " + check.explanation()));
        }
        return new ChannelWhy(DecisionChannel.LATERAL.name(), "Lane · lateral", action.name(), effect, relevant, summary,
            cycle.decidingRef(DecisionChannel.LATERAL), conditions, cycle.laneArbitration(),
            alsoConsidered(cycle, DecisionChannel.LATERAL, rule));
    }

    private static List<RuleRef> alsoConsidered(RecordedCycle cycle, DecisionChannel channel, String decidingRule) {
        return cycle.proposals(channel).stream().filter(p -> !p.rule().equals(decidingRule)).map(cycle::ref).toList();
    }
}
