package com.one2drive.projeto1.prolog;

import java.util.List;

/**
 * What one Prolog decision cycle produced: the proposals, the working memory they were
 * inferred from (each fact numbered and, when it was concluded, with the rule that did it),
 * the legal limit and the speed radar state the Prolog state holds.
 */
public record PrologCycleResult(List<PrologProposal> proposals,
                                List<WorkingMemoryFact> workingMemory,
                                double speedLimitKmh,
                                RadarState radar) {

    /** The Prolog counterpart of SpeedRadarFact. */
    public record RadarState(String radarId, double distanceMeters, double speedLimitKmh,
                             boolean detected, boolean passed) {
        public static final RadarState NONE =
            new RadarState("none", Double.POSITIVE_INFINITY, 50.0, false, false);

        public boolean active() {
            return detected && !passed && !"none".equals(radarId);
        }
    }

    public record WorkingMemoryFact(int number, String fact, String rule) {
    }
}
