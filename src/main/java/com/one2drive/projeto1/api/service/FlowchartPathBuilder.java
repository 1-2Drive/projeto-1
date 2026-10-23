package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiWhyAnswer.Branch;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.ChannelFlow;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.Condition;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.FlowStep;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.RuleRef;
import com.one2drive.projeto1.decision.DecisionPriority;
import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.explanation.DecisionFlowchart;
import com.one2drive.projeto1.explanation.DecisionFlowchart.Node;
import com.one2drive.projeto1.history.SessionSnapshot.LaneCheckSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.ProposalSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.one2drive.projeto1.api.service.RecordedCycle.CAPPED;
import static com.one2drive.projeto1.api.service.RecordedCycle.LOST_TO;
import static com.one2drive.projeto1.api.service.RecordedCycle.REJECTED_BY;
import static com.one2drive.projeto1.api.service.RecordedCycle.VETOED_BY;
import static com.one2drive.projeto1.api.service.RecordedCycle.WON;
import static com.one2drive.projeto1.api.service.RecordedCycle.kmh;
import static com.one2drive.projeto1.api.service.RecordedCycle.quote;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.ACTUATOR_GUARD;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_SPEED_DECISION;

/**
 * The path one recorded cycle took through the decision flowchart, per channel. A question
 * took its "yes" branch when one of its rules proposed something in the cycle and its "no"
 * branch otherwise. The path ends where the arbitration settled the channel: on the speed
 * side at an emergency or mandatory stop that won (speed ceilings all bind, so the path goes
 * on through them to the lowest), on the lane side at the proposal that won. The questions
 * after that are listed as not on the decision path, with whatever their rules proposed.
 */
final class FlowchartPathBuilder {
    private final RecordedCycle cycle;
    private final Set<String> fired;

    FlowchartPathBuilder(RecordedCycle cycle) {
        this.cycle = cycle;
        this.fired = cycle.firedRules();
    }

    List<ChannelFlow> build() {
        return List.of(longitudinal(), lateral());
    }

    private ChannelFlow longitudinal() {
        List<ProposalSnapshot> mine = cycle.proposals(DecisionChannel.LONGITUDINAL);
        List<ProposalSnapshot> placed = new ArrayList<>();
        List<FlowStep> steps = new ArrayList<>();
        String settledAt = null;
        for (Node node : DecisionFlowchart.nodes(DecisionChannel.LONGITUDINAL)) {
            List<ProposalSnapshot> props = mine.stream().filter(p -> DecisionFlowchart.answers(node, p.rule())).toList();
            placed.addAll(props);
            if (settledAt != null) {
                steps.add(notReached(node, props, settledAt, "speed"));
                continue;
            }
            boolean arbitrationDefault = "R00".equals(node.id()) && DEFAULT_SPEED_DECISION.equals(cycle.speedRule());
            if (props.isEmpty() && !arbitrationDefault) {
                steps.add(no(node, "No — " + node.noLabel()));
                continue;
            }
            boolean decisive = arbitrationDefault || props.stream().anyMatch(p -> WON.equals(p.outcome()) || CAPPED.equals(p.outcome()));
            String taken = arbitrationDefault && props.isEmpty()
                ? "Yes — no rule proposed a speed: the arbitration keeps the current trajectory"
                : yesLabel(props, "speed");
            steps.add(yes(node, taken, decisive, props));
            if (props.stream().anyMatch(p -> WON.equals(p.outcome()) && DecisionPriority.isOverride(p.priority()))) {
                settledAt = node.id();
            }
        }
        addOthers(steps, mine, placed);
        steps.add(new FlowStep("DV", "", "Which proposal decides the speed?", Branch.YES,
            "The strongest emergency or mandatory stop; otherwise the lowest of the speed ceilings and the base speed",
            "", cycle.speedArbitration(), true, decidingRules(DecisionChannel.LONGITUDINAL), List.of(), List.of(), List.of()));
        if (ACTUATOR_GUARD.equals(cycle.speedRule())) {
            steps.add(executionCheck());
        }
        return new ChannelFlow(DecisionChannel.LONGITUDINAL.name(), "Speed · longitudinal",
            cycle.speedAction() + " — " + cycle.speedEffect(), steps, "The vehicle " + cycle.speedEffect() + ".");
    }

