package com.one2drive.projeto1.api.model;

import java.util.List;

/**
 * The explanation of one recorded decision, per channel: {@code why} explains the speed
 * (longitudinal) and the lane (lateral) decision, {@code whyNot} each alternative that was not
 * taken, and {@code how} the path through the decision flowchart. Everything is read from the
 * saved cycle: proposals and their arbitration outcomes, lane-safety sub-goals, rule firings
 * and perception.
 */
public record ApiWhyAnswer(String sessionId, long timestampMillis, String engine, Situation situation,
                           List<ChannelWhy> why, List<Alternative> whyNot, List<ChannelFlow> how) {

    /** What the vehicle was doing when the rules decided. */
    public record Situation(double speedKmh, String lane, String laneChangeState, double recommendedSpeedKmh,
                            String actuatorResult) { }

    /** One rule's proposal in the cycle and what the arbitration did with it. */
    public record RuleRef(String rule, String action, String channel, Double recommendedSpeedKmh, int priority,
                          String rung, String outcome, String detail, String reason, List<String> evidence) { }

    /** A condition the explanation rests on; {@code holds} is null when the cycle does not record it. */
    public record Condition(String text, Boolean holds) { }

    /** Why one channel decided what it decided. {@code relevant} is false for an untouched default. */
    public record ChannelWhy(String channel, String title, String action, String effect, boolean relevant,
                             String summary, RuleRef decidingRule, List<String> conditions, String arbitration,
                             List<RuleRef> alsoConsidered) { }

    public enum AlternativeStatus {
        /** Nothing proposed it, or what it needs did not hold. */
        CONDITION_NOT_MET,
        /** A rule proposed it, but another one prevailed in the arbitration. */
        OVERRIDDEN_BY_PRIORITY,
        /** It makes no sense in this state (no lane on that side, already stopped). */
        NOT_APPLICABLE,
        /** The recorded cycle does not hold enough to explain it. */
        INSUFFICIENT_DATA
    }

    /** Why an alternative of one channel was not the decision. */
    public record Alternative(String channel, String option, String label, AlternativeStatus status, String summary,
                              List<Condition> requiredConditions, List<RuleRef> blockingRules, String prevailing) { }

    /** The path of one channel through the decision flowchart. */
    public record ChannelFlow(String channel, String title, String decision, List<FlowStep> steps, String outcome) { }

    public enum Branch { YES, NO, NOT_REACHED }

    /**
     * One question of the flowchart and the branch the cycle took. {@code takenLabel} says what
     * that branch led to; {@code decisive} marks the step whose proposal set the decision.
     */
    public record FlowStep(String nodeId, String docRef, String question, Branch branch, String yesLabel,
                           String noLabel, String takenLabel, boolean decisive, List<RuleRef> rules,
                           List<String> supportingRules, List<Condition> conditions, List<String> observed) { }
}
