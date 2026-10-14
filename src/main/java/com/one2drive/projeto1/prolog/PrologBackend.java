package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.SpeedRadar;
import com.one2drive.projeto1.drools.FactDescription;
import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.drools.events.BaseEvent;
import com.one2drive.projeto1.drools.facts.SpeedRadarFact;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import com.one2drive.projeto1.inference.DecisionInput;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.inference.ExpertSystemBackend;
import com.one2drive.projeto1.simulation.WorldState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The SWI-Prolog knowledge base as an {@link ExpertSystemBackend}. The events of a tick are
 * collected and sent in one request when the engine asks for the decision; proposals are
 * arbitrated by a {@link DecisionContext}, exactly as Drools' are.
 */
public class PrologBackend implements ExpertSystemBackend {
    private final SimulationConfig config;
    private final PrologKnowledgeSession session;
    private final ExplanationTrace trace = new ExplanationTrace(500);
    private final DecisionContext context = new DecisionContext();
    private final DrivingDecision decision = new DrivingDecision();
    private final Consumer<DrivingDecision> decisionConsumer;
    private final List<Object> cycleEvents = new ArrayList<>();
    private final List<Object> pendingCommands = new ArrayList<>();
    private PrologKnowledgeSession.VehicleInput vehicle =
        PrologKnowledgeSession.VehicleInput.keepingLane(0L, 0.0, Lane.CENTER);
    private PrologCycleResult lastResult =
        new PrologCycleResult(List.of(), List.of(), 120.0, PrologCycleResult.RadarState.NONE);
    /** Nearest radar ahead this cycle, read from the world before the rules run. */
    private PrologKnowledgeSession.RadarInput radarInput = PrologKnowledgeSession.RadarInput.NONE;
    /** Radar the ego passed after the last actuation; Prolog marks it passed next cycle. */
    private String passedRadarId;
    /** Between updateVehicleState and decide: the radar the rules are about to see. */
    private boolean radarUpdatedThisCycle;
    private boolean closed;

    public PrologBackend(SimulationConfig config, Consumer<DrivingDecision> decisionConsumer, double cruiseTargetKmh) {
        this.config = config;
        this.decisionConsumer = decisionConsumer;
        this.session = PrologKnowledgeSession.start(cruiseTargetKmh);
        trace.add(0L, TimelineEntryType.INFO, "SWI-Prolog backend started");
    }

    public PrologKnowledgeSession session() {
        return session;
    }

    @Override
    public ExpertBackendType type() {
        return ExpertBackendType.PROLOG;
    }

    @Override
    public ExplanationTrace getTrace() {
        return trace;
    }

    @Override
    public DrivingDecision getDrivingDecision() {
        return decision;
    }

    @Override
    public void advanceClock(long millis) {
        // Time reaches Prolog as the timestamp of each cycle.
    }

    @Override
    public void beginDecisionCycle(long timestampMillis, double currentSpeedKmh) {
        context.beginCycle(timestampMillis, currentSpeedKmh);
        cycleEvents.clear();
    }

    /**
     * Logs each sensor event as Drools' WorkingMemoryTraceListener does on insertion, with the
     * same description, so the timeline tells the same perception story for both engines.
     */
    @Override
    public void insertEvent(Object event) {
        cycleEvents.add(event);
        traceInserted(event);
    }

    @Override
    public synchronized void submitCommand(Object event) {
        pendingCommands.add(event);
        traceInserted(event);
    }

    private void traceInserted(Object event) {
        long timestamp = event instanceof BaseEvent base ? base.getTimestamp() : vehicle.timestampMillis();
        trace.add(timestamp, TimelineEntryType.FACT_INSERTED, "FACT INSERTED " + FactDescription.describe(event));
    }

    @Override
    public void updateVehicleState(DecisionInput input) {
        var world = input.world();
        var ego = world.getEgo();
        // Same inputs DroolsBackend writes into the VehicleStateFact.
        vehicle = new PrologKnowledgeSession.VehicleInput(
            input.timestampMillis(),
            ego.getSpeedKmh(),
            ego.getLane(),
            ego.getLaneChangeState(),
            ego.getTargetLane(),
            world.getLeftMarking(),
            world.getRightMarking(),
            world.isLaneSafe(ego.getLane().left(), config.getBlindSpotRangeMeters(), 60.0),
            world.isLaneSafe(ego.getLane().right(), config.getBlindSpotRangeMeters(), 60.0),
            world.hasHazardAheadInLane(Lane.RIGHT, 150.0),
            "S14_PRESENTATION_DEMO".equals(input.scenarioId()));
        // DroolsBackend.updateActiveRadarFact: the nearest radar within 250 m becomes the
        // detected radar, unless the current one has been passed.
        var radar = world.findNearestSpeedRadarAhead(250.0);
        radarInput = new PrologKnowledgeSession.RadarInput(
            radar.map(r -> r.getId()).orElse(null),
            radar.map(r -> world.signedDistance(r.getPositionMeters())).orElse(0.0),
            radar.map(r -> r.getSpeedLimitKmh()).orElse(0.0),
            passedRadarId);
        radarUpdatedThisCycle = true;
    }

