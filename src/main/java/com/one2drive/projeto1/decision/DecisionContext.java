package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects what the rules propose during one inference cycle and arbitrates it into a
 * {@link DrivingDecision} with two independent parts, one per vehicle control.
 *
 * <p><b>Longitudinal channel (speed).</b> Emergencies and mandatory stops
 * ({@code priority >= LEGAL_MANDATORY_STOP}) override everything, the highest rung
 * winning and the lowest speed breaking ties. Below them the proposals of the
 * COLLISION_AVOIDANCE, ROAD_HAZARD and SPEED_COMPLIANCE rungs are speed ceilings: the
 * vehicle must respect all of them, so the decision is the lowest of all that hold,
 * whatever their rung. The MANEUVER_SAFETY rung (moving off, resuming) and the
 * COMFORT_PROGRESS rung only apply when no ceiling holds.
 *
 * <p><b>Lateral channel (lane).</b> The highest rung wins; inside a rung RETURN_RIGHT
 * beats CHANGE_LANE and KEEP_LANE is only the explained default, since a rule that
 * blocks a direction and a rule that moves into it are exclusive by their conditions.
 *
 * <p><b>Coupling.</b> A lane maneuver is vetoed when the speed decision is an emergency
 * or a mandatory stop from a higher rung than the maneuver's own.
 *
 * <p>Every proposal is kept with its outcome, so the explanation can say why a rule did
 * not decide, not only which one did.
 */
public class DecisionContext {
    private static final Comparator<DecisionProposal> LOWEST_SPEED_FIRST = Comparator
        .comparingDouble((DecisionProposal p) -> Double.isNaN(p.getRecommendedSpeedKmh())
            ? Double.POSITIVE_INFINITY : p.getRecommendedSpeedKmh())
        .thenComparingInt(DecisionProposal::getOrder);

    private static final Comparator<DecisionProposal> LATERAL_ORDER = Comparator
        .comparingInt(DecisionProposal::getPriority).reversed()
        .thenComparingInt((DecisionProposal p) -> lateralRank(p.getAction()))
        .thenComparingInt(DecisionProposal::getOrder);

    private final List<DecisionProposal> proposals = new ArrayList<>();
    private long evaluationTimestamp;
    private double currentSpeedKmh;
    private int priority;

    public DecisionContext() {
        beginCycle(0L, 0.0);
    }

    public void beginCycle(long evaluationTimestamp, double currentSpeedKmh) {
        this.evaluationTimestamp = evaluationTimestamp;
        this.currentSpeedKmh = Math.max(0.0, currentSpeedKmh);
        this.priority = 0;
        this.proposals.clear();
    }

    public void propose(DrivingAction action,
                        double recommendedSpeedKmh,
                        RiskLevel riskLevel,
                        int priority,
                        String reason,
                        String sourceRule,
                        List<String> evidenceLines) {
        proposals.add(new DecisionProposal(action, recommendedSpeedKmh, riskLevel, priority, reason, sourceRule,
            evidenceLines, proposals.size()));
        if (action.channel() == DecisionChannel.LONGITUDINAL) {
            this.priority = Math.max(this.priority, priority);
        }
    }

    public long getEvaluationTimestamp() {
        return evaluationTimestamp;
    }

    /**
     * Highest longitudinal priority proposed so far in the cycle. Only the baseline rule
     * reads it, to propose when nothing else did.
     */
    public int getPriority() {
        return priority;
    }

    public List<DecisionProposal> getProposals() {
        return List.copyOf(proposals);
    }

    public void applyTo(DrivingDecision decision) {
        DecisionProposal speed = resolveLongitudinal();
        DecisionProposal lane = resolveLateral();

        if (speed == null) {
            decision.apply(DrivingAction.MAINTAIN_SPEED, currentSpeedKmh, RiskLevel.LOW, 0,
                "Maintain current trajectory", "Default base decision", evaluationTimestamp, List.of());
        } else {
            decision.apply(speed.getAction(), speed.getRecommendedSpeedKmh(), speed.getRiskLevel(),
                speed.getPriority(), speed.getReason(), speed.getSourceRule(), evaluationTimestamp,
                speed.getEvidence());
        }

        int speedPriority = speed == null ? 0 : speed.getPriority();
        if (lane == null) {
            decision.applyLane(DrivingAction.KEEP_LANE, 0, "No lane rule holds", "Default lane decision", List.of());
        } else if (lane.getAction().isLaneManeuver()
            && speedPriority >= DecisionPriority.LEGAL_MANDATORY_STOP && lane.getPriority() < speedPriority) {
            lane.settle(DecisionProposal.Outcome.VETOED_BY, speed.getSourceRule());
            decision.applyLane(DrivingAction.KEEP_LANE, lane.getPriority(),
                "Lane change vetoed: " + speed.getReason(), "Arbitration veto",
                List.of(lane.getSourceRule() + " proposed " + lane.getAction() + ", overruled by " + speed.getSourceRule()));
        } else {
            lane.settle(DecisionProposal.Outcome.WON, "");
            decision.applyLane(lane.getAction(), lane.getPriority(), lane.getReason(), lane.getSourceRule(),
                lane.getEvidence());
        }
        decision.setProposals(proposals);
    }