    private ChannelFlow lateral() {
        List<ProposalSnapshot> mine = cycle.proposals(DecisionChannel.LATERAL);
        List<ProposalSnapshot> placed = new ArrayList<>();
        List<FlowStep> steps = new ArrayList<>();
        steps.add(safeToChangeLane());

        String settledAt = null;
        List<ProposalSnapshot> vetoed = cycle.proposals(DecisionChannel.LATERAL, VETOED_BY);
        if (vetoed.isEmpty()) {
            steps.add(new FlowStep("FE", "", "Is an emergency or mandatory stop braking the vehicle to a stop while a lane change is proposed?",
                Branch.NO, "Keep the lane: the lane change is vetoed", "No veto",
                "No — no lane change was vetoed", false, List.of(), List.of(), List.of(), List.of()));
        } else {
            steps.add(new FlowStep("FE", "", "Is an emergency or mandatory stop braking the vehicle to a stop while a lane change is proposed?",
                Branch.YES, "Keep the lane: the lane change is vetoed", "No veto",
                "Yes — " + quote(vetoed.get(0).detail()) + " vetoes the lane change: the vehicle keeps its lane",
                true, vetoed.stream().map(cycle::ref).toList(), List.of(), List.of(), List.of()));
            settledAt = "FE";
        }

        for (Node node : DecisionFlowchart.nodes(DecisionChannel.LATERAL)) {
            List<ProposalSnapshot> props = mine.stream().filter(p -> DecisionFlowchart.answers(node, p.rule())).toList();
            placed.addAll(props);
            if (settledAt != null) {
                steps.add(notReached(node, props, settledAt, "lane"));
                continue;
            }
            if (props.isEmpty()) {
                Optional<ProposalSnapshot> hazard = cycle.proposals(DecisionChannel.LONGITUDINAL).stream()
                    .filter(p -> DecisionFlowchart.showsHazard(node, p.rule())).findFirst();
                steps.add(no(node, hazard
                    .map(h -> "No — " + quote(h.rule()) + " shows the hazard is there, but no rule to go around it "
                        + "held (see the safe-to-change check above)")
                    .orElse("No — " + node.noLabel())));
                continue;
            }
            boolean decisive = props.stream().anyMatch(p -> WON.equals(p.outcome()) || REJECTED_BY.equals(p.outcome()));
            steps.add(yes(node, yesLabel(props, "lane"), decisive, props));
            if (decisive) {
                settledAt = node.id();
            }
        }
        addOthers(steps, mine, placed);
        if (mine.isEmpty()) {
            steps.add(new FlowStep("FK", "", "Did no lane rule propose a maneuver?", Branch.YES,
                "Keep the lane (default lane decision)", "A lane rule proposed a maneuver",
                "Yes — keep the lane (default lane decision)", true, List.of(), List.of(), List.of(), List.of()));
        }
        steps.add(new FlowStep("DF", "", "Which proposal decides the lane?", Branch.YES,
            "The highest lane rung; within a rung, returning right before changing lane", "",
            cycle.laneArbitration(), true, decidingRules(DecisionChannel.LATERAL), List.of(), List.of(), List.of()));
        if (cycle.rejectionDetail().isPresent() || (cycle.laneAction().isLaneManeuver() && cycle.maneuverIdle())) {
            steps.add(executionCheck());
        }
        return new ChannelFlow(DecisionChannel.LATERAL.name(), "Lane · lateral",
            cycle.laneAction() + " — " + cycle.laneEffect(), steps, "The vehicle " + cycle.laneEffect() + ".");
    }

    /** "00": the backward-chained safeToChangeLane query the lane questions below rely on. */
    private FlowStep safeToChangeLane() {
        List<Condition> conditions = new ArrayList<>();
        List<String> sides = new ArrayList<>();
        boolean anySafe = false;
        for (Lane side : List.of(Lane.LEFT, Lane.RIGHT)) {
            Optional<LaneCheckSnapshot> check = cycle.laneCheck(side);
            if (check.isEmpty()) {
                continue;
            }
            anySafe |= check.get().allowed();
            String name = side.name().toLowerCase();
            sides.add(name + ": " + check.get().explanation());
            check.get().subGoals().forEach(g -> conditions.add(new Condition(capitalise(name) + " · " + g.detail(), g.holds())));
        }
        String taken = sides.isEmpty() ? "The lane-safety check was not recorded for this cycle"
            : (anySafe ? "Yes — " : "No — ") + String.join("; ", sides);
        return new FlowStep("00", "00", "Is it safe to change lane to each side? (backward-chained check used by the lane questions)",
            anySafe ? Branch.YES : Branch.NO, "At least one side is safe", "Neither side is safe", taken, false,
            List.of(), List.of(), conditions, cycle.observed(DecisionFlowchart.Perception.BLIND_SPOTS));
    }

