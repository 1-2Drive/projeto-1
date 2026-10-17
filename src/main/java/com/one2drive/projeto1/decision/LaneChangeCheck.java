package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.domain.Lane;

import java.util.ArrayList;
import java.util.List;

/**
 * "Why not change lane?": the sub-goals of the safeToChangeLane query (lane-safety.drl,
 * safe_to_change_lane/1 in Prolog) asked of the engine for one side after the rules fired.
 * It answers for a lane change no rule proposed as well, by naming the sub-goal that fails.
 *
 * @param side             {@link Lane#LEFT} or {@link Lane#RIGHT}, the side of the ego
 * @param egoLane          the lane the ego is in, to name the line and the lane on that side
 * @param maneuverIdle     no lane change in progress
 * @param noContinuousLine a lane on that side, and no continuous line to cross into it
 * @param sideClear        the blind-spot sensor reports that side clear
 * @param roomToFollow     the front radar reports no vehicle ahead in that lane, or one far
 *                         enough and slow enough to be followed after the lane change
 */
public record LaneChangeCheck(Lane side, Lane egoLane, boolean maneuverIdle, boolean noContinuousLine,
                              boolean sideClear, boolean roomToFollow) {

    public static final String MANEUVER_IDLE = "maneuverIdle";
    public static final String NO_CONTINUOUS_LINE = "noContinuousLine";
    public static final String SIDE_CLEAR = "sideClear";
    public static final String ROOM_TO_FOLLOW = "roomToFollow";

    /** One sub-goal, whether it holds, and in words what it means for this side. */
    public record SubGoal(String goal, boolean holds, String detail) {
    }

    /** safeToChangeLane itself: every sub-goal holds. */
    public boolean allowed() {
        return maneuverIdle && noContinuousLine && sideClear && roomToFollow;
    }

    public List<SubGoal> subGoals() {
        String direction = side.name().toLowerCase();
        Lane target = side == Lane.LEFT ? egoLane.left() : egoLane.right();
        String line;
        if (target == egoLane) {
            line = noContinuousLine ? "lane to the " + direction : "no lane to the " + direction;
        } else {
            line = noContinuousLine
                ? "no continuous line between " + egoLane + " and " + target
                : "continuous line between " + egoLane + " and " + target;
        }
        return List.of(
            new SubGoal(MANEUVER_IDLE, maneuverIdle,
                maneuverIdle ? "no lane change in progress" : "a lane change is already in progress"),
            new SubGoal(NO_CONTINUOUS_LINE, noContinuousLine, line),
            new SubGoal(SIDE_CLEAR, sideClear,
                sideClear ? "blind-spot sensor reports the " + direction + " side clear"
                          : "blind-spot sensor reports the " + direction + " side occupied"),
            new SubGoal(ROOM_TO_FOLLOW, roomToFollow,
                roomToFollow ? "room to follow the vehicle ahead in the " + target + " lane"
                             : "vehicle ahead in the " + target + " lane too close to follow safely"));
    }

    /**
     * The answer in one line: allowed, or every sub-goal that fails. The blind-spot sensor
     * also reports a side occupied when there is no lane there or a continuous line, so with
     * the line failing the occupied side would only repeat it and is left out.
     */
    public String explanation() {
        if (allowed()) {
            return "safe to change " + side.name().toLowerCase();
        }
        List<String> failing = new ArrayList<>();
        for (SubGoal subGoal : subGoals()) {
            if (!subGoal.holds() && !(SIDE_CLEAR.equals(subGoal.goal()) && !noContinuousLine)) {
                failing.add(subGoal.detail());
            }
        }
        return String.join("; ", failing);
    }
}