    private DecisionProposal resolveLongitudinal() {
        List<DecisionProposal> mine = proposals.stream()
            .filter(p -> p.getChannel() == DecisionChannel.LONGITUDINAL).toList();
        if (mine.isEmpty()) {
            return null;
        }
        List<DecisionProposal> overrides = mine.stream()
            .filter(p -> p.getPriority() >= DecisionPriority.LEGAL_MANDATORY_STOP).toList();
        DecisionProposal winner;
        if (!overrides.isEmpty()) {
            winner = overrides.stream()
                .min(Comparator.comparingInt(DecisionProposal::getPriority).reversed().thenComparing(LOWEST_SPEED_FIRST))
                .orElseThrow();
        } else {
            // The base is what the vehicle would do on its own: a release (moving off,
            // resuming) when one holds, otherwise a comfort proposal. Ceilings only cap
            // it: the lowest ceiling decides when it is below the base, the base otherwise.
            List<DecisionProposal> releases = mine.stream()
                .filter(p -> p.getPriority() == DecisionPriority.MANEUVER_SAFETY).toList();
            List<DecisionProposal> comfort = mine.stream()
                .filter(p -> p.getPriority() <= DecisionPriority.COMFORT_PROGRESS).toList();
            DecisionProposal base = (!releases.isEmpty() ? releases : comfort).stream()
                .min(LOWEST_SPEED_FIRST).orElse(null);
            DecisionProposal ceiling = mine.stream().filter(DecisionContext::isCeiling)
                .min(LOWEST_SPEED_FIRST).orElse(null);
            if (base == null) {
                winner = ceiling;
            } else if (ceiling == null) {
                winner = base;
            } else {
                winner = LOWEST_SPEED_FIRST.compare(ceiling, base) < 0 && !sameSpeed(ceiling, base) ? ceiling : base;
            }
            if (winner == null) {
                winner = mine.stream().min(LOWEST_SPEED_FIRST).orElseThrow();
            }
        }
        settleLosers(mine, winner);
        return winner;
    }

    private static boolean sameSpeed(DecisionProposal a, DecisionProposal b) {
        return Math.abs(a.getRecommendedSpeedKmh() - b.getRecommendedSpeedKmh()) < 1e-6;
    }

    private DecisionProposal resolveLateral() {
        List<DecisionProposal> mine = proposals.stream()
            .filter(p -> p.getChannel() == DecisionChannel.LATERAL).toList();
        if (mine.isEmpty()) {
            return null;
        }
        DecisionProposal winner = mine.stream().min(LATERAL_ORDER).orElseThrow();
        settleLosers(mine, winner);
        return winner;
    }

    private static void settleLosers(List<DecisionProposal> candidates, DecisionProposal winner) {
        for (DecisionProposal candidate : candidates) {
            if (candidate != winner) {
                candidate.settle(DecisionProposal.Outcome.LOST_TO, winner.getSourceRule());
            }
        }
        winner.settle(DecisionProposal.Outcome.WON, "");
    }

    /** Speed ceilings: every one that holds binds, so the lowest decides. */
    private static boolean isCeiling(DecisionProposal p) {
        return p.getPriority() == DecisionPriority.COLLISION_AVOIDANCE
            || p.getPriority() == DecisionPriority.ROAD_HAZARD
            || p.getPriority() == DecisionPriority.SPEED_COMPLIANCE;
    }

    private static int lateralRank(DrivingAction action) {
        return switch (action) {
            case RETURN_RIGHT -> 0;
            case CHANGE_LANE_LEFT, CHANGE_LANE_RIGHT -> 1;
            default -> 2;
        };
    }

    @Override
    public String toString() {
        return "DecisionContext{timestamp=" + evaluationTimestamp + ", priority=" + priority
            + ", proposals=" + proposals + "}";
    }
}
