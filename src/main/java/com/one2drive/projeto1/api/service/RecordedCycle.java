package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiWhyAnswer.RuleRef;
import com.one2drive.projeto1.decision.DecisionPriority;
import com.one2drive.projeto1.decision.SpeedIntent;
import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.explanation.DecisionFlowchart;
import com.one2drive.projeto1.explanation.DecisionFlowchart.Perception;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.DecisionSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.LaneCheckSnapshot;
import com.one2drive.projeto1.history.SessionSnapshot.ProposalSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.one2drive.projeto1.explanation.DecisionFlowchart.ACTUATOR_GUARD;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.ARBITRATION_VETO;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_LANE_DECISION;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_SPEED_DECISION;
import static com.one2drive.projeto1.explanation.DecisionFlowchart.DEFAULT_SPEED_RULE;

/**
 * One saved decision cycle, read for the explanation: each channel's decision, the proposals
 * behind it with their arbitration outcomes, the lane-safety sub-goals, the rules that fired
 * and what the sensors reported. Only what the cycle recorded is read; when a per-channel
 * field is missing (a session saved before it existed) the channel's winning proposal stands in.
 */
final class RecordedCycle {
    static final String WON = "WON";
    static final String LOST_TO = "LOST_TO";
    static final String VETOED_BY = "VETOED_BY";
    static final String REJECTED_BY = "REJECTED_BY";
    static final String CAPPED = "CAPPED";

    private final SessionSnapshot snapshot;
    private final DecisionSnapshot decision;

    RecordedCycle(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        this.decision = snapshot.decision();
    }

    SessionSnapshot snapshot() { return snapshot; }

    DecisionSnapshot decision() { return decision; }

    /** The speed the rules saw. */
    double speedKmh() { return snapshot.vehicle() == null ? 0.0 : snapshot.vehicle().speedKmh(); }

    Lane lane() {
        String lane = snapshot.vehicle() == null ? null : snapshot.vehicle().lane();
        try {
            return lane == null ? Lane.CENTER : Lane.valueOf(lane);
        } catch (IllegalArgumentException e) {
            return Lane.CENTER;
        }
    }

    String laneChangeState() {
        return snapshot.vehicle() == null || snapshot.vehicle().laneChangeState() == null
            ? "IDLE" : snapshot.vehicle().laneChangeState();
    }

    boolean maneuverIdle() { return "IDLE".equals(laneChangeState()) || "COMPLETED".equals(laneChangeState()); }

    // ---- proposals --------------------------------------------------------------------

    List<ProposalSnapshot> proposals(DecisionChannel channel) {
        return decision.proposals().stream().filter(p -> channel.name().equals(p.channel())).toList();
    }

    List<ProposalSnapshot> proposals(DecisionChannel channel, String outcome) {
        return proposals(channel).stream().filter(p -> outcome.equals(p.outcome())).toList();
    }

    Optional<ProposalSnapshot> proposalOf(DecisionChannel channel, String rule) {
        return proposals(channel).stream().filter(p -> p.rule().equals(rule)).findFirst();
    }

    /** The proposal that won the channel's arbitration, before any veto or actuator refusal. */
    Optional<ProposalSnapshot> winner(DecisionChannel channel) {
        List<ProposalSnapshot> won = proposals(channel, WON);
        if (!won.isEmpty()) {
            return Optional.of(won.get(0));
        }
        if (channel == DecisionChannel.LATERAL) {
            return proposals(channel).stream()
                .filter(p -> VETOED_BY.equals(p.outcome()) || REJECTED_BY.equals(p.outcome())).findFirst();
        }
        return Optional.empty();
    }

    SpeedIntent intentOf(ProposalSnapshot proposal) {
        double target = proposal.recommendedSpeedKmh() == null ? Double.NaN : proposal.recommendedSpeedKmh();
        return SpeedIntent.of(action(proposal.action(), DrivingAction.MAINTAIN_SPEED), proposal.priority(), target,
            speedKmh());
    }

    // ---- the two decisions ------------------------------------------------------------

