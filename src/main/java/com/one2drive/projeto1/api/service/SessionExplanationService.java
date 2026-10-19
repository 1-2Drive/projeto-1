package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiWhyAnswer;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.SimulationSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats why/why-not answers exclusively from saved engine evidence and arbitration outcomes. */
@Service
public class SessionExplanationService {
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?");

    public ApiWhyAnswer answer(SimulationSession session, String question, Long explicitTimestamp, String mode) {
        List<SessionSnapshot> snapshots = session.decisionSnapshots();
        if (snapshots.isEmpty()) {
            return empty(session, question);
        }
        String q = question.toLowerCase(Locale.ROOT);
        Long requestedTime = explicitTimestamp != null ? explicitTimestamp : timestampFrom(question);
        List<SessionSnapshot> matching = snapshots.stream()
            .filter(s -> requestedTime == null || s.simulationTimeMillis() <= requestedTime)
            .toList();
        List<SessionSnapshot> timeCandidates = matching;
        matching = matching.stream().filter(s -> relevant(s, q)).toList();
        if (matching.isEmpty() && requestedTime != null) {
            matching = timeCandidates;
        }
        if (matching.isEmpty()) matching = snapshots;
        SessionSnapshot selected = matching.get(matching.size() - 1);

        boolean whyNot = "WHY_NOT".equalsIgnoreCase(mode) || asksWhyNot(q) || asksAlternative(q, selected);
        List<String> why = new ArrayList<>();
        why.addAll(selected.decision().evidence());
        if (!selected.decision().reason().isBlank()) why.add(selected.decision().reason());
        if (!selected.decision().sourceRule().isBlank()) why.add("Selected rule: " + selected.decision().sourceRule());
        selected.decision().proposals().stream().filter(p -> p.outcome().equals("WON"))
            .forEach(p -> why.add("Rule proposal won arbitration: " + p.rule() + " → " + p.action()
                + " (priority " + p.priority() + ")"));

        List<String> whyNotLines = new ArrayList<>();
        String alternative = alternativeAction(q);
        if (alternative != null) {
            selected.decision().proposals().stream().filter(p -> p.action().equals(alternative))
                .forEach(p -> whyNotLines.add(alternative + " was considered by " + p.rule() + " and "
                    + p.outcome() + " (priority " + p.priority() + "): " + p.conditions()));
            if (selected.decision().proposals().stream().noneMatch(p -> p.action().equals(alternative))) {
                whyNotLines.add(alternative + " was not proposed in the recorded inference cycle. The cycle contains no evaluation trace establishing a failed antecedent for this action.");
            }
            if (alternative.equals("BRAKE")) {
                explainBrakeAlternative(selected, whyNotLines);
            }
        }
        selected.decision().proposals().stream()
            .filter(p -> !p.outcome().equals("WON") && !p.outcome().equals("PENDING"))
            .forEach(p -> whyNotLines.add(p.action() + " from " + p.rule() + " was " + p.outcome()
                + (p.detail().isBlank() ? "" : ": " + p.detail())
                + " (priority " + p.priority() + "; " + p.conditions() + ")"));
        selected.decision().laneChecks().forEach(check -> check.subGoals().stream()
            .filter(goal -> !goal.holds())
            .forEach(goal -> whyNotLines.add(check.side() + " lane change unavailable: " + goal.detail())));
        if (whyNotLines.isEmpty()) {
            whyNotLines.add("The inference trace has no rejected proposal or recorded failed lane-safety condition for this alternative.");
        }

        List<String> observations = new ArrayList<>(selected.timeline().stream()
            .filter(e -> e.type().equals("PERCEPTION_CHANGE") || e.type().equals("FACT_INSERTED"))
            .map(e -> e.type() + ": " + e.message()).toList());
        if (!session.decisionEventSequences().isEmpty()) {
            List<Long> linked = session.decisionEventSequences().getOrDefault(
                selected.simulationTimeMillis() + ":" + selected.decision().action(), List.of());
            var linkedEvents = session.events().stream().filter(event -> linked.contains(event.sequence())).toList();
            linkedEvents.stream().filter(event -> event.type().equals("PERCEPTION_CHANGE")
                    || event.type().equals("WORLD_CHANGE"))
                .forEach(event -> observations.add(event.type() + " at " + event.simulationTimeMillis() + " ms: " + event.message()));
            linkedEvents.stream().filter(event -> event.type().equals("FACT_INSERTED"))
                .map(event -> event.message().toLowerCase(Locale.ROOT).contains("pothole")
                    ? event.message() : null)
                .filter(java.util.Objects::nonNull)
                .forEach(message -> observations.add("SENSOR / WORKING MEMORY: " + message));
        }
        if (selected.sensors().frontCamera() != null) {
            selected.sensors().frontCamera().detections().forEach(d -> observations.add("CAMERA: " + d.label()
                + " " + d.objectId() + " at " + d.distanceMeters() + " m, lane " + d.lane()));
        }
        boolean cameraInTimeline = observations.stream().anyMatch(s -> s.startsWith("FACT_INSERTED: Camera read"));
        if (!cameraInTimeline && selected.timeline().stream().anyMatch(e -> e.type().equals("FACT_INSERTED")
            && e.message().toLowerCase(Locale.ROOT).contains("pothole"))) {
            selected.timeline().stream().filter(e -> e.type().equals("FACT_INSERTED")
                && e.message().toLowerCase(Locale.ROOT).contains("pothole"))
                .forEach(e -> observations.add("CAMERA observation: " + e.message()));
        }
        if (selected.sensors().frontRadar() != null
            && !"none".equals(selected.sensors().frontRadar().targetId())) {
            observations.add("RADAR: " + selected.sensors().frontRadar().targetId() + " at "
                + selected.sensors().frontRadar().distanceMeters() + " m");
        }
        List<String> facts = new ArrayList<>(selected.workingMemory().stream()
            .map(f -> f.type() + ": " + f.value()).toList());
        if (selected.roadMap() != null) {
            facts.add("Road map at decision: ego row " + selected.roadMap().egoRow() + ", lap " + selected.roadMap().lap()
                + ", cells=" + java.util.Arrays.deepToString(selected.roadMap().cells()));
        }
        List<String> rules = selected.timeline().stream()
            .filter(e -> e.type().startsWith("RULE_"))
            .map(e -> e.type() + ": " + e.message()).toList();
        List<String> actuator = selected.timeline().stream()
            .filter(e -> e.type().equals("ACTUATOR") || e.type().equals("MANEUVER"))
            .map(e -> e.type() + ": " + e.message()).toList();
        actuator = new ArrayList<>(actuator);
        actuator.add(selected.decision().actuatorResult());
        long timestamp = selected.simulationTimeMillis();
        List<ApiWhyAnswer.HowStep> how = buildHowSteps(session, selected, observations, facts, rules, actuator);
        return new ApiWhyAnswer(session.id(), timestamp, question, selected.decision().action(), why,
            whyNot ? whyNotLines : List.of(), observations, facts, rules, actuator,
            selected.decision().proposals(), selected.decision().laneChecks(), session.backend(),
            explicitTimestamp == null && matching.size() > 1 ? matching : List.of(), how);
    }

