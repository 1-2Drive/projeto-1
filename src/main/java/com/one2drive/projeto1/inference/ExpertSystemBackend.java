package com.one2drive.projeto1.inference;

import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.explanation.ExplanationTrace;

import java.util.List;

/**
 * A rule engine as the simulation sees it. One instance is one session: a reset of the
 * simulation closes it and creates another.
 *
 * <p>Per tick the engine calls, in order: {@link #advanceClock}, {@link #beginDecisionCycle},
 * {@link #insertEvent} for each sensor event, {@link #updateVehicleState} and {@link #decide}.
 * Decisions are proposals arbitrated by the same DecisionContext policy in every backend.
 */
public interface ExpertSystemBackend extends AutoCloseable {

    ExpertBackendType type();

    ExplanationTrace getTrace();

    /** The decision the rules fill in; the same instance for the whole session. */
    DrivingDecision getDrivingDecision();

    /** Keeps the session clock on simulation time (CEP windows, hold timers). */
    void advanceClock(long millis);

    void beginDecisionCycle(long timestampMillis, double currentSpeedKmh);

    /** A sensor event observed this tick. */
    void insertEvent(Object event);

    /** A driver command (lane change, acceleration) arriving between ticks. */
    void submitCommand(Object event);

    /** Refreshes what the rules know about the vehicle itself, before they fire. */
    void updateVehicleState(DecisionInput input);

    /**
     * Fires the rules and writes the arbitrated decision into {@link #getDrivingDecision()}.
     *
     * @throws ExpertSystemException if the engine failed and no decision was inferred
     */
    void decide();

    /** The legal limit the rules currently hold. */
    double currentSpeedLimitKmh();

    /** Working memory as one line per fact. */
    List<String> factSnapshot();

    List<WorkingMemoryFactView> factViews();

    /** The current decision as the engine itself reports it. */
    List<String> queryCurrentDecision();

    @Override
    void close();
}
