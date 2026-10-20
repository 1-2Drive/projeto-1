package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.drools.FactDescription;
import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.drools.events.BaseEvent;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import com.one2drive.projeto1.inference.DecisionInput;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.inference.ExpertSystemBackend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final ExplanationTrace trace = new ExplanationTrace(0);
    private final DecisionContext context = new DecisionContext();
    private final DrivingDecision decision = new DrivingDecision();
    private final Consumer<DrivingDecision> decisionConsumer;
    private final List<Object> cycleEvents = new ArrayList<>();
    private final List<Object> pendingCommands = new ArrayList<>();
    private PrologKnowledgeSession.VehicleInput vehicle =
        PrologKnowledgeSession.VehicleInput.keepingLane(0L, 0.0, Lane.CENTER);
    private PrologCycleResult lastResult =
        new PrologCycleResult(List.of(), List.of(), 120.0);
    private boolean closed;

    public PrologBackend(SimulationConfig config, Consumer<DrivingDecision> decisionConsumer, double cruiseTargetKmh) {
        this.config = config;
        this.decisionConsumer = decisionConsumer;
        this.session = PrologKnowledgeSession.start(cruiseTargetKmh);
        trace.add(0L, TimelineEntryType.INFO, "SWI-Prolog backend started");
    }

    /** What the last decision cycle produced, its cost included. */
    public PrologCycleResult lastCycleResult() {
        return lastResult;
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
            ego.getPositionMeters(),
            ego.getLane(),
            ego.getLaneChangeState(),
            ego.getTargetLane(),
            world.getLeftMarking(),
            world.getRightMarking(),
            world.isLaneSafe(ego.getLane().left(), config.getBlindSpotRangeMeters(), 60.0),
            world.isLaneSafe(ego.getLane().right(), config.getBlindSpotRangeMeters(), 60.0),
            world.hasHazardAheadInLane(Lane.RIGHT, 150.0),
            "S12_PRESENTATION_DEMO".equals(input.scenarioId()),
            ego.getLaneSettledAtMillis(),
            Lane.fromLateralPosition(ego.getLateralPositionMeters()));
    }

    @Override
    public void decide() {
        List<Object> events = new ArrayList<>(cycleEvents);
        synchronized (this) {
            events.addAll(pendingCommands);
            pendingCommands.clear();
        }
        // decision still holds the previous cycle's outcome until applyTo below.
        lastResult = session.cycle(context, vehicle, events, decision);
        context.applyTo(decision);
        decision.setLaneChecks(lastResult.laneChecks());
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

    @Override
    public double currentSpeedLimitKmh() {
        return lastResult.speedLimitKmh();
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
