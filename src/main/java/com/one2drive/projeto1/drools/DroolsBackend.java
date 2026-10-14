package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.drools.facts.CruiseTargetFact;
import com.one2drive.projeto1.drools.facts.CurrentSpeedLimit;
import com.one2drive.projeto1.drools.facts.VehicleStateFact;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import com.one2drive.projeto1.inference.DecisionInput;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.inference.ExpertSystemBackend;
import com.one2drive.projeto1.simulation.WorldState;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** The Drools KieSession as an {@link ExpertSystemBackend}. */
public class DroolsBackend implements ExpertSystemBackend {
    /** Upper bound on rule firings in one tick; a normal tick fires well under a hundred. */
    private static final int MAX_RULE_FIRINGS_PER_TICK = 5000;

    private final SimulationConfig config;
    private final KnowledgeSession knowledgeSession;
    private final VehicleStateFact vehicleStateFact;
    private final FactHandle vehicleStateHandle;
    private boolean closed;

    /**
     * Opens a session seeded with the ego vehicle as it is now and the scenario's cruise
     * speed: the only target that can accelerate the vehicle again after a rule slowed it
     * down (brake, overtake, stop) and no speed-limit sign governs the road.
     */
    public DroolsBackend(SimulationConfig config,
                         boolean pseudoClock,
                         Consumer<DrivingDecision> decisionConsumer,
                         WorldState world,
                         long timestampMillis,
                         double cruiseTargetKmh) {
        this.config = config;
        ExplanationTrace trace = new ExplanationTrace(500);
        this.knowledgeSession = pseudoClock
            ? KnowledgeSession.createPseudoClock(trace, decisionConsumer)
            : KnowledgeSession.createRealtime(trace, decisionConsumer);
        this.vehicleStateFact = new VehicleStateFact(
            world.getEgo().getSpeedKmh(),
            world.getEgo().getPositionMeters(),
            world.getEgo().getLane(),
            timestampMillis
        );
        this.vehicleStateHandle = knowledgeSession.getKieSession().insert(vehicleStateFact);
        knowledgeSession.getKieSession().insert(new CruiseTargetFact(cruiseTargetKmh));
    }

    public KnowledgeSession session() {
        return knowledgeSession;
    }

    @Override
    public ExpertBackendType type() {
        return ExpertBackendType.DROOLS;
    }

    @Override
    public ExplanationTrace getTrace() {
        return knowledgeSession.getTrace();
    }

    @Override
    public DrivingDecision getDrivingDecision() {
        return knowledgeSession.getDrivingDecision();
    }

    @Override
    public void advanceClock(long millis) {
        if (knowledgeSession.getKieSession().getSessionClock() instanceof SessionPseudoClock pseudoClock) {
            pseudoClock.advanceTime(millis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void beginDecisionCycle(long timestampMillis, double currentSpeedKmh) {
        knowledgeSession.beginDecisionCycle(timestampMillis, currentSpeedKmh);
    }

    @Override
    public void insertEvent(Object event) {
        knowledgeSession.getRouter().insertEvent(event);
    }

    @Override
    public void submitCommand(Object event) {
        knowledgeSession.getRouter().insertEvent(event);
    }

    @Override
    public void updateVehicleState(DecisionInput input) {
        updateVehicleStateFact(input);
    }

    @Override
    public void decide() {
        // Circuit breaker: rules that re-trigger each other must never freeze the tick.
        int fired = knowledgeSession.getKieSession().fireAllRules(MAX_RULE_FIRINGS_PER_TICK);
        if (fired >= MAX_RULE_FIRINGS_PER_TICK) {
            knowledgeSession.getTrace().add(knowledgeSession.getDecisionContext().getEvaluationTimestamp(),
                TimelineEntryType.INFO,
                "Rule firing limit reached (" + MAX_RULE_FIRINGS_PER_TICK + "): possible rule loop");
        }
        DrivingDecision decision = knowledgeSession.getDrivingDecision();
        knowledgeSession.getDecisionContext().applyTo(decision);

        FactHandle decisionHandle = findFactHandle(decision);
        if (decisionHandle != null) {
            knowledgeSession.getKieSession().update(decisionHandle, decision);
        }
    }

    @Override
    public double currentSpeedLimitKmh() {
        for (Object obj : knowledgeSession.getKieSession().getObjects(new ClassObjectFilter(CurrentSpeedLimit.class))) {
            return ((CurrentSpeedLimit) obj).getLimitKmh();
        }
        return 120;
    }

    @Override
    public List<String> factSnapshot() {
        return knowledgeSession.getInspector().snapshotFacts();
    }

    @Override
    public List<WorkingMemoryFactView> factViews() {
        return knowledgeSession.getInspector().snapshotFactViews();
    }

    @Override
    public List<String> queryCurrentDecision() {
        return knowledgeSession.getInspector().queryCurrentDecision();
    }

    private void updateVehicleStateFact(DecisionInput input) {
        WorldState worldState = input.world();
        EgoVehicleState ego = worldState.getEgo();
        vehicleStateFact.setSpeedKmh(ego.getSpeedKmh());
        vehicleStateFact.setPositionMeters(ego.getPositionMeters());
        vehicleStateFact.setLane(ego.getLane());
        vehicleStateFact.setLaneChangeState(ego.getLaneChangeState());
        vehicleStateFact.setTargetLane(ego.getTargetLane());
        vehicleStateFact.setTimestamp(input.timestampMillis());
        vehicleStateFact.setDrivingState(ego.getDrivingState());
        vehicleStateFact.setIndicatorState(ego.getIndicatorState());
        vehicleStateFact.setLateralPositionMeters(ego.getLateralPositionMeters());
        vehicleStateFact.setLeftMarking(worldState.getLeftMarking());
        vehicleStateFact.setRightMarking(worldState.getRightMarking());
        vehicleStateFact.setRightLaneBlockedAhead(worldState.hasHazardAheadInLane(com.one2drive.projeto1.domain.Lane.RIGHT, 150.0));
        vehicleStateFact.setLeftAdjacentLaneSafe(worldState.isLaneSafe(ego.getLane().left(), config.getBlindSpotRangeMeters(), 60.0));
        vehicleStateFact.setRightAdjacentLaneSafe(worldState.isLaneSafe(ego.getLane().right(), config.getBlindSpotRangeMeters(), 60.0));
        vehicleStateFact.setPresentationDemo("S12_PRESENTATION_DEMO".equals(input.scenarioId()));
        knowledgeSession.getKieSession().update(vehicleStateHandle, vehicleStateFact);
    }

    private FactHandle findFactHandle(Object object) {
        for (FactHandle handle : knowledgeSession.getKieSession().getFactHandles()) {
            Object candidate = knowledgeSession.getKieSession().getObject(handle);
            if (candidate == object) {
                return handle;
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            knowledgeSession.close();
        }
    }
}
