package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.drools.FactDescription;
import com.one2drive.projeto1.drools.KnowledgeSession;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.drools.facts.SpeedRadarFact;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntry;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import com.one2drive.projeto1.simulation.SensorTelemetry;
import com.one2drive.projeto1.simulation.SimulationEngine;
import com.one2drive.projeto1.simulation.SimulationSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the "what just happened" narrative shown in the UI.
 *
 * <p>Every line restates something the engine, the sensors or Drools already
 * produced. Nothing here infers a driving conclusion; if Drools has not decided
 * something, this class does not claim it.
 */
final class InferenceNarrator {

    /** Only these categories tell the user a meaningful change happened. */
    private static final List<TimelineEntryType> IMPORTANT_TYPES = List.of(
        TimelineEntryType.PERCEPTION_CHANGE,
        TimelineEntryType.MANEUVER,
        TimelineEntryType.DECISION,
        TimelineEntryType.RULE_FIRED
    );

    private InferenceNarrator() {
    }

    static ApiSimulationSnapshot.InferenceSummaryView summarise(SimulationEngine engine, SimulationSnapshot snapshot) {
        KnowledgeSession knowledge = engine.getKnowledgeSession();
        List<TimelineEntry> entries = knowledge.getTrace().snapshot();
        TimelineEntry lastImportant = lastImportantEntry(entries);

        return new ApiSimulationSnapshot.InferenceSummaryView(
            engine.getPreviousDecisionAction(),
            engine.getDecisionChangedAtMillis(),
            situationSteps(engine, snapshot, lastImportant),
            lastImportant == null ? null : new ApiSimulationSnapshot.ImportantEventView(
                lastImportant.getTimestamp(),
                category(lastImportant),
                lastImportant.getMessage(),
                detail(lastImportant)
            ),
            activeRule(entries)
        );
    }

    /**
     * Ordered narrative of the current cycle: what was perceived, what the sensors
     * reported, which rule fired, what was decided, and what is being executed.
     */
    private static List<String> situationSteps(SimulationEngine engine,
                                               SimulationSnapshot snapshot,
                                               TimelineEntry lastImportant) {
        List<String> steps = new ArrayList<>();
        SensorTelemetry telemetry = engine.getSensorTelemetry();

        SpeedRadarFact speedRadar = engine.activeSpeedRadarFact().orElse(null);
        if (speedRadar != null) {
            steps.add("Speed-control camera detects all-lane zone: configured radar limit "
                + round(speedRadar.getSpeedLimitKmh()) + " km/h at "
                + round(speedRadar.getDistanceMeters()) + "m; current vehicle speed "
                + round(snapshot.egoSpeedKmh()) + " km/h");
        }

        if (lastImportant != null && lastImportant.getType() == TimelineEntryType.PERCEPTION_CHANGE) {
            steps.add(lastImportant.getMessage());
        }

        if (!telemetry.frontCamera().detections().isEmpty()) {
            SensorTelemetry.CameraDetection nearest = telemetry.frontCamera().detections().stream()
                .min((a, b) -> Double.compare(a.distanceMeters(), b.distanceMeters()))
                .orElse(null);
            if (nearest != null) {
                steps.add("Camera perceives " + nearest.label() + " at "
                    + round(nearest.distanceMeters()) + "m" + laneSuffix(nearest.lane()));
            }
        }

        if (telemetry.frontRadar().status().equals("TRACKING")
            || telemetry.frontRadar().status().equals("WARNING")
            || telemetry.frontRadar().status().equals("CRITICAL")) {
            steps.add("Radar tracks " + telemetry.frontRadar().targetId() + " at "
                + round(telemetry.frontRadar().distanceMeters()) + "m, TTC "
                + round(telemetry.frontRadar().ttcSeconds()) + "s");
        }

        String left = sideLane("left", telemetry.blindSpotLeft());
        steps.add(Character.toUpperCase(left.charAt(0)) + left.substring(1)
            + ", " + sideLane("right", telemetry.blindSpotRight()));

        String rule = engine.getCurrentDecision().getSourceRule();
        if (rule != null && !rule.isBlank()) {
            steps.add("Drools fired \"" + rule + "\"");
        }

        steps.add("Decision: " + snapshot.currentDecision()
            + " at " + round(snapshot.recommendedSpeedKmh()) + " km/h");

        switch (snapshot.egoLaneChangeState()) {
            case "REQUESTED" -> steps.add("Vehicle is starting to move to the " + snapshot.egoTargetLane() + " lane");
            case "CHANGING" -> steps.add("Vehicle is moving to the " + snapshot.egoTargetLane() + " lane, "
                + Math.round(snapshot.egoLaneChangeProgress() * 100) + "% complete");
            case "COMPLETED" -> steps.add("Vehicle completed the lane change and is now in the "
                + snapshot.egoTargetLane() + " lane");
            // Always emit an execution step so the panel keeps a stable row count between cycles.
            default -> steps.add(isBraking(snapshot)
                ? "Vehicle is braking to " + round(snapshot.recommendedSpeedKmh()) + " km/h"
                : "Vehicle keeps " + round(snapshot.egoSpeedKmh()) + " km/h" + laneSuffix(snapshot.egoLane()));
        }

        return List.copyOf(steps);
    }

    private static String sideLane(String side, SensorTelemetry.BlindSpotView view) {
        return "NO LANE".equals(view.status())
            ? "no lane on the " + side
            : side + " lane " + view.status().toLowerCase();
    }

    private static boolean isBraking(SimulationSnapshot snapshot) {
        String action = snapshot.currentDecision();
        return DrivingAction.BRAKE.name().equals(action)
            || DrivingAction.EMERGENCY_BRAKE.name().equals(action)
            || DrivingAction.REDUCE_SPEED.name().equals(action)
            || DrivingAction.STOP.name().equals(action);
    }

    private static TimelineEntry lastImportantEntry(List<TimelineEntry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (IMPORTANT_TYPES.contains(entries.get(i).getType())) {
                return entries.get(i);
            }
        }
        return null;
    }

    private static String category(TimelineEntry entry) {
        return switch (entry.getType()) {
            case PERCEPTION_CHANGE -> "PERCEPTION";
            case MANEUVER -> "MANEUVER";
            case DECISION -> "DECISION";
            case RULE_FIRED -> "RULE";
            default -> "INFO";
        };
    }

    private static String detail(TimelineEntry entry) {
        String message = entry.getMessage();
        int arrow = message.indexOf("->");
        if (arrow > 0) {
            return message.substring(arrow + 2).trim();
        }
        return message;
    }

    private static String activeRule(List<TimelineEntry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).getType() == TimelineEntryType.RULE_FIRED) {
                return entries.get(i).getMessage();
            }
        }
        return null;
    }

    private static String laneSuffix(String lane) {
        if (lane == null || lane.isBlank() || "CAMERA".equals(lane)) {
            return "";
        }
        return " in the " + lane + " lane";
    }

    private static String round(double value) {
        return Double.isFinite(value) ? String.format("%.1f", value) : "none";
    }

    static String describeFact(Object fact) {
        return FactDescription.describe(fact);
    }
}
