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
    public record DecisionSnapshot(String action, String speedAction, String laneAction,
                                   String reason, String sourceRule, String speedSourceRule,
                                   String laneSourceRule, double recommendedSpeedKmh,
                                   List<String> evidence, List<ProposalSnapshot> proposals,
                                   List<LaneCheckSnapshot> laneChecks, String actuatorResult) {
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
                    conditions(p.getSourceRule(), decision))).toList(),
                decision.getLaneChecks().stream().map(c -> new LaneCheckSnapshot(
                    c.side().name(), c.allowed(), c.explanation(),
                    c.subGoals().stream().map(g -> new SubGoalSnapshot(g.goal(), g.holds(), g.detail())).toList())).toList(),
                actuatorResult);
        }

        public DecisionSnapshot withActuatorResult(String result) {
            return new DecisionSnapshot(action, speedAction, laneAction, reason, sourceRule, speedSourceRule,
                laneSourceRule, recommendedSpeedKmh, evidence, proposals, laneChecks, result);
        }

        private static String conditions(String rule, DrivingDecision decision) {
            if (rule != null && rule.toLowerCase().contains("pothole")) {
                String facts = decision.getEvidence().stream().filter(e -> e.toLowerCase().contains("pothole"))
                    .findFirst().orElse("pothole conditions were satisfied as recorded by the fired rule");
                return facts + "; lane eligibility from the rule engine's recorded lane-safety checks";
            }
            if (rule != null && rule.toLowerCase().contains("obstacle")) {
                return decision.getEvidence().stream().findFirst().orElse("obstacle conditions were satisfied as recorded by the fired rule");
            }
            return "Antecedents are not exposed for this rule; use the stored working-memory facts and fired-rule trace.";
        }
    }

    public record ProposalSnapshot(String rule, String action, String channel, int priority,
                                   Double recommendedSpeedKmh, String outcome, String detail,
                                   String reason, List<String> evidence, String conditions) { }

    public record LaneCheckSnapshot(String side, boolean allowed, String explanation,
                                    List<SubGoalSnapshot> subGoals) { }

    public record SubGoalSnapshot(String goal, boolean holds, String detail) { }
}