    DrivingAction speedAction() {
        return action(decision.speedAction() == null ? decision.action() : decision.speedAction(),
            DrivingAction.MAINTAIN_SPEED);
    }

    String speedRule() { return firstNonBlank(decision.speedSourceRule(), decision.sourceRule()); }

    int speedPriority() {
        if (decision.speedPriority() != null) {
            return decision.speedPriority();
        }
        return winner(DecisionChannel.LONGITUDINAL).map(ProposalSnapshot::priority).orElse(0);
    }

    String speedReason() {
        return firstNonBlank(decision.speedReason(),
            proposalOf(DecisionChannel.LONGITUDINAL, speedRule()).map(ProposalSnapshot::reason).orElse(null),
            speedRule().equals(decision.sourceRule()) ? decision.reason() : null);
    }

    List<String> speedEvidence() {
        if (!decision.speedEvidence().isEmpty()) {
            return decision.speedEvidence();
        }
        return proposalOf(DecisionChannel.LONGITUDINAL, speedRule()).map(ProposalSnapshot::evidence)
            .orElse(speedRule().equals(decision.sourceRule()) ? decision.evidence() : List.of());
    }

    double targetKmh() { return decision.recommendedSpeedKmh(); }

    SpeedIntent speedIntent() { return SpeedIntent.of(speedAction(), speedPriority(), targetKmh(), speedKmh()); }

    DrivingAction laneAction() {
        return action(decision.laneAction() == null ? DrivingAction.KEEP_LANE.name() : decision.laneAction(),
            DrivingAction.KEEP_LANE);
    }

    String laneRule() { return firstNonBlank(decision.laneSourceRule(), DEFAULT_LANE_DECISION); }

    int lanePriority() {
        if (decision.lanePriority() != null) {
            return decision.lanePriority();
        }
        return winner(DecisionChannel.LATERAL).map(ProposalSnapshot::priority).orElse(0);
    }

    String laneReason() {
        return firstNonBlank(decision.laneReason(),
            proposalOf(DecisionChannel.LATERAL, laneRule()).map(ProposalSnapshot::reason).orElse(null),
            laneRule().equals(decision.sourceRule()) ? decision.reason() : null);
    }

    List<String> laneEvidence() {
        if (!decision.laneEvidence().isEmpty()) {
            return decision.laneEvidence();
        }
        return proposalOf(DecisionChannel.LATERAL, laneRule()).map(ProposalSnapshot::evidence)
            .orElse(laneRule().equals(decision.sourceRule()) ? decision.evidence() : List.of());
    }

    /** The lane a lateral action leads to from the ego lane, as the actuator resolves it. */
    Lane targetOf(DrivingAction action) {
        return switch (action) {
            case CHANGE_LANE_LEFT -> lane().left();
            case CHANGE_LANE_RIGHT -> lane().right();
            case RETURN_RIGHT -> lane() == Lane.LEFT ? Lane.CENTER : Lane.RIGHT;
            default -> lane();
        };
    }

    Optional<LaneCheckSnapshot> laneCheck(Lane side) {
        return decision.laneChecks().stream().filter(c -> side.name().equals(c.side())).findFirst();
    }

    /** The rules that fired in the cycle, by their Drools name, in firing order. */
    Set<String> firedRules() {
        Set<String> fired = new LinkedHashSet<>();
        if (snapshot.timeline() == null) {
            return fired;
        }
        snapshot.timeline().stream().filter(e -> "RULE_FIRED".equals(e.type())).forEach(e -> {
            int open = e.message().indexOf('"');
            int close = e.message().lastIndexOf('"');
            if (open >= 0 && close > open) {
                fired.add(DecisionFlowchart.baseRuleName(e.message().substring(open + 1, close)));
            }
        });
        return fired;
    }

    // ---- references -------------------------------------------------------------------

    RuleRef ref(ProposalSnapshot p) {
        return new RuleRef(p.rule(), p.action(), p.channel(), p.recommendedSpeedKmh(), p.priority(),
            DecisionPriority.rungName(p.priority()), p.outcome(), p.detail(), p.reason(), p.evidence());
    }

