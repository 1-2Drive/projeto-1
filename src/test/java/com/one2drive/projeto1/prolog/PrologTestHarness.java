package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.Lane;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs Prolog decision cycles the way DroolsTestHarness runs Drools ones. */
public class PrologTestHarness implements AutoCloseable {
    private final PrologKnowledgeSession session;
    private final DecisionContext context = new DecisionContext();
    private final DrivingDecision decision = new DrivingDecision();
    private PrologCycleResult lastResult;
    private double positionMeters;

    public PrologTestHarness() {
        this(0.0);
    }

    public PrologTestHarness(double cruiseTargetKmh) {
        assumeSwiplAvailable();
        this.session = PrologKnowledgeSession.start(cruiseTargetKmh);
    }

    public PrologKnowledgeSession session() {
        return session;
    }

    public PrologCycleResult lastResult() {
        return lastResult;
    }

    /** Places the vehicle along the road for the next cycle, as DroolsTestHarness.moveTo does. */
    public void moveTo(double positionMeters) {
        this.positionMeters = positionMeters;
    }

    public DrivingDecision cycle(long timestampMillis, double speedKmh, Object... events) {
        context.beginCycle(timestampMillis, speedKmh);
        lastResult = session.cycle(context,
            PrologKnowledgeSession.VehicleInput.keepingLane(timestampMillis, speedKmh, Lane.CENTER).at(positionMeters),
            List.of(events), decision);
        context.applyTo(decision);
        return decision;
    }

    /** Skips, rather than fails, the Prolog tests on a machine without SWI-Prolog. */
    public static void assumeSwiplAvailable() {
        boolean available;
        try {
            Process process = new ProcessBuilder(PrologProcess.executable(), "--version")
                .redirectErrorStream(true).start();
            available = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            available = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            available = false;
        }
        Assumptions.assumeTrue(available, "SWI-Prolog (swipl) is not installed");
    }

    @Override
    public void close() {
        session.close();
    }
}
