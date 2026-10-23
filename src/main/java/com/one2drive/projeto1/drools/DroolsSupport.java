package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;

import java.util.List;

public class DroolsSupport {
    public void proposeDecision(DecisionContext context,
                                DrivingAction action,
                                double recommendedSpeedKmh,
                                RiskLevel riskLevel,
                                int priority,
                                String reason,
                                String sourceRule,
                                List<String> evidence) {
        context.propose(action, recommendedSpeedKmh, riskLevel, priority, reason, sourceRule, evidence);
    }

    public void proposeDecision(DecisionContext context,
                                DrivingAction action,
                                double recommendedSpeedKmh,
                                RiskLevel riskLevel,
                                int priority,
                                String reason,
                                String sourceRule,
                                String evidence) {
        context.propose(action, recommendedSpeedKmh, riskLevel, priority, reason, sourceRule, List.of(evidence));
    }
}