    /** Reconstructs the WORLD -> SENSORS -> WORKING MEMORY -> RULES -> DECISION -> ACTUATOR process from recorded evidence. */
    private List<ApiWhyAnswer.HowStep> buildHowSteps(SimulationSession session, SessionSnapshot selected,
            List<String> observations, List<String> facts, List<String> rules, List<String> actuator) {
        List<String> worldEvidence = selected.worldObjects() == null || selected.worldObjects().isEmpty()
            ? List.of() : selected.worldObjects().stream().map(Object::toString).toList();
        List<String> sensorEvidence = new ArrayList<>(observations.stream()
            .filter(o -> o.startsWith("CAMERA") || o.startsWith("RADAR") || o.startsWith("PERCEPTION_CHANGE")
                || o.startsWith("CAMERA observation") || o.startsWith("SENSOR / WORKING MEMORY"))
            .toList());
        List<String> workingMemoryEvidence = new ArrayList<>(facts);
        List<String> ruleEvidence = new ArrayList<>(rules);
        if (!selected.decision().sourceRule().isBlank()) {
            ruleEvidence.add("Rule fired: " + selected.decision().sourceRule());
        }
        List<String> decisionEvidence = new ArrayList<>();
        decisionEvidence.add("Proposed action: " + selected.decision().action());
        selected.decision().proposals().stream().filter(p -> p.outcome().equals("WON"))
            .forEach(p -> decisionEvidence.add("Arbitration selected " + p.action() + " from " + p.rule()
                + " (priority " + p.priority() + ")"));
        selected.decision().laneChecks().forEach(check -> decisionEvidence.add(
            "Lane-safety check " + check.side() + ": " + (check.allowed() ? "PASSED" : "BLOCKED") + " — " + check.explanation()));
        List<String> actuatorEvidence = new ArrayList<>(actuator);

        List<ApiWhyAnswer.HowStep> steps = new ArrayList<>();
        steps.add(new ApiWhyAnswer.HowStep("WORLD", fallback(worldEvidence)));
        steps.add(new ApiWhyAnswer.HowStep("SENSORS", fallback(sensorEvidence)));
        steps.add(new ApiWhyAnswer.HowStep("WORKING MEMORY", fallback(workingMemoryEvidence)));
        steps.add(new ApiWhyAnswer.HowStep("RULE EVALUATION (" + session.backend() + ")", fallback(ruleEvidence)));
        steps.add(new ApiWhyAnswer.HowStep("DECISION / ARBITRATION", fallback(decisionEvidence)));
        steps.add(new ApiWhyAnswer.HowStep("ACTUATOR", fallback(actuatorEvidence)));
        return steps;
    }

