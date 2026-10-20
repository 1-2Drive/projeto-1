package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiWhyAnswer.Alternative;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.AlternativeStatus;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.Branch;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.ChannelFlow;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.Condition;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.FlowStep;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.RuleRef;
import com.one2drive.projeto1.decision.DecisionPriority;
import com.one2drive.projeto1.decision.SpeedIntent;
import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.explanation.DecisionFlowchart;
import com.one2drive.projeto1.explanation.DecisionFlowchart.Node;
import com.one2drive.projeto1.history.SessionSnapshot.LaneCheckSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.ProposalSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.one2drive.projeto1.api.service.RecordedCycle.CAPPED;
import static com.one2drive.projeto1.api.service.RecordedCycle.LOST_TO;
import static com.one2drive.projeto1.api.service.RecordedCycle.REJECTED_BY;
import static com.one2drive.projeto1.api.service.RecordedCycle.VETOED_BY;
import static com.one2drive.projeto1.api.service.RecordedCycle.kmh;
import static com.one2drive.projeto1.api.service.RecordedCycle.quote;
import static com.one2drive.projeto1.api.service.RecordedCycle.rung;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.ACTUATOR_GUARD;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_LANE_DECISION;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_SPEED_DECISION;

/**
 * "Why not?" for every alternative of both channels that the cycle did not take: speed up,
 * keep the speed or slow down; keep the lane, change left or right, or return right. Each one
 * is classified from what the cycle recorded: proposed but beaten in the arbitration, never
 * proposed because what it needs did not hold, or not applicable in that state. When the
 * cycle does not record enough, the explanation says so instead of guessing.
 */
final class AlternativeExplainer {
    private static final List<DrivingAction> LANE_OPTIONS = List.of(DrivingAction.KEEP_LANE,
        DrivingAction.CHANGE_LANE_LEFT, DrivingAction.CHANGE_LANE_RIGHT, DrivingAction.RETURN_RIGHT);

    private final RecordedCycle cycle;
    private final Map<String, FlowStep> steps;

    AlternativeExplainer(RecordedCycle cycle, List<ChannelFlow> flows) {
        this.cycle = cycle;
        this.steps = flows.stream().flatMap(flow -> flow.steps().stream())
            .collect(Collectors.toMap(FlowStep::nodeId, Function.identity(), (a, b) -> a));
    }

    /** The alternatives of both channels; those not applicable come last within their channel. */
    List<Alternative> build() {
        List<Alternative> alternatives = new ArrayList<>();
        SpeedIntent taken = cycle.speedIntent();
        List<Alternative> speed = new ArrayList<>();
        for (SpeedIntent option : SpeedIntent.values()) {
            if (option != taken) {
                speed.add(speedAlternative(option));
            }
        }
        List<Alternative> lane = new ArrayList<>();
        for (DrivingAction option : LANE_OPTIONS) {
            if (option != cycle.laneAction()) {
                lane.add(laneAlternative(option));
            }
        }
        Comparator<Alternative> applicableFirst = Comparator.comparing(a -> a.status() == AlternativeStatus.NOT_APPLICABLE);
        speed.sort(applicableFirst);
        lane.sort(applicableFirst);
        alternatives.addAll(speed);
        alternatives.addAll(lane);
        return alternatives;
    }

    // ---- speed (longitudinal) ---------------------------------------------------------

