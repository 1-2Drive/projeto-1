package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;

import java.util.Collections;
import java.util.List;

/**
 * What one rule proposed in one cycle, and what the arbitration did with it. Every
 * proposal of a cycle is kept, so the explanation can answer "why not": a losing rule
 * either lost to a stronger one in its channel, was vetoed by the speed decision, or had
 * its lane change refused by the actuator.
 */
public final class DecisionProposal {

    public enum Outcome {
        /** Not arbitrated yet. */
        PENDING,
        /** Became the decision of its channel. */
        WON,
        /** Another proposal of the same channel was stronger; {@link #getDetail()} names its rule. */
        LOST_TO,
        /** A lane change that an emergency or mandatory stop forbade; the detail names the speed rule. */
        VETOED_BY,
        /** A lane change the actuator refused to execute; the detail says why. */
        REJECTED_BY,
        /** A speed ceiling that bound below a mandatory stop's own speed; the detail names that rule. */
        CAPPED
    }

    private final DrivingAction action;
    private final double recommendedSpeedKmh;
    private final RiskLevel riskLevel;
    private final int priority;
    private final String reason;
    private final String sourceRule;
    private final List<String> evidence;
    private final int order;
    private Outcome outcome = Outcome.PENDING;
    private String detail = "";

    public DecisionProposal(DrivingAction action,
                            double recommendedSpeedKmh,
                            RiskLevel riskLevel,
                            int priority,
                            String reason,
                            String sourceRule,
                            List<String> evidence,
                            int order) {
        this.action = action;
        this.recommendedSpeedKmh = recommendedSpeedKmh;
        this.riskLevel = riskLevel;
        this.priority = priority;
        this.reason = reason;
        this.sourceRule = sourceRule;
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        this.order = order;
    }

    public DecisionChannel getChannel() {
        return action.channel();
    }

    public DrivingAction getAction() {
        return action;
    }

    public double getRecommendedSpeedKmh() {
        return recommendedSpeedKmh;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public int getPriority() {
        return priority;
    }

    public String getReason() {
        return reason;
    }

    public String getSourceRule() {
        return sourceRule;
    }

    public List<String> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    /** Position among the cycle's proposals, the rules' firing order. */
    public int getOrder() {
        return order;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    void settle(Outcome outcome, String detail) {
        this.outcome = outcome;
        this.detail = detail == null ? "" : detail;
    }

    @Override
    public String toString() {
        return "Proposal{" + sourceRule + " -> " + action
            + (Double.isNaN(recommendedSpeedKmh) ? "" : " " + Math.round(recommendedSpeedKmh) + " km/h")
            + " @" + priority + " " + outcome + (detail.isEmpty() ? "" : " " + detail) + "}";
    }
}
