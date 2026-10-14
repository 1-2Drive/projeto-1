package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.drools.facts.VehicleStateFact;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;

import java.util.concurrent.TimeUnit;

public class DroolsTestHarness implements AutoCloseable {
    private final KnowledgeSession knowledgeSession;
    private final VehicleStateFact vehicleStateFact;
    private final FactHandle vehicleHandle;
    private final FactHandle decisionHandle;

    public DroolsTestHarness(double initialSpeedKmh) {
        this.knowledgeSession = KnowledgeSession.createPseudoClockForTests(new ExplanationTrace(1000));
        this.vehicleStateFact = new VehicleStateFact(initialSpeedKmh, 0.0, com.one2drive.projeto1.domain.Lane.CENTER, 0L);
        this.vehicleHandle = knowledgeSession.getKieSession().insert(vehicleStateFact);
        this.decisionHandle = findHandle(knowledgeSession.getDrivingDecision());
    }

    public KnowledgeSession session() {
        return knowledgeSession;
    }

    /** Places the vehicle along the road for the next cycle, for rules that depend on reaching a point. */
    public void moveTo(double positionMeters) {
        vehicleStateFact.setPositionMeters(positionMeters);
    }

    public DrivingDecision cycle(long timestampMillis, double speedKmh, Object... events) {
        SessionPseudoClock clock = (SessionPseudoClock) knowledgeSession.getKieSession().getSessionClock();
        long now = clock.getCurrentTime();
        if (timestampMillis > now) {
            clock.advanceTime(timestampMillis - now, TimeUnit.MILLISECONDS);
        }

        vehicleStateFact.setSpeedKmh(speedKmh);
        vehicleStateFact.setTimestamp(timestampMillis);
        knowledgeSession.getKieSession().update(vehicleHandle, vehicleStateFact);

        // As SimulationEngine does: reset the context and tell Drools it changed, so rules
        // whose own facts did not change this cycle still fire again.
        knowledgeSession.beginDecisionCycle(timestampMillis, speedKmh);
        DecisionContext context = knowledgeSession.getDecisionContext();

        for (Object event : events) {
            knowledgeSession.getRouter().insertEvent(event);
        }

        knowledgeSession.getKieSession().fireAllRules();
        context.applyTo(knowledgeSession.getDrivingDecision());
        knowledgeSession.getKieSession().update(decisionHandle, knowledgeSession.getDrivingDecision());
        return knowledgeSession.getDrivingDecision();
    }

    private FactHandle findHandle(Object fact) {
        for (FactHandle handle : knowledgeSession.getKieSession().getFactHandles()) {
            if (knowledgeSession.getKieSession().getObject(handle) == fact) {
                return handle;
            }
        }
        throw new IllegalStateException("Fact handle not found");
    }

    @Override
    public void close() {
        knowledgeSession.close();
    }
}