    private Alternative speedAlternative(SpeedIntent option) {
        String prevailing = speedPrevailing();
        if (option == SpeedIntent.REDUCE && cycle.speedKmh() < SpeedIntent.STANDSTILL_KMH) {
            return speed(option, AlternativeStatus.NOT_APPLICABLE,
                "The vehicle is already stationary, so it cannot slow down any further.", List.of(), List.of(), prevailing);
        }
        List<ProposalSnapshot> mine = cycle.proposals(DecisionChannel.LONGITUDINAL);
        if (mine.isEmpty() && !DEFAULT_SPEED_DECISION.equals(cycle.speedRule())) {
            return speed(option, AlternativeStatus.INSUFFICIENT_DATA,
                "This recorded cycle holds no speed proposals, so it cannot say why the vehicle did not "
                    + verb(option) + ".", List.of(), List.of(), prevailing);
        }

        // Proposed, and beaten in the arbitration.
        List<ProposalSnapshot> lost = mine.stream()
            .filter(p -> LOST_TO.equals(p.outcome()) && cycle.intentOf(p) == option)
            .sorted(Comparator.comparingInt(ProposalSnapshot::priority).reversed()).toList();
        if (!lost.isEmpty()) {
            ProposalSnapshot loser = lost.get(0);
            RuleRef winner = winnerOf(DecisionChannel.LONGITUDINAL, loser);
            List<RuleRef> blocking = new ArrayList<>();
            blocking.add(winner);
            lost.forEach(p -> blocking.add(cycle.ref(p)));
            List<Condition> held = lost.stream()
                .map(p -> new Condition(quote(p.rule()) + " held and proposed " + proposed(p) + " (" + rung(p.priority()) + ")", true))
                .toList();
            return speed(option, AlternativeStatus.OVERRIDDEN_BY_PRIORITY, speedPrinciple(loser, winner, option),
                held, blocking, prevailing);
        }

        Optional<ProposalSnapshot> winner = cycle.winner(DecisionChannel.LONGITUDINAL);
        List<ProposalSnapshot> capped = cycle.proposals(DecisionChannel.LONGITUDINAL, CAPPED);
        // The deciding proposal itself would have done it, but a lower ceiling set the speed.
        if (winner.isPresent() && cycle.intentOf(winner.get()) == option && !capped.isEmpty()) {
            ProposalSnapshot cap = capped.get(0);
            return speed(option, AlternativeStatus.OVERRIDDEN_BY_PRIORITY,
                quote(winner.get().rule()) + " decides the action (" + winner.get().action() + "), but the lower "
                    + "speed ceiling " + quote(cap.rule()) + " still sets the speed to " + kmh(cap.recommendedSpeedKmh())
                    + " km/h: every ceiling that holds binds.",
                List.of(new Condition(quote(cap.rule()) + " requires " + kmh(cap.recommendedSpeedKmh()) + " km/h", true)),
                List.of(cycle.ref(cap), cycle.ref(winner.get())), prevailing);
        }
        // The rules decided it, but the lane change decided with it could not be executed.
        if (ACTUATOR_GUARD.equals(cycle.speedRule()) && winner.isPresent() && cycle.intentOf(winner.get()) == option) {
            String detail = cycle.rejectionDetail().orElse("no detail recorded");
            return speed(option, AlternativeStatus.CONDITION_NOT_MET,
                quote(winner.get().rule()) + " had decided to " + verb(option) + ", but the lane change decided with it "
                    + "could not be executed (" + detail + "), so the actuator eased off as its safe reaction.",
                List.of(new Condition("Execution check of the lane change: " + detail, false)),
                List.of(cycle.ref(winner.get())), prevailing);
        }

        // Never proposed.
        return speed(option, AlternativeStatus.CONDITION_NOT_MET, notProposed(option), required(option),
            List.of(cycle.decidingRef(DecisionChannel.LONGITUDINAL)), prevailing);
    }