    /** The rule behind a channel's decision, also when no proposal names it (a default, a veto, the actuator). */
    RuleRef decidingRef(DecisionChannel channel) {
        boolean speed = channel == DecisionChannel.LONGITUDINAL;
        String rule = speed ? speedRule() : laneRule();
        Optional<ProposalSnapshot> proposal = proposalOf(channel, rule);
        if (proposal.isPresent()) {
            return ref(proposal.get());
        }
        int priority = speed ? speedPriority() : lanePriority();
        String outcome = switch (rule) {
            case DEFAULT_SPEED_DECISION, DEFAULT_LANE_DECISION -> "DEFAULT";
            case ARBITRATION_VETO -> "VETO";
            case ACTUATOR_GUARD -> "ACTUATOR";
            default -> "RECORDED";
        };
        return new RuleRef(rule, speed ? speedAction().name() : laneAction().name(), channel.name(),
            speed ? targetKmh() : null, priority, DecisionPriority.rungName(priority), outcome, "",
            speed ? speedReason() : laneReason(), speed ? speedEvidence() : laneEvidence());
    }

    // ---- wording ----------------------------------------------------------------------

    /** What the speed decision does, e.g. "slows down from 48 to 30 km/h". */
    String speedEffect() {
        double current = speedKmh();
        double target = targetKmh();
        return switch (speedIntent()) {
            case INCREASE -> Double.isNaN(target)
                ? "speeds up from " + kmh(current) + " km/h"
                : "speeds up from " + kmh(current) + " to " + kmh(target) + " km/h";
            case REDUCE -> {
                DrivingAction action = speedAction();
                if (action == DrivingAction.EMERGENCY_BRAKE) {
                    yield "brakes hard from " + kmh(current) + " km/h";
                }
                if (action == DrivingAction.STOP || action == DrivingAction.WAIT || target == 0.0) {
                    yield "slows down from " + kmh(current) + " km/h to a stop";
                }
                yield Double.isNaN(target) || target >= current
                    ? "slows down from " + kmh(current) + " km/h"
                    : "slows down from " + kmh(current) + " to " + kmh(target) + " km/h";
            }
            case HOLD -> current < SpeedIntent.STANDSTILL_KMH ? "stays stopped" : "keeps its speed of " + kmh(current) + " km/h";
        };
    }

    /** What the lane decision does, e.g. "moves from the RIGHT to the CENTER lane". */
    String laneEffect() {
        DrivingAction action = laneAction();
        if (!action.isLaneManeuver()) {
            return "stays in the " + lane() + " lane";
        }
        if (!maneuverIdle()) {
            String target = snapshot.vehicle() == null ? targetOf(action).name() : snapshot.vehicle().targetLane();
            return "continues its lane change to the " + target + " lane";
        }
        return "moves from the " + lane() + " to the " + targetOf(action) + " lane";
    }

