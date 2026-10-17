package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.decision.LaneChangeCheck;

import java.util.List;

/**
 * What one Prolog decision cycle produced: the proposals, the working memory they were
 * inferred from (each fact numbered and, when it was concluded, with the rule that did it),
 * the legal limit the Prolog state holds, the answer to "why not change lane?" for each
 * side, and what the cycle cost the engine.
 */
public record PrologCycleResult(List<PrologProposal> proposals,
                                List<WorkingMemoryFact> workingMemory,
                                double speedLimitKmh,
                                List<LaneChangeCheck> laneChecks,
                                EngineStats engineStats) {

    public PrologCycleResult(List<PrologProposal> proposals, List<WorkingMemoryFact> workingMemory,
                             double speedLimitKmh) {
        this(proposals, workingMemory, speedLimitKmh, List.of(), EngineStats.NONE);
    }

    public record WorkingMemoryFact(int number, String fact, String rule) {
    }

    /**
     * The cost of one run of the rules: how many times a rule was matched against the
     * working memory, how many rules fired, and the time it took inside Prolog.
     */
    public record EngineStats(int rematches, int firings, double ruleMillis) {
        public static final EngineStats NONE = new EngineStats(0, 0, 0.0);
    }
}