    private static List<String> fallback(List<String> evidence) {
        return evidence.isEmpty() ? List.of("No recorded evidence available for this stage.") : evidence;
    }

    private boolean relevant(SessionSnapshot snapshot, String question) {
        String action = snapshot.decision().action().toLowerCase(Locale.ROOT);
        boolean asksAction = question.contains("left") || question.contains("right") || question.contains("lane")
            || question.contains("brake") || question.contains("stop") || question.contains("accelerat")
            || question.contains("speed") || question.contains("reduce");
        boolean actionMatches = (!question.contains("left") || action.contains("left"))
            && (!question.contains("right") || action.contains("right"))
            && (asksWhyNot(question) || !question.contains("brake") || action.contains("brake"))
            && (!question.contains("stop") || action.contains("stop"))
            && (!question.contains("accelerat") || action.contains("accelerat"))
            && (!question.contains("reduce") || action.contains("reduce"))
            && (!(question.contains("speed") && !question.contains("reduce")) || action.contains("speed") || action.contains("accelerat"));
        if (asksAction && !actionMatches && !asksWhyNot(question)) return false;
        if (question.contains("left") && !action.contains("left")) return false;
        if (question.contains("right") && !action.contains("right")) return false;
        if (question.contains("centre") || question.contains("center")) {
            if (!snapshot.decision().laneAction().contains("CENTER") && !snapshot.vehicle().lane().equals("CENTER")) return false;
        }
        if (question.contains("pothole")) {
            return snapshot.decision().reason().toLowerCase(Locale.ROOT).contains("pothole")
                || snapshot.decision().evidence().stream().anyMatch(e -> e.toLowerCase(Locale.ROOT).contains("pothole"))
                || snapshot.workingMemory().stream().anyMatch(f -> f.value().toLowerCase(Locale.ROOT).contains("pothole"));
        }
        return true;
    }

