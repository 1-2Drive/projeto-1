package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;

import java.util.ArrayList;
import java.util.List;

public class DecisionContext {
    private DrivingAction action;
    private double recommendedSpeedKmh;
    private RiskLevel riskLevel;
    private int priority;
    private String reason;
    private String sourceRule;
    private final List<String> evidence;
    private long evaluationTimestamp;

    public DecisionContext() {
        this.evidence = new ArrayList<>();
        beginCycle(0L, 0.0);
    }

    public void beginCycle(long evaluationTimestamp, double currentSpeedKmh) {
        this.evaluationTimestamp = evaluationTimestamp;
        this.action = DrivingAction.MAINTAIN_SPEED;
        this.recommendedSpeedKmh = Math.max(0.0, currentSpeedKmh);
        this.riskLevel = RiskLevel.LOW;
        this.priority = 0;
        this.reason = "Maintain current trajectory";
        this.sourceRule = "Default base decision";
        this.evidence.clear();
    }

    public void propose(DrivingAction action,
                        double recommendedSpeedKmh,
                        RiskLevel riskLevel,
                        int priority,
                        String reason,
                        String sourceRule,
                        List<String> evidenceLines) {
        boolean take = false;
        if (priority > this.priority) {
            take = true;
        } else if (priority == this.priority) {
            if (!Double.isNaN(recommendedSpeedKmh) && (Double.isNaN(this.recommendedSpeedKmh)
                || recommendedSpeedKmh < this.recommendedSpeedKmh)) {
                take = true;
            }
        }
        if (take) {
            this.action = action;
            this.recommendedSpeedKmh = recommendedSpeedKmh;
            this.riskLevel = riskLevel;
            this.priority = priority;
            this.reason = reason;
            this.sourceRule = sourceRule;
            this.evidence.clear();
            if (evidenceLines != null) {
                this.evidence.addAll(evidenceLines);
            }
        }
    }

    public long getEvaluationTimestamp() {
        return evaluationTimestamp;
    }

    public int getPriority() {
        return priority;
    }

    public void applyTo(DrivingDecision decision) {
        decision.apply(
            action,
            recommendedSpeedKmh,
            riskLevel,
            priority,
            reason,
            sourceRule,
            evaluationTimestamp,
            evidence
        );
    }
}
