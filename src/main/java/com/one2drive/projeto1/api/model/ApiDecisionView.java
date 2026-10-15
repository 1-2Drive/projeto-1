package com.one2drive.projeto1.api.model;

import java.util.List;

/**
 * The decision of the last cycle. {@code action} is the headline (the lane maneuver while
 * one executes, the speed action otherwise); the two channels are also given explicitly,
 * and {@code proposals} lists everything the rules proposed with what became of it.
 */
public record ApiDecisionView(
    String action,
    double recommendedSpeedKmh,
    String riskLevel,
    String reason,
    String sourceRule,
    long timestamp,
    List<String> evidence,
    String speedAction,
    String speedReason,
    String speedSourceRule,
    String laneAction,
    String laneReason,
    String laneSourceRule,
    List<ApiProposalView> proposals
) {
    public record ApiProposalView(
        String rule,
        String action,
        String channel,
        int priority,
        Double recommendedSpeedKmh,
        String outcome,
        String detail,
        String reason
    ) {
    }
}