    /** Why the loser lost, in the terms of DecisionContext.resolveLongitudinal. */
    private static String speedPrinciple(ProposalSnapshot loser, RuleRef winner, SpeedIntent option) {
        int lp = loser.priority();
        int wp = winner.priority();
        String l = quote(loser.rule()) + " (" + rung(lp) + ")";
        String w = quote(winner.rule()) + " (" + rung(wp) + ")";
        String ls = kmh(loser.recommendedSpeedKmh());
        String ws = kmh(winner.recommendedSpeedKmh());
        if (DecisionPriority.isOverride(wp) && !DecisionPriority.isOverride(lp)) {
            return l + " proposed to " + verb(option) + ", but " + w + " is an emergency or mandatory stop, "
                + "which overrides every lower-ranked speed proposal.";
        }
        if (DecisionPriority.isOverride(wp)) {
            return "Both " + l + " and " + w + " are emergencies or mandatory stops: the higher rung wins, and on "
                + "the same rung the lower speed.";
        }
        if (DecisionPriority.isSpeedCeiling(wp) && DecisionPriority.isSpeedCeiling(lp)) {
            return "Every speed ceiling binds and the lowest decides: " + w + " requires " + ws + " km/h, below the "
                + ls + " km/h of " + l + ".";
        }
        if (DecisionPriority.isSpeedCeiling(wp)) {
            return w + " is a speed ceiling of " + ws + " km/h that binds below the " + ls + " km/h proposed by " + l
                + ": ceilings cap the vehicle's own base speed.";
        }
        if (DecisionPriority.isSpeedCeiling(lp)) {
            return "The speed ceiling " + l + " (" + ls + " km/h) does not bind below the base speed of " + w + " ("
                + ws + " km/h), so the base speed applies.";
        }
        if (wp == DecisionPriority.MANEUVER_SAFETY && lp <= DecisionPriority.COMFORT_PROGRESS) {
            return "A release such as " + w + " (moving off, resuming) takes precedence over comfort proposals such as "
                + l + ".";
        }
        if (wp == lp) {
            return "Among base proposals of the same rung the lower speed is kept: " + w + " (" + ws + " km/h) against "
                + l + " (" + ls + " km/h).";
        }
        return l + " lost the speed arbitration to " + w + ".";
    }

    private String notProposed(SpeedIntent option) {
        String current = kmh(cycle.speedKmh());
        RuleRef decided = cycle.decidingRef(DecisionChannel.LONGITUDINAL);
        SpeedIntent taken = cycle.speedIntent();
        return switch (option) {
            case INCREASE -> "No rule proposed a speed above the current " + current + " km/h"
                + (taken == SpeedIntent.REDUCE ? ", and " + quote(decided.rule()) + " requires " + demand(decided) : "") + ".";
            case REDUCE -> {
                List<String> holding = cycle.proposals(DecisionChannel.LONGITUDINAL).stream()
                    .filter(p -> cycle.intentOf(p) == SpeedIntent.HOLD)
                    .filter(p -> DecisionFlowchart.nodeFor(p.rule()).map(Node::canLower).orElse(false))
                    .map(p -> quote(p.rule())).toList();
                yield holding.isEmpty()
                    ? "No rule found a condition that requires slowing down: none of the speed-reducing questions of "
                        + "the flowchart held."
                    : "No rule proposed slowing down below the current " + current + " km/h: " + String.join(", ", holding)
                        + " held, but only to hold the speed.";
            }
            case HOLD -> taken == SpeedIntent.REDUCE
                ? quote(decided.rule()) + " requires " + demand(decided) + ", and every stop or speed ceiling that holds binds."
                : quote(decided.rule()) + " targets " + kmh(decided.recommendedSpeedKmh()) + " km/h, above the current "
                    + current + " km/h, and no rule held the speed lower.";
        };
    }

    /**
     * What a slowing proposal asks of the vehicle. Under a mandatory stop a REDUCE_SPEED brakes
     * whatever its target (DecisionActuator), so a target at the current speed is not a speed
     * to reach but a demand to brake now.
     */
    private String demand(RuleRef rule) {
        DrivingAction action = RecordedCycle.action(rule.action(), DrivingAction.MAINTAIN_SPEED);
        Double target = rule.recommendedSpeedKmh();
        double current = cycle.speedKmh();
        if (action == DrivingAction.EMERGENCY_BRAKE) {
            return "an emergency stop";
        }
        if (action == DrivingAction.STOP || action == DrivingAction.WAIT || (target != null && target == 0.0)) {
            return "a stop";
        }
        if (target == null || target.isNaN() || target >= current - SpeedIntent.DEAD_BAND_KMH) {
            return "braking now (" + action + ", " + DecisionPriority.rungName(rule.priority()) + ")";
        }
        return kmh(target) + " km/h, below the current " + kmh(current) + " km/h";
    }