    /** How the speed arbitration (DecisionContext.resolveLongitudinal) settled on the decision. */
    String speedArbitration() {
        String rule = speedRule();
        if (DEFAULT_SPEED_DECISION.equals(rule)) {
            return "No rule proposed a speed in this cycle, so the arbitration kept the current trajectory.";
        }
        if (ACTUATOR_GUARD.equals(rule)) {
            return "The rules chose a lane change that the actuator refused to start ("
                + rejectionDetail().orElse("no detail recorded")
                + "); as its safe reaction the actuator eased off by 10 km/h.";
        }
        Optional<ProposalSnapshot> found = winner(DecisionChannel.LONGITUDINAL);
        if (found.isEmpty()) {
            return "The recorded cycle does not name the proposal that won the speed arbitration.";
        }
        ProposalSnapshot winner = found.get();
        int priority = winner.priority();
        int lost = proposals(DecisionChannel.LONGITUDINAL, LOST_TO).size();
        String tail = lost == 0 ? "" : " " + lost + " other speed proposal(s) lost to it.";
        if (DecisionPriority.isOverride(priority)) {
            String text = "Emergencies and mandatory stops override every other speed proposal, and this was the "
                + "strongest one (" + rung(priority) + ")." + tail;
            for (ProposalSnapshot cap : proposals(DecisionChannel.LONGITUDINAL, CAPPED)) {
                text += " A lower speed ceiling still sets the speed: " + quote(cap.rule()) + " ("
                    + kmh(cap.recommendedSpeedKmh()) + " km/h).";
            }
            return text;
        }
        List<ProposalSnapshot> ceilings = proposals(DecisionChannel.LONGITUDINAL).stream()
            .filter(p -> DecisionPriority.isSpeedCeiling(p.priority())).toList();
        if (DecisionPriority.isSpeedCeiling(priority)) {
            if (ceilings.size() > 1) {
                return "Every speed ceiling binds and the lowest one decides: this was the lowest of "
                    + ceilings.size() + " ceilings (" + rung(priority) + ")." + tail;
            }
            return lost == 0
                ? "It was the only speed proposal (" + rung(priority) + ")."
                : "It was the only speed ceiling, and it bound below the vehicle's own base speed (" + rung(priority) + ")." + tail;
        }
        if (DEFAULT_SPEED_RULE.equals(winner.rule())) {
            return "Nothing else proposed a speed, so the fallback rule keeps the current speed.";
        }
        String text = ceilings.isEmpty()
            ? "No speed ceiling held, so the vehicle's own base speed applies (" + rung(priority) + ")."
            : "No speed ceiling bound below it, so the vehicle's own base speed applies (" + rung(priority) + ").";
        if (priority == DecisionPriority.MANEUVER_SAFETY && proposals(DecisionChannel.LONGITUDINAL).stream()
                .anyMatch(p -> p.priority() <= DecisionPriority.COMFORT_PROGRESS)) {
            text += " A release (moving off, resuming) takes precedence over comfort proposals.";
        }
        return text + tail;
    }

    /** How the lane arbitration (DecisionContext.resolveLateral, the veto, the actuator) settled. */
    String laneArbitration() {
        String rule = laneRule();
        if (DEFAULT_LANE_DECISION.equals(rule)) {
            return "No lane rule held, so the vehicle keeps its lane (the default lane decision).";
        }
        Optional<ProposalSnapshot> vetoed = proposals(DecisionChannel.LATERAL, VETOED_BY).stream().findFirst();
        if (ARBITRATION_VETO.equals(rule) && vetoed.isPresent()) {
            return quote(vetoed.get().rule()) + " proposed " + vetoed.get().action() + ", but "
                + quote(vetoed.get().detail()) + " is braking the vehicle to a stop with a higher priority, "
                + "and that vetoes lane changes.";
        }
        Optional<ProposalSnapshot> rejected = proposals(DecisionChannel.LATERAL, REJECTED_BY).stream().findFirst();
        if (ACTUATOR_GUARD.equals(rule) && rejected.isPresent()) {
            return quote(rejected.get().rule()) + " won the lane arbitration, but the actuator refused to start the "
                + "maneuver: " + rejectionDetail().orElse("no detail recorded") + ".";
        }
        Optional<ProposalSnapshot> found = proposals(DecisionChannel.LATERAL, WON).stream().findFirst();
        if (found.isEmpty()) {
            return "The recorded cycle does not name the proposal that won the lane arbitration.";
        }
        ProposalSnapshot winner = found.get();
        List<ProposalSnapshot> lost = proposals(DecisionChannel.LATERAL, LOST_TO);
        String text = lost.isEmpty()
            ? "It was the only lane proposal (" + rung(winner.priority()) + ")."
            : "The highest lane rung wins: it beat " + lost.size() + " other lane proposal(s) ("
                + rung(winner.priority()) + ").";
        if (DrivingAction.RETURN_RIGHT.name().equals(winner.action()) && lost.stream()
                .anyMatch(p -> p.priority() == winner.priority() && p.action().startsWith("CHANGE_LANE"))) {
            text += " Within the same rung, returning right beats changing lane.";
        }
        return text;
    }