    private static boolean asksWhyNot(String question) {
        return question.contains("why didn") || question.contains("why not") || question.contains("instead of");
    }

    private static boolean asksAlternative(String question, SessionSnapshot snapshot) {
        return (question.contains("brake") && !snapshot.decision().action().contains("BRAKE"))
            || (question.contains("stay") && snapshot.decision().laneAction().startsWith("CHANGE"))
            || (question.contains("centre") && !snapshot.decision().laneAction().contains("CENTER"));
    }

    private static String alternativeAction(String question) {
        if (question.contains("brake") || question.contains("brak")) return "BRAKE";
        if (question.contains("stay") || question.contains("remain")) return "KEEP_LANE";
        if (question.contains("centre") || question.contains("center")) return "CHANGE_LANE_RIGHT";
        if (question.contains("left")) return "CHANGE_LANE_LEFT";
        if (question.contains("right")) return "CHANGE_LANE_RIGHT";
        return null;
    }

    private static void explainBrakeAlternative(SessionSnapshot snapshot, List<String> explanation) {
        String pothole = factValue(snapshot, "PotholeStateFact");
        if (pothole == null || pothole.contains("potholeId='none'")) return;
        String left = factValue(snapshot, "LaneOccupancyState", "lane=LEFT");
        String right = factValue(snapshot, "LaneOccupancyState", "lane=RIGHT");
        boolean egoRight = snapshot.vehicle().lane().equals("RIGHT");
        String escapeLaneOccupancy = egoRight ? left : right;
        boolean escapeOccupied = escapeLaneOccupancy != null && escapeLaneOccupancy.contains("occupied=true");
        explanation.add("Recorded pothole rule " + quote("Pothole ahead reduce speed")
            + " requires the adjacent escape lane(s) to be occupied before it proposes REDUCE_SPEED."
            + " The recorded " + (egoRight ? "left" : "right") + " lane fact is "
            + (escapeLaneOccupancy == null ? "unavailable" : escapeLaneOccupancy) + "."
            + (escapeOccupied ? "" : " Its occupied condition was false, so the pothole slow-down rule did not match."));
        String obstacle = factValue(snapshot, "ObstacleStateFact");
        if (obstacle != null && obstacle.contains("obstacleId='none'")) {
            explanation.add("The recorded obstacle-stop rule applies to ObstacleStateFact; the stored obstacle fact was "
                + obstacle + ", so that stop/brake rule did not match this pothole.");
        }
        String laneClear = escapeOccupied ? "occupied" : "clear";
        explanation.add("The selected lane-avoidance rule records the adjacent "
            + (egoRight ? "left" : "right") + " lane as " + laneClear
            + " and the rule engine's lane-safety check as "
            + snapshot.decision().laneChecks().stream()
                .filter(check -> check.side().equals(egoRight ? "LEFT" : "RIGHT"))
                .map(SessionSnapshot.LaneCheckSnapshot::explanation).findFirst().orElse("not recorded") + ".");
    }

    private static String factValue(SessionSnapshot snapshot, String type) {
        return factValue(snapshot, type, null);
    }

    private static String factValue(SessionSnapshot snapshot, String type, String contains) {
        return snapshot.workingMemory().stream().filter(f -> f.type().equals(type))
            .map(f -> f.value())
            .filter(value -> contains == null || value.contains(contains))
            .findFirst().orElse(null);
    }

    private static String quote(String text) { return "\"" + text + "\""; }

    private static Long timestampFrom(String question) {
        Matcher matcher = TIME.matcher(question);
        if (!matcher.find()) return null;
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String millisText = matcher.group(3);
        long millis = millisText == null ? 0 : Long.parseLong((millisText + "00").substring(0, 3));
        return minutes * 60_000 + seconds * 1_000 + millis;
    }

    private static ApiWhyAnswer empty(SimulationSession session, String question) {
        return new ApiWhyAnswer(session.id(), 0, question, "No decision snapshot", List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), session.backend(), List.of(), List.of());
    }
}
