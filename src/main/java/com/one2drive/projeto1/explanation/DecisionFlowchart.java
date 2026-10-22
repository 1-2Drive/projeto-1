package com.one2drive.projeto1.explanation;

import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.domain.DrivingAction;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The questions of the decision flowchart (docs/fluxogramas/fluxogramas_apresentar/
 * 99-sistema-completo.md), one list per channel in the order the flowchart asks them, each
 * tied to the rules that answer it. The explanation walks these nodes over a recorded cycle:
 * a node took its "yes" branch when one of its rules proposed something, its "no" branch
 * otherwise. The rules are named as in Drools; Prolog names its per-action variants with a
 * suffix in parentheses, which {@link #baseRuleName} strips, so one map serves both engines.
 *
 * <p>This is a map of the knowledge base, not a copy of it: no condition is evaluated here.
 * Whether a node held always comes from the proposals and firings the engine recorded.
 */
public final class DecisionFlowchart {

    /** The recorded perception that gives a node its context in the explanation. */
    public enum Perception {
        NONE, RADAR, PEDESTRIAN, OBSTACLE, POTHOLE, SPEED_SIGN, WEATHER, BLIND_SPOTS, VEHICLE
    }

    /**
     * One question of the flowchart.
     *
     * @param id              the node id in flowchart 99 (V10, F13, ...)
     * @param docRef          the detailed flowchart (01-18) of the behaviour
     * @param rules           the rules whose proposal answers "yes"
     * @param supportingRules rules that only update state on the way (they propose nothing)
     * @param hazardRules     speed rules whose proposal shows the hazard of a lateral node is
     *                        there even when no rule of the node itself held
     * @param laneActions     lateral actions this node can lead to
     * @param canRaise        a longitudinal node whose rules can speed the vehicle up
     * @param canLower        a longitudinal node whose rules can slow the vehicle down
     */
    public record Node(String id, String docRef, DecisionChannel channel, String question,
                       String yesLabel, String noLabel, List<String> rules, List<String> supportingRules,
                       List<String> hazardRules, Set<DrivingAction> laneActions,
                       boolean canRaise, boolean canLower, Perception perception) {
    }

    /** Rule the arbitration names when no rule proposed a speed (DecisionContext). */
    public static final String DEFAULT_SPEED_DECISION = "Default base decision";
    /** Rule the arbitration names when no rule proposed a lane action (DecisionContext). */
    public static final String DEFAULT_LANE_DECISION = "Default lane decision";
    /** Rule the decision names when a mandatory stop vetoes a lane change (DecisionContext). */
    public static final String ARBITRATION_VETO = "Arbitration veto";
    /** Rule the decision names when the actuator refuses a lane change (DecisionActuator). */
    public static final String ACTUATOR_GUARD = "Actuator safety guard";
    /** The fallback rule of the speed channel (defaults.drl). */
    public static final String DEFAULT_SPEED_RULE = "Default maintain speed baseline";

    private static final Pattern VARIANT = Pattern.compile("\\s\\([^()]*\\)$");

    /**
     * The base of the speed flowchart: moving off, a driver request, the posted limit and the
     * cruise speed only ever build the speed up to a target, and the default keeps it.
     */
    private static final Set<String> NEVER_LOWER = Set.of("R04", "R03", "R03B", "R03C", "R00");

    private static final List<Node> LONGITUDINAL = List.of(
        speed("V10", "10", "Is the latest time-to-collision (TTC) reading below 1.3 s?",
            "Emergency brake", "TTC is not immediately critical", Perception.RADAR, false,
            List.of("Emergency brake when TTC is immediately critical"), List.of()),
        speed("V10B", "10", "Were 3 or more TTC readings below 2.5 s in the last 2 s?",
            "Emergency brake", "TTC is not persistently critical", Perception.RADAR, false,
            List.of("Emergency brake when persistent TTC is critical"), List.of()),
        speed("V08", "08", "Is a pedestrian in the vehicle's path or crossing the road?",
            "6 m or less, or comfortable braking no longer stops 6 m short: emergency brake; "
                + "6 to 32 m: stop and wait; beyond 32 m: slow down in time",
            "No pedestrian conflicts with the vehicle", Perception.PEDESTRIAN, false,
            List.of("Emergency stop for pedestrian extremely close",
                "Emergency brake for pedestrian when comfortable braking no longer stops in time",
                "Stop for pedestrian in crossing zone",
                "Brake for pedestrian proximity", "Hold for pedestrian still crossing"),
            List.of("Pedestrian conflicts with trajectory", "Start braking for pedestrian above the braking curve",
                "Hold speed for pedestrian once under the lower curve",
                "End the pedestrian approach once no pedestrian is approached")),
        speed("V13G", "13", "Is a high-severity obstacle 8 m or less ahead, in the lane the vehicle is physically in?",
            "Emergency brake", "No obstacle is critically close", Perception.OBSTACLE, false,
            List.of("Emergency braking for high severity obstacle at critical distance"), List.of()),
        speed("VS", "05/06", "Does a traffic light ahead govern the speed?",
            "Red: stop at the line (hard if comfortable braking is not enough); yellow: stop if it can, "
                + "otherwise proceed; green: release the hold",
            "No traffic light restricts the speed", Perception.NONE, true,
            List.of("Red traffic light progressive approach", "Hold at red traffic light",
                "Red traffic light requires full stop and hold", "Yellow traffic light: stop before the line",
                "Green traffic light releases red-light hold",
                "Green traffic light allows progression when no higher hazard"),
            List.of("Commit to stop or to proceed at traffic light")),
        speed("V07", "07", "Is there a STOP sign in the vehicle's lane?",
            "Slow down and stop at the line; proceed after a full stop of 1.2 s",
            "No STOP sign to obey", Perception.NONE, true,
            List.of("Stop sign requires progressive deceleration", "Stop sign requires full stop near line",
                "Proceed after stop sign condition satisfied"),
            List.of("Start stop timer once vehicle is stopped", "Stop sign condition satisfied after full stop hold",
                "Forget the stop when the stop sign is re-armed")),
        speed("V14", "14", "Is a closed barrier in the vehicle's path within comfortable stopping distance?",
            "Brake to stop 10 m before it until a lane change takes the vehicle out of its way; "
                + "with no safe side to go around it, stop (hard if comfortable braking is not enough)",
            "No closed barrier in the path to stop for", Perception.NONE, false,
            List.of("Stop before closed barrier while still in its lane", "Closed barrier requires stop"), List.of()),
        speed("V13", "13", "Is an obstacle in the lane within comfortable stopping distance, with the vehicle not yet moving out of its lane?",
            "Brake to stop 10 m before it", "No obstacle to stop for in the lane", Perception.OBSTACLE, false,
            List.of("Stop before obstacle ahead while still in its lane",
                "Stop before obstacle when lane change prohibited by continuous line"), List.of()),
        speed("L01", "01", "Is the speed above the limit in force?",
            "Slow down to the limit", "The speed is within the limit", Perception.SPEED_SIGN, false,
            List.of("Reduce speed when exceeding detected limit"), List.of()),
        speed("L01B", "01", "Does a lower limit ahead already require slowing down?",
            "Slow down gradually to reach it at the sign", "No lower limit ahead requires slowing yet",
            Perception.SPEED_SIGN, false, List.of("Slow down in time for the speed limit ahead"), List.of()),
        speed("L09", "09", "Is there a vehicle ahead in the same lane?",
            "Stop about 12 m behind it, slow below it, or match its speed", "No vehicle ahead to follow",
            Perception.RADAR, false,
            List.of("Brake when front vehicle distance low", "Keep safe following distance behind front vehicle",
                "Reduce speed when closing front vehicle", "Approaching trend from sliding distance window",
                "Follow the front vehicle speed outside the following gap",
                "Hold speed on an approaching trend below its target",
                "Fall back behind slow traffic to return right"), List.of()),
        speed("L11", "11", "Is a slower vehicle in a lane to the left, which must not be passed on its right?",
            "Slow down or hold so as not to pass it on the right", "No slower vehicle to the left",
            Perception.NONE, false,
            List.of("Do not pass a slower left lane vehicle on its right",
                "Do not pass a slower far left lane vehicle on its right"), List.of()),
        speed("L10", "10", "Is it raining, with 2 or more TTC readings below 3 s in the last 2 s?",
            "Brake to 15 km/h", "No persistent low TTC in the rain", Perception.WEATHER, false,
            List.of("Persistent low TTC in rain increases conservatism"), List.of()),
        speed("L12", "12", "Is a pothole in the vehicle's path close enough to need slowing down (at least 40 m), "
                + "with no safe side to go around it yet?",
            "Slow to 30 km/h by the pothole (20 km/h over the last 15 m; hard if it is severe and comfort no longer gets there)",
            "No pothole ahead to slow down for",
            Perception.POTHOLE, false, List.of("Pothole ahead reduce speed"), List.of()),
        speed("L14", "14", "Is an open barrier 35 m or less ahead?",
            "Pass it at up to 35 km/h", "No open barrier ahead", Perception.NONE, true,
            List.of("Open barrier allows continue if no higher priority"), List.of()),
        speed("L18", "18", "Does the weather restrict the speed (fuzzy logic on visibility and grip)?",
            "Keep below the speed the fuzzy controller recommends", "The weather does not restrict the speed",
            Perception.WEATHER, false, List.of("Defuzzify weather speed ceiling"),
            List.of("Fuzzify visibility", "Fuzzify grip", "Fuzzy: poor visibility or poor grip -> very slow",
                "Fuzzy: reduced visibility or reduced grip -> slow", "Fuzzy: fair visibility or fair grip -> moderate",
                "Fuzzy: rain with good visibility and good grip -> prudent",
                "Fuzzy: no rain with good visibility and good grip -> light")),
        speed("R04", "04", "Is the vehicle stopped (below 3 km/h) with no rule holding it?",
            "Move off gently up to 30 km/h", "Not moving off", Perception.VEHICLE, true,
            List.of("Move off when stopped and nothing holds the vehicle"),
            List.of("Start moving off after a standstill", "Stop moving off once held or at speed")),
        speed("R03", "03", "Is there a driver acceleration request less than 1.5 s old?",
            "Accelerate to the requested speed", "No acceleration request", Perception.NONE, true,
            List.of("Allow acceleration request when safe context"), List.of()),
        speed("R03B", "03", "Is a posted speed limit in force, with the vehicle within it?",
            "Keep to, or build up to, the posted limit", "No posted limit governs the speed",
            Perception.SPEED_SIGN, true, List.of("Maintain speed when compliant with detected limit"), List.of()),
        speed("R03C", "03", "With no sign in force, is the speed below the scenario cruise speed?",
            "Build back up to the cruise speed", "Already at the cruise speed, or a sign governs the road",
            Perception.VEHICLE, true,
            List.of("Build back up to the scenario cruise speed when no sign governs the road"), List.of()),
        speed("R00", "03", "Did no other rule propose a speed?",
            "Keep the current speed (default decision)", "Another rule proposed a speed", Perception.NONE, false,
            List.of(DEFAULT_SPEED_RULE), List.of())
    );

    private static final List<Node> LATERAL = List.of(
        lane("F16", "16", "Is a lane change already in progress?",
            "Finish the maneuver; never start another one midway", "No maneuver in progress", Perception.VEHICLE,
            EnumSet.of(DrivingAction.CHANGE_LANE_LEFT, DrivingAction.CHANGE_LANE_RIGHT),
            List.of("Maintain ongoing lane change left avoidance", "Maintain ongoing lane change right avoidance"),
            List.of(), List.of()),
        lane("F13", "13", "Is an obstacle in the lane 50 m or less ahead, with a safe side to go around it?",
            "Go around it (from the centre, preferably by the left)", "No obstacle to go around",
            Perception.OBSTACLE, EnumSet.of(DrivingAction.CHANGE_LANE_LEFT, DrivingAction.CHANGE_LANE_RIGHT),
            List.of("Avoid obstacle in centre lane by changing left",
                "Avoid obstacle in centre lane by changing right when left is occupied",
                "Avoid obstacle in left lane by changing right", "Avoid obstacle in right lane by changing left"),
            List.of(),
            List.of("Stop before obstacle ahead while still in its lane",
                "Stop before obstacle when lane change prohibited by continuous line",
                "Emergency braking for high severity obstacle at critical distance")),
        lane("F14", "14", "Is a closed barrier in the lane 55 m or less ahead, with a safe side to go around it?",
            "Go around it (from the centre, preferably by the left)", "No closed barrier to go around",
            Perception.NONE, EnumSet.of(DrivingAction.CHANGE_LANE_LEFT, DrivingAction.CHANGE_LANE_RIGHT),
            List.of("Avoid closed barrier by changing left when safe",
                "Avoid closed barrier by changing left from centre when safe",
                "Avoid closed barrier by changing right from centre when safe",
                "Avoid closed barrier by changing right when safe"),
            List.of(), List.of("Stop before closed barrier while still in its lane", "Closed barrier requires stop")),
        lane("F12", "12", "Is a pothole in the lane 50 m or less ahead, with a safe side to go around it?",
            "Go around it (from the centre, preferably by the right)", "No pothole to go around",
            Perception.POTHOLE, EnumSet.of(DrivingAction.CHANGE_LANE_LEFT, DrivingAction.CHANGE_LANE_RIGHT),
            List.of("Avoid pothole in centre lane by changing right when right is clear",
                "Avoid pothole in centre lane by changing left when right is occupied",
                "Avoid pothole in left lane by changing right", "Avoid pothole in right lane by changing left"),
            List.of(), List.of("Pothole ahead reduce speed")),
        lane("F11", "11", "Is a slower vehicle ahead in the same lane that can be overtaken on the left?",
            "Overtake on the left (never on the right)", "No overtake", Perception.RADAR,
            EnumSet.of(DrivingAction.CHANGE_LANE_LEFT),
            List.of("Overtake slow front vehicle via left lane", "Overtake the vehicle being followed",
                "Move left behind slower left lane vehicle to overtake it on the left",
                "Go around stationary front vehicle from standstill"),
            List.of("Cancel overtaking that never left its original lane"),
            List.of("Brake when front vehicle distance low", "Keep safe following distance behind front vehicle",
                "Reduce speed when closing front vehicle", "Approaching trend from sliding distance window",
                "Follow the front vehicle speed outside the following gap",
                "Hold speed on an approaching trend below its target")),
        lane("F11B", "11", "Is the vehicle boxed in behind slow traffic in every lane ahead?",
            "Give up the overtake: return right, or keep the right lane", "Not boxed in", Perception.RADAR,
            EnumSet.of(DrivingAction.RETURN_RIGHT, DrivingAction.KEEP_LANE),
            List.of("Return right when boxed in and the right lane is clear",
                "Keep the right lane while the lanes ahead stay blocked"),
            List.of("Note the vehicle is boxed in behind slow traffic", "Forget boxed in once nothing is ahead",
                "Forget boxed in once the left lane opens"), List.of()),
        lane("F15", "15", "Is there an external lane-change request (driver or scenario) less than 1.5 s old?",
            "Change to the requested side if it is safe; otherwise refuse and explain", "No lane-change request",
            Perception.BLIND_SPOTS,
            EnumSet.of(DrivingAction.CHANGE_LANE_LEFT, DrivingAction.CHANGE_LANE_RIGHT, DrivingAction.KEEP_LANE),
            List.of("Allow lane change left when lane safe", "Allow lane change right when lane safe",
                "Block lane change when target lane occupied", "Block lane change right when target lane occupied"),
            List.of(), List.of()),
        lane("F17", "17", "Is the vehicle out of the right lane with no reason to stay there (keep-right rule)?",
            "Return to the right, one lane at a time", "Already in the right lane, or a reason to stay",
            Perception.BLIND_SPOTS, EnumSet.of(DrivingAction.RETURN_RIGHT),
            List.of("Enforce keep right road law when right lane is safe",
                "Return to right lane after overtaking slow vehicle"),
            List.of("Mark front vehicle passed once it is behind or cleared",
                "Deactivate overtaking state once return right completes"), List.of())
    );

    private DecisionFlowchart() {
    }

    public static List<Node> nodes(DecisionChannel channel) {
        return channel == DecisionChannel.LONGITUDINAL ? LONGITUDINAL : LATERAL;
    }

    /** The node whose question the rule answers, by the rule's proposal. */
    public static Optional<Node> nodeFor(String rule) {
        String base = baseRuleName(rule);
        return Stream.concat(LONGITUDINAL.stream(), LATERAL.stream())
            .filter(node -> node.rules().contains(base))
            .findFirst();
    }

    /** The rule's proposal answers the node's question. */
    public static boolean answers(Node node, String rule) {
        return node.rules().contains(baseRuleName(rule));
    }

    /** The rule updates the state the node's rules read, without proposing anything. */
    public static boolean supports(Node node, String rule) {
        return node.supportingRules().contains(baseRuleName(rule));
    }

    /** The rule's proposal shows that the hazard of a lateral node is there. */
    public static boolean showsHazard(Node node, String rule) {
        return node.hazardRules().contains(baseRuleName(rule));
    }

    /** "Brake for pedestrian proximity (brake)", a Prolog variant, is the Drools rule "Brake for pedestrian proximity". */
    public static String baseRuleName(String rule) {
        return rule == null ? "" : VARIANT.matcher(rule.trim()).replaceFirst("");
    }

    private static Node speed(String id, String docRef, String question, String yes, String no,
                              Perception perception, boolean canRaise, List<String> rules, List<String> supporting) {
        return new Node(id, docRef, DecisionChannel.LONGITUDINAL, question, yes, no, rules, supporting, List.of(),
            Set.of(), canRaise, !NEVER_LOWER.contains(id), perception);
    }

    private static Node lane(String id, String docRef, String question, String yes, String no, Perception perception,
                             Set<DrivingAction> actions, List<String> rules, List<String> supporting,
                             List<String> hazardRules) {
        return new Node(id, docRef, DecisionChannel.LATERAL, question, yes, no, rules, supporting, hazardRules,
            Set.copyOf(actions), false, false, perception);
    }
}
