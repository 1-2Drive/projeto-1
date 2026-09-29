package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.decision.DrivingDecision;

import java.util.List;
import java.util.Map;

public record SimulationSnapshot(
    long simTimeMillis,
    SimulationStatus status,
    double egoSpeedKmh,
    double egoPositionMeters,
    String egoLane,
    double legalLimitKmh,
    double recommendedSpeedKmh,
    String currentDecision,
    String riskLevel,
    String decisionReason,
    String sourceRule,
    Map<String, String> sensorValues,
    List<String> workingMemoryFacts,
    List<String> timeline
) {
    public static SimulationSnapshot empty() {
        return new SimulationSnapshot(
            0,
            SimulationStatus.STOPPED,
            0,
            0,
            "CENTER",
            120,
            0,
            "MAINTAIN_SPEED",
            "LOW",
            "No restrictive condition detected",
            "Default maintain speed baseline",
            Map.of(),
            List.of(),
            List.of()
        );
    }
}