    /** What the alternative would have needed, and whether it held in the cycle. */
    private List<Condition> required(SpeedIntent option) {
        List<Condition> conditions = new ArrayList<>();
        String current = kmh(cycle.speedKmh());
        if (option == SpeedIntent.HOLD) {
            boolean reducing = cycle.speedIntent() == SpeedIntent.REDUCE;
            conditions.add(new Condition(reducing
                ? "No rule requires a speed below the current " + current + " km/h"
                : "No rule targets a speed above the current " + current + " km/h", false));
            cycle.proposals(DecisionChannel.LONGITUDINAL).stream()
                .filter(p -> cycle.intentOf(p) == cycle.speedIntent())
                .forEach(p -> conditions.add(new Condition(quote(p.rule())
                    + (reducing ? " requires " + demand(cycle.ref(p)) : " targets " + kmh(p.recommendedSpeedKmh()) + " km/h")
                    + " (" + rung(p.priority()) + ")", true)));
            return conditions;
        }
        for (Node node : DecisionFlowchart.nodes(DecisionChannel.LONGITUDINAL)) {
            if (option == SpeedIntent.INCREASE ? !node.canRaise() : !node.canLower()) {
                continue;
            }
            FlowStep step = steps.get(node.id());
            List<ProposalSnapshot> props = cycle.proposals(DecisionChannel.LONGITUDINAL).stream()
                .filter(p -> DecisionFlowchart.answers(node, p.rule())).toList();
            if (step != null && step.branch() == Branch.NOT_REACHED) {
                conditions.add(new Condition(node.question() + " — not on the decision path", null));
            } else if (props.isEmpty()) {
                conditions.add(new Condition(node.question() + " — no", false));
            } else {
                conditions.add(new Condition(node.question() + " — yes, but " + quote(props.get(0).rule()) + " targets "
                    + kmh(props.get(0).recommendedSpeedKmh()) + " km/h, not "
                    + (option == SpeedIntent.INCREASE ? "above" : "below") + " the current " + current + " km/h", false));
            }
        }
        RuleRef decided = cycle.decidingRef(DecisionChannel.LONGITUDINAL);
        if (option == SpeedIntent.INCREASE && cycle.speedIntent() == SpeedIntent.REDUCE) {
            conditions.add(new Condition("No stop or speed ceiling below the current speed: " + quote(decided.rule())
                + " requires " + demand(decided), false));
        }
        return conditions;
    }

    private String speedPrevailing() {
        RuleRef decided = cycle.decidingRef(DecisionChannel.LONGITUDINAL);
        return "The speed decision was " + cycle.speedAction() + " (the vehicle " + cycle.speedEffect() + "), from "
            + quote(decided.rule()) + " (" + rung(decided.priority()) + "). " + cycle.speedArbitration();
    }

    private static Alternative speed(SpeedIntent option, AlternativeStatus status, String summary,
                                     List<Condition> conditions, List<RuleRef> blocking, String prevailing) {
        return new Alternative(DecisionChannel.LONGITUDINAL.name(), option.name(), label(option), status, summary,
            conditions, blocking, prevailing);
    }

    private static String label(SpeedIntent option) {
        return switch (option) {
            case INCREASE -> "Speed up";
            case HOLD -> "Keep the current speed";
            case REDUCE -> "Slow down";
        };
    }

    private static String verb(SpeedIntent option) {
        return switch (option) {
            case INCREASE -> "speed up";
            case HOLD -> "keep its speed";
            case REDUCE -> "slow down";
        };
    }

    // ---- lane (lateral) ---------------------------------------------------------------

