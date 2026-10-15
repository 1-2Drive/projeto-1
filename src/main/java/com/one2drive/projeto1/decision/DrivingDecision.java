package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The decision of one cycle: a speed decision (longitudinal channel) and a lane decision
 * (lateral channel), plus every proposal the rules made and what became of it.
 *
 * <p>{@link #getAction()} is the headline the UI and the timeline show: the lane maneuver
 * while one executes, the speed action otherwise. The actuator reads the two channels
 * explicitly through {@link #getSpeedAction()} and {@link #getLaneAction()}.
 */
public class DrivingDecision {
    private DrivingAction speedAction;
    private double recommendedSpeedKmh;
    private RiskLevel riskLevel;
    private int priority;
    private String speedReason;
    private String speedSourceRule;
    private long timestamp;
    private final List<String> speedEvidence;

    private DrivingAction laneAction;
    private int lanePriority;
    private String laneReason;
    private String laneSourceRule;
    private final List<String> laneEvidence;

    private final List<DecisionProposal> proposals;

    public DrivingDecision() {
        this.speedEvidence = new ArrayList<>();
        this.laneEvidence = new ArrayList<>();
        this.proposals = new ArrayList<>();
        reset(0L);
    }

    public void reset(long timestamp) {
        this.speedAction = DrivingAction.CONTINUE;
        this.recommendedSpeedKmh = 0.0;
        this.riskLevel = RiskLevel.LOW;
        this.priority = 0;
        this.speedReason = "No restrictive condition detected";
        this.speedSourceRule = "Default continue decision";
        this.timestamp = timestamp;
        this.speedEvidence.clear();
        this.laneAction = DrivingAction.KEEP_LANE;
        this.lanePriority = 0;
        this.laneReason = "No lane rule holds";
        this.laneSourceRule = "Default lane decision";
        this.laneEvidence.clear();
        this.proposals.clear();
    }

    /**
     * Sets the decision of the action's channel. A lateral action sets the lane decision
     * and leaves the speed decision alone, so callers that build a decision by hand keep
     * working whichever channel they set.
     */
    public void apply(DrivingAction action,
                      double recommendedSpeedKmh,
                      RiskLevel riskLevel,
                      int priority,
                      String reason,
                      String sourceRule,
                      long timestamp,
                      List<String> evidenceLines) {
        this.timestamp = timestamp;
        if (action.channel() == DecisionChannel.LATERAL) {
            applyLane(action, priority, reason, sourceRule, evidenceLines);
            return;
        }
        this.speedAction = action;
        this.recommendedSpeedKmh = recommendedSpeedKmh;
        this.riskLevel = riskLevel;
        this.priority = priority;
        this.speedReason = reason;
        this.speedSourceRule = sourceRule;
        this.speedEvidence.clear();
        if (evidenceLines != null) {
            this.speedEvidence.addAll(evidenceLines);
        }
    }

    public void applyLane(DrivingAction action, int priority, String reason, String sourceRule, List<String> evidenceLines) {
        this.laneAction = action;
        this.lanePriority = priority;
        this.laneReason = reason;
        this.laneSourceRule = sourceRule;
        this.laneEvidence.clear();
        if (evidenceLines != null) {
            this.laneEvidence.addAll(evidenceLines);
        }
    }

    void setProposals(List<DecisionProposal> cycleProposals) {
        this.proposals.clear();
        this.proposals.addAll(cycleProposals);
    }

    /**
     * The actuator could not execute the lane maneuver: the lane decision becomes
     * KEEP_LANE and the winning lane proposal is recorded as rejected, with the reason.
     */
    public void rejectLane(String detail) {
        for (DecisionProposal proposal : proposals) {
            if (proposal.getChannel() == DecisionChannel.LATERAL
                && proposal.getOutcome() == DecisionProposal.Outcome.WON) {
                proposal.settle(DecisionProposal.Outcome.REJECTED_BY, "actuator: " + detail);
            }
        }
        String rule = laneSourceRule;
        applyLane(DrivingAction.KEEP_LANE, lanePriority, "Lane change rejected by the actuator: " + detail,
            "Actuator safety guard", List.of(rule + " proposed " + laneAction + ", " + detail));
    }

    /** The headline action: the lane maneuver while one executes, the speed action otherwise. */
    public DrivingAction getAction() {
        return laneAction.isLaneManeuver() ? laneAction : speedAction;
    }

    public DrivingAction getSpeedAction() {
        return speedAction;
    }

    public DrivingAction getLaneAction() {
        return laneAction;
    }

    public boolean isLaneManeuver() {
        return laneAction.isLaneManeuver();
    }

    public double getRecommendedSpeedKmh() {
        return recommendedSpeedKmh;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    /** Priority of the speed decision: how strongly the speed is constrained this cycle. */
    public int getPriority() {
        return priority;
    }

    public int getLanePriority() {
        return lanePriority;
    }

    /** Reason of the headline action. */
    public String getReason() {
        return laneAction.isLaneManeuver() ? laneReason : speedReason;
    }

    /** Rule behind the headline action. */
    public String getSourceRule() {
        return laneAction.isLaneManeuver() ? laneSourceRule : speedSourceRule;
    }

    public String getSpeedReason() {
        return speedReason;
    }

    public String getSpeedSourceRule() {
        return speedSourceRule;
    }

    public String getLaneReason() {
        return laneReason;
    }

    public String getLaneSourceRule() {
        return laneSourceRule;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /** Evidence of the headline action. */
    public List<String> getEvidence() {
        return Collections.unmodifiableList(laneAction.isLaneManeuver() ? laneEvidence : speedEvidence);
    }

    public List<String> getSpeedEvidence() {
        return Collections.unmodifiableList(speedEvidence);
    }

    public List<String> getLaneEvidence() {
        return Collections.unmodifiableList(laneEvidence);
    }

    /** Every proposal of the cycle with its outcome, in firing order. */
    public List<DecisionProposal> getProposals() {
        return Collections.unmodifiableList(proposals);
    }

    @Override
    public String toString() {
        return "DrivingDecision{" +
            "speed=" + speedAction +
            " " + recommendedSpeedKmh + " km/h" +
            ", riskLevel=" + riskLevel +
            ", priority=" + priority +
            ", rule='" + speedSourceRule + '\'' +
            ", lane=" + laneAction +
            ", laneRule='" + laneSourceRule + '\'' +
            ", timestamp=" + timestamp +
            '}';
    }
}
