package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;

import java.util.List;

/**
 * A decision proposed by one Prolog rule in one cycle. It carries exactly what a Drools
 * rule hands to {@link DecisionContext#propose}, so both backends are arbitrated alike.
 */
public record PrologProposal(String rule,
                             DrivingAction action,
                             double speedKmh,
                             RiskLevel risk,
                             int priority,
                             String reason,
                             List<String> evidence) {

    public void proposeTo(DecisionContext context) {
        context.propose(action, speedKmh, risk, priority, reason, rule, evidence);
    }
}