    /** Why the actuator refused the lane change, when it did. */
    Optional<String> rejectionDetail() {
        return proposals(DecisionChannel.LATERAL, REJECTED_BY).stream().findFirst()
            .map(p -> p.detail().replaceFirst("^actuator:\\s*", ""));
    }

    /** What the sensors reported that a flowchart question is about. */
    List<String> observed(Perception perception) {
        List<String> lines = new ArrayList<>();
        ApiSimulationSnapshot.SensorTelemetryView sensors = snapshot.sensors();
        switch (perception) {
            case RADAR -> {
                if (sensors != null && sensors.frontRadar() != null) {
                    var radar = sensors.frontRadar();
                    if ("none".equals(radar.targetId()) || !Double.isFinite(radar.distanceMeters())) {
                        lines.add("Front radar: no vehicle ahead");
                    } else {
                        lines.add("Front radar: " + radar.targetId() + " at " + metres(radar.distanceMeters())
                            + " m, relative speed " + kmh(radar.relativeSpeedKmh()) + " km/h"
                            + (Double.isFinite(radar.ttcSeconds()) ? ", TTC " + seconds(radar.ttcSeconds()) + " s" : ""));
                    }
                }
            }
            case PEDESTRIAN -> lines.addAll(camera("Pedestrian", "pedestrian"));
            case OBSTACLE -> lines.addAll(camera("Obstacle", "obstacle"));
            case POTHOLE -> lines.addAll(camera("Pothole", "pothole"));
            case SPEED_SIGN -> lines.addAll(camera("Speed limit", "speed-limit sign"));
            case WEATHER -> {
                if (snapshot.environment() != null) {
                    lines.add("Environment: " + snapshot.environment().weatherCondition() + ", visibility "
                        + factor(snapshot.environment().visibilityFactor()) + ", grip "
                        + factor(snapshot.environment().gripFactor()));
                }
            }
            case BLIND_SPOTS -> {
                if (sensors != null) {
                    lines.add(blindSpot("left", sensors.blindSpotLeft()));
                    lines.add(blindSpot("right", sensors.blindSpotRight()));
                    lines.removeIf(String::isEmpty);
                }
            }
            case VEHICLE -> lines.add("Vehicle: " + kmh(speedKmh()) + " km/h in the " + lane()
                + " lane, lane change " + laneChangeState());
            case NONE -> { }
        }
        return lines;
    }

    private List<String> camera(String label, String noun) {
        ApiSimulationSnapshot.SensorTelemetryView sensors = snapshot.sensors();
        if (sensors == null || sensors.frontCamera() == null || sensors.frontCamera().detections() == null) {
            return List.of();
        }
        List<String> lines = sensors.frontCamera().detections().stream()
            .filter(d -> d.label() != null && d.label().startsWith(label))
            .map(d -> "Camera: " + d.label() + " " + d.objectId() + " at " + metres(d.distanceMeters()) + " m"
                + (List.of("LEFT", "CENTER", "RIGHT").contains(d.lane()) ? ", " + d.lane() + " lane" : ""))
            .toList();
        return lines.isEmpty() ? List.of("Camera: no " + noun + " detected") : lines;
    }

    private static String blindSpot(String side, ApiSimulationSnapshot.BlindSpotTelemetryView view) {
        if (view == null) {
            return "";
        }
        return "Blind spot " + side + ": " + view.status()
            + (view.occupied() && Double.isFinite(view.distanceMeters()) ? " (" + metres(view.distanceMeters()) + " m)" : "");
    }

    static DrivingAction action(String name, DrivingAction fallback) {
        try {
            return name == null ? fallback : DrivingAction.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    static String rung(int priority) {
        return DecisionPriority.rungName(priority) + ", priority " + priority;
    }

    static String quote(String rule) {
        return "'" + rule + "'";
    }

    static String kmh(Double value) {
        return value == null || value.isNaN() ? "—" : String.format(Locale.ROOT, "%.0f", value);
    }

    private static String metres(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String seconds(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String factor(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
