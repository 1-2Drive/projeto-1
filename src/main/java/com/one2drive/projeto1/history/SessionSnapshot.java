package com.one2drive.projeto1.history;

import com.one2drive.projeto1.api.model.ApiRoadElement;
import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiTimelineEntry;
import com.one2drive.projeto1.api.model.ApiWorkingMemoryRow;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.simulation.RoadMapSnapshot;

import java.util.List;

/** A point-in-time, read-only copy of the evidence used for historical explanations. */
public record SessionSnapshot(long simulationTimeMillis,
                              ApiSimulationSnapshot.EgoVehicleView vehicle,
                              ApiSimulationSnapshot.EnvironmentView environment,
                              ApiSimulationSnapshot.SensorTelemetryView sensors,
                              List<ApiRoadElement> worldObjects,
                              List<ApiWorkingMemoryRow> workingMemory,
                              List<ApiTimelineEntry> timeline,
                              DecisionSnapshot decision,
                              RoadMapSnapshot roadMap) {
    /**
     * The decision of one cycle as the actuator left it: the headline fields, and each channel
     * on its own (reason, priority, evidence), so the explanation can answer for the speed and
     * the lane separately. Sessions saved before the per-channel fields existed load them as
     * null; they are normalised to empty values and the explanation falls back to proposals.
     */
    public record DecisionSnapshot(String action, String speedAction, String laneAction,
                                   String reason, String sourceRule, String speedSourceRule,
                                   String laneSourceRule, double recommendedSpeedKmh,
                                   List<String> evidence, List<ProposalSnapshot> proposals,
                                   List<LaneCheckSnapshot> laneChecks, String actuatorResult,
                                   String speedReason, String laneReason,
                                   Integer speedPriority, Integer lanePriority,
                                   List<String> speedEvidence, List<String> laneEvidence) {
        public DecisionSnapshot {
            evidence = evidence == null ? List.of() : evidence;
            proposals = proposals == null ? List.of() : proposals;
            laneChecks = laneChecks == null ? List.of() : laneChecks;
            speedEvidence = speedEvidence == null ? List.of() : speedEvidence;
            laneEvidence = laneEvidence == null ? List.of() : laneEvidence;
        }

        public static DecisionSnapshot from(DrivingDecision decision, String actuatorResult) {
            return new DecisionSnapshot(
                decision.getAction().name(), decision.getSpeedAction().name(), decision.getLaneAction().name(),
                decision.getReason(), decision.getSourceRule(), decision.getSpeedSourceRule(),
                decision.getLaneSourceRule(), decision.getRecommendedSpeedKmh(),
                List.copyOf(decision.getEvidence()),
                decision.getProposals().stream().map(p -> new ProposalSnapshot(
                    p.getSourceRule(), p.getAction().name(), p.getChannel().name(), p.getPriority(),
                    Double.isNaN(p.getRecommendedSpeedKmh()) ? null : p.getRecommendedSpeedKmh(),
                    p.getOutcome().name(), p.getDetail(), p.getReason(), p.getEvidence(),
                    recordedFacts(p.getEvidence()))).toList(),
                decision.getLaneChecks().stream().map(c -> new LaneCheckSnapshot(
                    c.side().name(), c.allowed(), c.explanation(),
                    c.subGoals().stream().map(g -> new SubGoalSnapshot(g.goal(), g.holds(), g.detail())).toList())).toList(),
                actuatorResult,
                decision.getSpeedReason(), decision.getLaneReason(),
                decision.getPriority(), decision.getLanePriority(),
                List.copyOf(decision.getSpeedEvidence()), List.copyOf(decision.getLaneEvidence()));
        }
    }

    /** The facts the rule itself recorded as its evidence; empty when it recorded none. */
    private static String recordedFacts(List<String> evidence) {
        return evidence == null ? "" : String.join("; ", evidence);
    }

    public record ProposalSnapshot(String rule, String action, String channel, int priority,
                                   Double recommendedSpeedKmh, String outcome, String detail,
                                   String reason, List<String> evidence, String conditions) {
        public ProposalSnapshot {
            evidence = evidence == null ? List.of() : evidence;
        }
    }

    public record LaneCheckSnapshot(String side, boolean allowed, String explanation,
                                    List<SubGoalSnapshot> subGoals) {
        public LaneCheckSnapshot {
            subGoals = subGoals == null ? List.of() : subGoals;
        }
    }

    public record SubGoalSnapshot(String goal, boolean holds, String detail) { }
}
