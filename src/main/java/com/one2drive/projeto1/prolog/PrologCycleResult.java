package com.one2drive.projeto1.prolog;

import java.util.List;

/**
 * What one Prolog decision cycle produced: the proposals, the working memory they were
 * inferred from (each fact numbered and, when it was concluded, with the rule that did it),
 * and the legal limit the Prolog state holds.
 */
public record PrologCycleResult(List<PrologProposal> proposals,
                                List<WorkingMemoryFact> workingMemory,
                                double speedLimitKmh) {

    public record WorkingMemoryFact(int number, String fact, String rule) {
    }
}