    @Override
    public void decide() {
        List<Object> events = new ArrayList<>(cycleEvents);
        synchronized (this) {
            events.addAll(pendingCommands);
            pendingCommands.clear();
        }
        // decision still holds the previous cycle's outcome until applyTo below.
        lastResult = session.cycle(context, vehicle, radarInput, events, decision);
        passedRadarId = null;
        radarUpdatedThisCycle = false;
        context.applyTo(decision);
        traceFiredRules();
        decisionConsumer.accept(decision);
    }

    /** One entry per rule that concluded something this cycle, as Drools' agenda listener does per firing. */
    private void traceFiredRules() {
        Set<String> rules = new LinkedHashSet<>();
        for (PrologCycleResult.WorkingMemoryFact fact : lastResult.workingMemory()) {
            if (fact.rule() != null) {
                rules.add(fact.rule());
            }
        }
        for (String rule : rules) {
            trace.add(vehicle.timestampMillis(), TimelineEntryType.RULE_FIRED, "RULE FIRED \"" + rule + "\"");
        }
    }

    /** DroolsBackend.recordRadarPassage: once the ego is past the detected radar, it is passed. */
    @Override
    public void afterActuation(WorldState world, long timestampMillis) {
        PrologCycleResult.RadarState radar = lastResult.radar();
        if (!radar.active() || radar.radarId().equals(passedRadarId)) {
            return;
        }
        var device = world.getRoadElements().stream()
            .filter(SpeedRadar.class::isInstance)
            .map(SpeedRadar.class::cast)
            .filter(candidate -> candidate.getId().equals(radar.radarId()))
            .findFirst();
        if (device.isPresent() && world.signedDistance(device.get().getPositionMeters()) <= 0.0) {
            passedRadarId = radar.radarId();
            trace.add(timestampMillis, TimelineEntryType.INFO,
                "Speed radar passed at " + Math.round(world.getEgo().getSpeedKmh())
                    + " km/h — legal limit " + Math.round(radar.speedLimitKmh()) + " km/h");
        }
    }

    @Override
    public double currentSpeedLimitKmh() {
        return lastResult.speedLimitKmh();
    }

    /**
     * The radar as Drools would hold it at this point of the tick: after updateVehicleState
     * it is the one just read from the world (what DroolsBackend writes before the rules
     * run), otherwise the state Prolog returned; a radar passed after the actuator is gone.
     */
    @Override
    public Optional<SpeedRadarFact> activeSpeedRadar() {
        PrologCycleResult.RadarState radar = lastResult.radar();
        if (radar.passed() || radar.radarId().equals(passedRadarId)) {
            return Optional.empty();
        }
        if (radarUpdatedThisCycle && radarInput.aheadId() != null) {
            return Optional.of(new SpeedRadarFact(radarInput.aheadId(), radarInput.aheadDistanceMeters(),
                radarInput.aheadSpeedLimitKmh(), vehicle.timestampMillis(), false, true));
        }
        if (!radar.active()) {
            return Optional.empty();
        }
        return Optional.of(new SpeedRadarFact(radar.radarId(), radar.distanceMeters(), radar.speedLimitKmh(),
            vehicle.timestampMillis(), false, true));
    }

    @Override
    public List<String> factSnapshot() {
        return lastResult.workingMemory().stream()
            .map(fact -> fact.rule() == null ? fact.fact() : fact.fact() + " <- " + fact.rule())
            .toList();
    }

    @Override
    public List<WorkingMemoryFactView> factViews() {
        return lastResult.workingMemory().stream()
            .map(fact -> new WorkingMemoryFactView(
                functor(fact.fact()),
                fact.fact(),
                fact.rule() == null ? "PERCEPTION" : fact.rule(),
                vehicle.timestampMillis(),
                "PROLOG",
                "wm_fact(" + fact.number() + ", " + fact.fact() + ")"))
            .toList();
    }

    @Override
    public List<String> queryCurrentDecision() {
        return List.of(decision.toString());
    }

    private static String functor(String fact) {
        int paren = fact.indexOf('(');
        return paren < 0 ? fact : fact.substring(0, paren);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            session.close();
        }
    }
}