    /** G1–G2B: the actuator's last check, against the world, before a new lane change starts. */
    private FlowStep executionCheck() {
        Optional<String> refused = cycle.rejectionDetail();
        String actuator = cycle.decision().actuatorResult();
        return new FlowStep("G2", "16", "Is the target lane free, with no continuous line to cross, when the actuator checks it?",
            refused.isPresent() ? Branch.NO : Branch.YES, "The lane change starts (indicator, then about 3 s)",
            "Cancel the maneuver and ease off by 10 km/h",
            refused.map(r -> "No — the actuator refused: " + r + "; the vehicle keeps its lane and eases off")
                .orElse("Yes — the maneuver starts" + (actuator == null || actuator.isBlank() ? "" : " (" + actuator + ")")),
            true, List.of(), List.of(), List.of(), List.of());
    }

    private FlowStep yes(Node node, String taken, boolean decisive, List<ProposalSnapshot> props) {
        return new FlowStep(node.id(), node.docRef(), node.question(), Branch.YES, node.yesLabel(), node.noLabel(), taken,
            decisive, props.stream().map(cycle::ref).toList(), supporting(node), List.of(), cycle.observed(node.perception()));
    }

    private FlowStep no(Node node, String taken) {
        return new FlowStep(node.id(), node.docRef(), node.question(), Branch.NO, node.yesLabel(), node.noLabel(), taken,
            false, List.of(), supporting(node), List.of(), cycle.observed(node.perception()));
    }

    private FlowStep notReached(Node node, List<ProposalSnapshot> props, String settledAt, String channel) {
        String taken = "Not on the decision path: the " + channel + " was already settled at " + settledAt
            + (props.isEmpty() ? "" : "; what its rules proposed did not decide it");
        return new FlowStep(node.id(), node.docRef(), node.question(), Branch.NOT_REACHED, node.yesLabel(), node.noLabel(),
            taken, false, props.stream().map(cycle::ref).toList(), supporting(node), List.of(), List.of());
    }

    /** Proposals of rules the flowchart does not place: shown, never dropped. */
    private void addOthers(List<FlowStep> steps, List<ProposalSnapshot> mine, List<ProposalSnapshot> placed) {
        List<ProposalSnapshot> others = mine.stream().filter(p -> !placed.contains(p)).toList();
        if (!others.isEmpty()) {
            boolean decisive = others.stream().anyMatch(p -> WON.equals(p.outcome()) || CAPPED.equals(p.outcome()));
            steps.add(new FlowStep("OTHER", "", "Rules outside the flowchart that proposed something", Branch.YES,
                "", "", yesLabel(others, "decision"), decisive, others.stream().map(cycle::ref).toList(), List.of(),
                List.of(), List.of()));
        }
    }

    private List<RuleRef> decidingRules(DecisionChannel channel) {
        List<RuleRef> rules = new ArrayList<>();
        rules.add(cycle.decidingRef(channel));
        if (channel == DecisionChannel.LONGITUDINAL) {
            cycle.proposals(channel, CAPPED).forEach(p -> rules.add(cycle.ref(p)));
        }
        return rules;
    }

    private List<String> supporting(Node node) {
        return fired.stream().filter(rule -> DecisionFlowchart.supports(node, rule)).toList();
    }

    /** What the "yes" branch led to: the proposal that decided, or why it did not. */
    private static String yesLabel(List<ProposalSnapshot> props, String channel) {
        Optional<ProposalSnapshot> won = props.stream().filter(p -> WON.equals(p.outcome())).findFirst();
        if (won.isPresent()) {
            return "Yes — " + quote(won.get().rule()) + " decides the " + channel + ": " + proposed(won.get());
        }
        Optional<ProposalSnapshot> capped = props.stream().filter(p -> CAPPED.equals(p.outcome())).findFirst();
        if (capped.isPresent()) {
            return "Yes — " + quote(capped.get().rule()) + " is a lower speed ceiling: it sets the speed ("
                + kmh(capped.get().recommendedSpeedKmh()) + " km/h) under " + quote(capped.get().detail());
        }
        Optional<ProposalSnapshot> rejected = props.stream().filter(p -> REJECTED_BY.equals(p.outcome())).findFirst();
        if (rejected.isPresent()) {
            return "Yes — " + quote(rejected.get().rule()) + " won with " + rejected.get().action()
                + ", but the actuator refused to start it";
        }
        ProposalSnapshot first = props.get(0);
        String outcome = LOST_TO.equals(first.outcome()) ? "lost to " + quote(first.detail())
            : VETOED_BY.equals(first.outcome()) ? "was vetoed by " + quote(first.detail())
            : first.outcome().toLowerCase();
        return "Yes — " + quote(first.rule()) + " proposed " + proposed(first) + ", but " + outcome;
    }

    private static String proposed(ProposalSnapshot p) {
        return p.action() + (p.recommendedSpeedKmh() == null ? "" : " at " + kmh(p.recommendedSpeedKmh()) + " km/h");
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
