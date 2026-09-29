package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DrivingDecision {
    private DrivingAction action;
    private double recommendedSpeedKmh;
    private RiskLevel riskLevel;
    private int priority;
    private String reason;
    private String sourceRule;
    private long timestamp;
    private final List<String> evidence;

    public DrivingDecision() {
        this.evidence = new ArrayList<>();
        reset(0L);
    }

    public void reset(long timestamp) {
        this.action = DrivingAction.CONTINUE;
        this.recommendedSpeedKmh = Double.NaN;
        this.riskLevel = RiskLevel.LOW;
        this.priority = 0;
        this.reason = "No restrictive condition detected";
        this.sourceRule = "Default continue decision";
        this.timestamp = timestamp;
        this.evidence.clear();
    }

    public void apply(DrivingAction action,
                      double recommendedSpeedKmh,
                      RiskLevel riskLevel,
                      int priority,
                      String reason,
                      String sourceRule,
                      long timestamp,
                      List<String> evidenceLines) {
        this.action = action;
        this.recommendedSpeedKmh = recommendedSpeedKmh;
        this.riskLevel = riskLevel;
        this.priority = priority;
        this.reason = reason;
        this.sourceRule = sourceRule;
        this.timestamp = timestamp;
        this.evidence.clear();
        if (evidenceLines != null) {
            this.evidence.addAll(evidenceLines);
        }
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

    public long getTimestamp() {
        return timestamp;
    }

    public List<String> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    @Override
    public String toString() {
        return "DrivingDecision{" +
            "action=" + action +
            ", recommendedSpeedKmh=" + recommendedSpeedKmh +
            ", riskLevel=" + riskLevel +
            ", priority=" + priority +
            ", reason='" + reason + '\'' +
            ", sourceRule='" + sourceRule + '\'' +
            ", timestamp=" + timestamp +
            '}';
    }
}
