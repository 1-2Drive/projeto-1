package com.one2drive.projeto1.api.model;

import java.util.List;

public record ApiDecisionView(
    String action,
    double recommendedSpeedKmh,
    String riskLevel,
    String reason,
    String sourceRule,
    long timestamp,
    List<String> evidence
) {
}