    private Alternative laneAlternative(DrivingAction option) {
        String prevailing = lanePrevailing();
        Lane lane = cycle.lane();
        if (option == DrivingAction.CHANGE_LANE_LEFT && lane == Lane.LEFT) {
            return lane(option, AlternativeStatus.NOT_APPLICABLE, "There is no lane to the left of the LEFT lane.",
                List.of(), List.of(), prevailing);
        }
        if (option == DrivingAction.CHANGE_LANE_RIGHT && lane == Lane.RIGHT) {
            return lane(option, AlternativeStatus.NOT_APPLICABLE, "There is no lane to the right of the RIGHT lane.",
                List.of(), List.of(), prevailing);
        }
        if (option == DrivingAction.RETURN_RIGHT && lane == Lane.RIGHT) {
            return lane(option, AlternativeStatus.NOT_APPLICABLE, "The vehicle is already in the rightmost lane.",
                List.of(), List.of(), prevailing);
        }
        List<ProposalSnapshot> props = cycle.proposals(DecisionChannel.LATERAL).stream()
            .filter(p -> option.name().equals(p.action())).toList();
        Lane side = option == DrivingAction.CHANGE_LANE_LEFT ? Lane.LEFT : Lane.RIGHT;

        Optional<ProposalSnapshot> rejected = props.stream().filter(p -> REJECTED_BY.equals(p.outcome())).findFirst();
        if (rejected.isPresent()) {
            String detail = cycle.rejectionDetail().orElse("no detail recorded");
            List<Condition> conditions = new ArrayList<>(laneSafety(side));
            conditions.add(new Condition("Execution check by the actuator: " + detail, false));
            return lane(option, AlternativeStatus.CONDITION_NOT_MET,
                quote(rejected.get().rule()) + " proposed it and won the lane arbitration, but the actuator's execution "
                    + "check failed: " + detail + ".", conditions, List.of(cycle.ref(rejected.get())), prevailing);
        }
        Optional<ProposalSnapshot> vetoed = props.stream().filter(p -> VETOED_BY.equals(p.outcome())).findFirst();
        if (vetoed.isPresent()) {
            List<RuleRef> blocking = new ArrayList<>();
            cycle.proposalOf(DecisionChannel.LONGITUDINAL, vetoed.get().detail()).map(cycle::ref).ifPresent(blocking::add);
            blocking.add(cycle.ref(vetoed.get()));
            return lane(option, AlternativeStatus.OVERRIDDEN_BY_PRIORITY,
                quote(vetoed.get().rule()) + " proposed it, but " + quote(vetoed.get().detail()) + " is an emergency or "
                    + "mandatory stop braking the vehicle to a stop from a higher rung, and that vetoes lane changes.",
                List.of(new Condition(quote(vetoed.get().rule()) + " held (" + rung(vetoed.get().priority()) + ")", true)),
                blocking, prevailing);
        }
        Optional<ProposalSnapshot> lost = props.stream().filter(p -> LOST_TO.equals(p.outcome()))
            .max(Comparator.comparingInt(ProposalSnapshot::priority));
        if (lost.isPresent()) {
            RuleRef winner = winnerOf(DecisionChannel.LATERAL, lost.get());
            return lane(option, AlternativeStatus.OVERRIDDEN_BY_PRIORITY, lanePrinciple(lost.get(), winner),
                List.of(new Condition(quote(lost.get().rule()) + " held (" + rung(lost.get().priority()) + ")", true)),
                List.of(winner, cycle.ref(lost.get())), prevailing);
        }

        // Never proposed.
        if (option == DrivingAction.KEEP_LANE) {
            RuleRef decided = cycle.decidingRef(DecisionChannel.LATERAL);
            boolean ongoing = !cycle.maneuverIdle();
            return lane(option, AlternativeStatus.CONDITION_NOT_MET,
                "Keeping the lane is the default only when no lane rule proposes a maneuver, and " + quote(decided.rule())
                    + " did." + (ongoing ? " A lane change in progress is finished, never reverted midway." : ""),
                List.of(new Condition("No lane rule proposes a maneuver", false),
                    new Condition(quote(decided.rule()) + " proposed " + decided.action()
                        + (decided.reason() == null || decided.reason().isBlank() ? "" : ": " + decided.reason()), true)),
                List.of(decided), prevailing);
        }
        List<Condition> conditions = new ArrayList<>(laneSafety(side));
        boolean anyTrigger = false;
        for (Node node : DecisionFlowchart.nodes(DecisionChannel.LATERAL)) {
            if (!node.laneActions().contains(option)) {
                continue;
            }
            FlowStep step = steps.get(node.id());
            if (step != null && step.branch() == Branch.NOT_REACHED) {
                conditions.add(new Condition(node.question() + " — not on the decision path", null));
            } else if (step != null && step.branch() == Branch.YES) {
                anyTrigger = true;
                conditions.add(new Condition(node.question() + " — yes, but its rules proposed another lane action", true));
            } else {
                conditions.add(new Condition(node.question() + " — " + (step == null ? "no" : step.takenLabel()
                    .replaceFirst("^No — ", "no: ")), false));
            }
        }
        Optional<LaneCheckSnapshot> check = cycle.laneCheck(side);
        String move = (option == DrivingAction.RETURN_RIGHT ? "Returning" : "Changing") + " to the "
            + side.name().toLowerCase();
        String summary;
        if (check.isPresent() && !check.get().allowed()) {
            summary = move + " was not safe: " + check.get().explanation() + "."
                + (anyTrigger ? "" : " No rule proposed it either.");
        } else if (check.isPresent()) {
            summary = move + " was safe, but no rule had a reason to: none of the questions that lead to it held.";
        } else {
            summary = "No rule proposed it: none of the questions that lead to it held. The lane-safety check of that "
                + "side was not recorded in this cycle.";
        }
        return lane(option, AlternativeStatus.CONDITION_NOT_MET, summary, conditions,
            List.of(cycle.decidingRef(DecisionChannel.LATERAL)), prevailing);
    }

