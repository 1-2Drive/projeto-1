package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.decision.LaneChangeCheck;

import java.util.List;

/**
 * What one Prolog decision cycle produced: the proposals, the working memory they were
 * inferred from (each fact numbered and, when it was concluded, with the rule that did it),
 * the legal limit the Prolog state holds, and the answer to "why not change lane?" for
 * each side.
 */
public record PrologCycleResult(List<PrologProposal> proposals,
                                List<WorkingMemoryFact> workingMemory,
                                double speedLimitKmh,
                                List<LaneChangeCheck> laneChecks) {

    public PrologCycleResult(List<PrologProposal> proposals, List<WorkingMemoryFact> workingMemory,
                             double speedLimitKmh) {
        this(proposals, workingMemory, speedLimitKmh, List.of());
    }

    public record WorkingMemoryFact(int number, String fact, String rule) {
    }
}
