package com.one2drive.projeto1.prolog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DecisionPriority;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RiskLevel;
import com.one2drive.projeto1.domain.RoadMarkingType;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The SWI-Prolog counterpart of the Drools {@code KnowledgeSession}: same sensor events in,
 * proposals for the same {@link DecisionContext} out. The perception state lives in the
 * Prolog process (driving_state.pl), so one session is one process, and a reset of the
 * simulation is a reset of this session.
 */
public class PrologKnowledgeSession implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_MILLIS = 2_000;

    private final PrologProcess process;

    private PrologKnowledgeSession(PrologProcess process) {
        this.process = process;
    }

    /**
     * Starts {@code swipl} and seeds the session as Drools seeds a new KieSession: no sign
     * seen yet, nothing perceived, and the scenario's cruise speed.
     */
    public static PrologKnowledgeSession start(double cruiseTargetKmh) {
        PrologProcess process;
        try {
            process = new PrologProcess(MAPPER, DEFAULT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            throw new PrologEngineException("Unable to start SWI-Prolog (is 'swipl' on PATH, or set -Dprolog.executable?): " + e.getMessage(), e);
        }
        PrologKnowledgeSession session = new PrologKnowledgeSession(process);
        try {
            session.reset(cruiseTargetKmh);
        } catch (RuntimeException e) {
            process.close();
            throw e;
        }
        return session;
    }

    public void reset(double cruiseTargetKmh) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cmd", "reset");
        request.put("cruiseTargetKmh", cruiseTargetKmh);
        request.put("params", stoppingParameters());
        request.put("priorities", priorities());
        process.request(request);
    }

    /**
     * What Prolog is told about the ego vehicle itself each cycle. The sensors describe the
     * world around it; this is what Drools reads from VehicleStateFact.
     */
    public record VehicleInput(long timestampMillis,
                               double speedKmh,
                               double positionMeters,
                               Lane lane,
                               LaneChangeState laneChangeState,
                               Lane targetLane,
                               RoadMarkingType leftMarking,
                               RoadMarkingType rightMarking,
                               boolean leftAdjacentLaneSafe,
                               boolean rightAdjacentLaneSafe,
                               boolean rightLaneBlockedAhead,
                               boolean presentationDemo) {

        /**
         * A vehicle keeping its lane between dashed markings, neither adjacent lane verified
         * safe, at the start of the road: the same defaults a new VehicleStateFact has.
         */
        public static VehicleInput keepingLane(long timestampMillis, double speedKmh, Lane lane) {
            return new VehicleInput(timestampMillis, speedKmh, 0.0, lane, LaneChangeState.IDLE, lane,
                RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, false, false, false);
        }

        /** The same input with the vehicle at {@code positionMeters} along the road. */
        public VehicleInput at(double positionMeters) {
            return new VehicleInput(timestampMillis, speedKmh, positionMeters, lane, laneChangeState, targetLane,
                leftMarking, rightMarking, leftAdjacentLaneSafe, rightAdjacentLaneSafe, rightLaneBlockedAhead,
                presentationDemo);
        }
    }

    /**
     * Runs one decision cycle: the vehicle state and this tick's sensor events go in, the
     * proposals of every rule that fired come out. Proposals are not arbitrated here.
     *
     * @param previous the decision arbitrated in the previous cycle, which Drools rules also
     *                 see as the DrivingDecision fact; null before the first cycle
     */
    public PrologCycleResult cycle(VehicleInput vehicle, List<?> events, DrivingDecision previous) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cmd", "tick");
        request.put("timestamp", vehicle.timestampMillis());
        request.put("speedKmh", vehicle.speedKmh());
        request.put("positionMeters", vehicle.positionMeters());
        request.put("lane", vehicle.lane().name());
        request.put("laneChangeState", vehicle.laneChangeState().name());
        request.put("targetLane", vehicle.targetLane().name());
        request.put("leftMarking", vehicle.leftMarking().name());
        request.put("rightMarking", vehicle.rightMarking().name());
        request.put("leftAdjacentLaneSafe", vehicle.leftAdjacentLaneSafe());
        request.put("rightAdjacentLaneSafe", vehicle.rightAdjacentLaneSafe());
        request.put("rightLaneBlockedAhead", vehicle.rightLaneBlockedAhead());
        request.put("presentationDemo", vehicle.presentationDemo());
        request.put("events", events.stream().map(PrologKnowledgeSession::toJson).toList());
        if (previous != null) {
            request.put("previousDecision", Map.of(
                "action", previous.getAction().name(),
                "rule", previous.getSourceRule() == null ? "" : previous.getSourceRule(),
                "priority", previous.getPriority()));
        }
        JsonNode reply = process.request(request);

        List<PrologProposal> proposals = new ArrayList<>();
        for (JsonNode node : reply.path("proposals")) {
            JsonNode speed = node.path("speedKmh");
            proposals.add(new PrologProposal(
                node.path("rule").asText(),
                DrivingAction.valueOf(node.path("action").asText()),
                // A lane change carries no target speed: null, which Drools writes as NaN.
                speed.isNull() ? Double.NaN : speed.asDouble(),
                RiskLevel.valueOf(node.path("risk").asText()),
                node.path("priority").asInt(),
                node.path("reason").asText(),
                texts(node.path("evidence"))));
        }
        List<PrologCycleResult.WorkingMemoryFact> facts = new ArrayList<>();
        for (JsonNode node : reply.path("facts")) {
            JsonNode rule = node.path("rule");
            facts.add(new PrologCycleResult.WorkingMemoryFact(
                node.path("n").asInt(), node.path("fact").asText(), rule.isNull() ? null : rule.asText()));
        }
        return new PrologCycleResult(List.copyOf(proposals), List.copyOf(facts),
            reply.path("speedLimitKmh").asDouble());
    }

    public PrologCycleResult cycle(long timestampMillis, double speedKmh, Lane lane, List<?> events,
                                   DrivingDecision previous) {
        return cycle(VehicleInput.keepingLane(timestampMillis, speedKmh, lane), events, previous);
    }

    /** Runs a cycle and hands every proposal to {@code context}, which arbitrates them. */
    public PrologCycleResult cycle(DecisionContext context, VehicleInput vehicle, List<?> events,
                                   DrivingDecision previous) {
        PrologCycleResult result = cycle(vehicle, events, previous);
        result.proposals().forEach(proposal -> proposal.proposeTo(context));
        return result;
    }

    public PrologCycleResult cycle(DecisionContext context, long timestampMillis, double speedKmh, Lane lane, List<?> events,
                                   DrivingDecision previous) {
        return cycle(context, VehicleInput.keepingLane(timestampMillis, speedKmh, lane), events, previous);
    }

    public PrologCycleResult cycle(DecisionContext context, long timestampMillis, double speedKmh, Lane lane,
                                   LaneChangeState laneChangeState, List<?> events, DrivingDecision previous) {
        return cycle(context, new VehicleInput(timestampMillis, speedKmh, 0.0, lane, laneChangeState, lane,
            RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, false, false, false), events, previous);
    }

    /** The conditions of {@code ruleId} that do not hold in the last cycle's working memory. */
    public List<String> whyNot(String ruleId) {
        JsonNode reply = process.request(Map.of("cmd", "whynot", "rule", ruleId));
        return texts(reply.path("failedConditions"));
    }

    private static JsonNode toJson(Object event) {
        ObjectNode node = MAPPER.valueToTree(event);
        node.put("type", event.getClass().getSimpleName());
        return node;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(item -> texts.add(item.asText()));
        return List.copyOf(texts);
    }

    /** The stopping figures stay in StoppingPolicy; Prolog receives them, it does not copy them. */
    private static Map<String, Object> stoppingParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("comfortDecelerationMps2", StoppingPolicy.COMFORT_DECELERATION_MPS2);
        params.put("anticipationDecelerationMps2", StoppingPolicy.ANTICIPATION_DECELERATION_MPS2);
        params.put("reactionTimeSeconds", StoppingPolicy.REACTION_TIME_SECONDS);
        params.put("redLightStopLineOffsetMeters", StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS);
        params.put("stopLineToleranceMeters", StoppingPolicy.STOP_LINE_TOLERANCE_METERS);
        params.put("stopSignOffsetMeters", StoppingPolicy.STOP_SIGN_OFFSET_METERS);
        params.put("speedLimitApproachBufferMeters", StoppingPolicy.SPEED_LIMIT_APPROACH_BUFFER_METERS);
        return params;
    }

    /** DecisionPriority constants by lower-case name, e.g. legal_mandatory_stop -> 60. */
    private static Map<String, Object> priorities() {
        Map<String, Object> priorities = new LinkedHashMap<>();
        for (Field field : DecisionPriority.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                try {
                    priorities.put(field.getName().toLowerCase(Locale.ROOT), field.getInt(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return priorities;
    }

    @Override
    public void close() {
        process.close();
    }
}