    /** Why the loser lost, in the terms of DecisionContext.resolveLateral. */
    private static String lanePrinciple(ProposalSnapshot loser, RuleRef winner) {
        String l = quote(loser.rule()) + " (" + rung(loser.priority()) + ")";
        String w = quote(winner.rule()) + " (" + rung(winner.priority()) + ")";
        if (winner.priority() > loser.priority()) {
            return l + " proposed it, but " + w + " is on a higher lane rung, and the highest rung wins.";
        }
        if (winner.priority() == loser.priority() && DrivingAction.RETURN_RIGHT.name().equals(winner.action())
                && loser.action().startsWith("CHANGE_LANE")) {
            return l + " and " + w + " are on the same rung, and returning right beats changing lane.";
        }
        if (winner.priority() == loser.priority() && DrivingAction.KEEP_LANE.name().equals(loser.action())) {
            return l + " and " + w + " are on the same rung, and a maneuver beats keeping the lane.";
        }
        if (winner.priority() == loser.priority()) {
            return l + " and " + w + " are on the same rung; " + w + " was proposed first (rule order).";
        }
        return l + " lost the lane arbitration to " + w + ".";
    }

    /** The sub-goals of safeToChangeLane for that side, as the engine proved them after the rules fired. */
    private List<Condition> laneSafety(Lane side) {
        String direction = side.name().toLowerCase();
        return cycle.laneCheck(side)
            .map(check -> check.subGoals().stream()
                .map(g -> new Condition("Safe to change " + direction + " · " + g.detail(), g.holds())).toList())
            .orElse(List.of(new Condition("Safe to change " + direction + " · not recorded in this cycle", null)));
    }

    private String lanePrevailing() {
        RuleRef decided = cycle.decidingRef(DecisionChannel.LATERAL);
        String rule = DEFAULT_LANE_DECISION.equals(decided.rule()) ? "the default lane decision" : quote(decided.rule());
        return "The lane decision was " + cycle.laneAction() + " (the vehicle " + cycle.laneEffect() + "), from "
            + rule + ". " + cycle.laneArbitration();
    }

    private static Alternative lane(DrivingAction option, AlternativeStatus status, String summary,
                                    List<Condition> conditions, List<RuleRef> blocking, String prevailing) {
        return new Alternative(DecisionChannel.LATERAL.name(), option.name(), label(option), status, summary,
            conditions, blocking, prevailing);
    }

    private static String label(DrivingAction option) {
        return switch (option) {
            case KEEP_LANE -> "Stay in the current lane";
            case CHANGE_LANE_LEFT -> "Change to the left lane";
            case CHANGE_LANE_RIGHT -> "Change to the right lane";
            case RETURN_RIGHT -> "Return towards the right lane (keep right)";
            default -> option.name();
        };
    }

    // ---- shared -----------------------------------------------------------------------

    /** The proposal a loser lost to, as its detail names it, or the channel's decision. */
    private RuleRef winnerOf(DecisionChannel channel, ProposalSnapshot loser) {
        return cycle.proposalOf(channel, loser.detail()).map(cycle::ref).orElse(cycle.decidingRef(channel));
    }

    private static String proposed(ProposalSnapshot p) {
        return p.action() + (p.recommendedSpeedKmh() == null ? "" : " at " + kmh(p.recommendedSpeedKmh()) + " km/h");
    }
}
