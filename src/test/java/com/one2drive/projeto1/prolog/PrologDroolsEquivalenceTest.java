package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.PedestrianCrossingState;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.drools.DroolsTestHarness;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.drools.events.AccelerationRequestEvent;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.LaneChangeRequestEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.ReducedGripEvent;
import com.one2drive.projeto1.drools.events.ReducedVisibilityEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.LeftLaneVehicleEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same single cycle given to both backends must end in the same decision: same action,
 * same recommended speed and, where the Prolog rule is a one-to-one copy, the same rule.
 * Prolog rule ids that split a Drools rule by outcome add a suffix in parentheses.
 */
class PrologDroolsEquivalenceTest {

    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("no restriction", 60.0, new Object[]{}),
            Arguments.of("exceeding a sign", 90.0, new Object[]{
                new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50")}),
            Arguments.of("below a sign", 60.0, new Object[]{
                new SpeedLimitDetectedEvent(1_000, "camera", 90, 80, "sign-90")}),
            Arguments.of("red light far", 50.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 120, "light-1")}),
            Arguments.of("red light, braking distance", 50.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 55, "light-1")}),
            Arguments.of("red light, anticipation distance", 50.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 70, "light-1")}),
            Arguments.of("red light at the line", 60.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 18, "light-1")}),
            Arguments.of("red light, stopped", 0.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 3, "light-1")}),
            Arguments.of("yellow, room to stop", 50.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 60, "light-1")}),
            Arguments.of("yellow, too close", 50.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 30, "light-1")}),
            Arguments.of("green, moving", 40.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.GREEN, 30, "light-1")}),
            Arguments.of("green, stopped", 0.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.GREEN, 20, "light-1")}),
            Arguments.of("pedestrian in path, near", 50.0, new Object[]{
                new PedestrianDetectedEvent(1_000, "camera", 18.0, "ped-1", true)}),
            Arguments.of("pedestrian in path, far and fast", 70.0, new Object[]{
                new PedestrianDetectedEvent(1_000, "camera", 60.0, "ped-1", true)}),
            Arguments.of("pedestrian crossing, hold band", 40.0, new Object[]{
                new PedestrianDetectedEvent(1_000, "camera", 50.0, "ped-1", false, PedestrianCrossingState.CROSSING)}),
            Arguments.of("pedestrian crossing, stopped", 0.0, new Object[]{
                new PedestrianDetectedEvent(1_000, "camera", 40.0, "ped-1", false, PedestrianCrossingState.CROSSING)}),
            Arguments.of("pedestrian very close", 30.0, new Object[]{
                new PedestrianDetectedEvent(1_000, "camera", 5.0, "ped-1", true)}),
            Arguments.of("pedestrian waiting aside", 50.0, new Object[]{
                new PedestrianDetectedEvent(1_000, "camera", 15.0, "ped-1", false, PedestrianCrossingState.WAITING)}),
            Arguments.of("persistent TTC", 90.0, new Object[]{
                new TTCEvent(700, "radar", 2.3, "front-1"),
                new TTCEvent(850, "radar", 2.1, "front-1"),
                new TTCEvent(980, "radar", 1.9, "front-1")}),
            Arguments.of("immediate TTC", 90.0, new Object[]{
                new TTCEvent(1_000, "radar", 1.0, "front-1")}),
            Arguments.of("stop sign, below the curve", 30.0, new Object[]{
                new StopSignEvent(1_000, "camera", 80, "stop-1")}),
            Arguments.of("stop sign, hold speed", 40.0, new Object[]{
                new StopSignEvent(1_000, "camera", 30, "stop-1")}),
            Arguments.of("stop sign, braking", 50.0, new Object[]{
                new StopSignEvent(1_000, "camera", 35, "stop-1")}),
            Arguments.of("stop sign, too close", 50.0, new Object[]{
                new StopSignEvent(1_000, "camera", 25, "stop-1")}),
            Arguments.of("stop sign, at the line", 4.0, new Object[]{
                new StopSignEvent(1_000, "camera", 3, "stop-1")}),
            Arguments.of("front vehicle too close", 50.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 10.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 3.0, "front-1")}),
            Arguments.of("closing on a slower front vehicle", 60.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 30.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1")}),
            Arguments.of("front vehicle far enough", 60.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 80.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 1.0, "front-1")}),
            Arguments.of("stationary front vehicle 40 m ahead", 50.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 50.0 / 3.6, "front-1")}),
            Arguments.of("stopped behind a stationary vehicle", 0.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 13.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1")}),
            Arguments.of("front vehicle pulling away", 50.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 20.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", -2.0, "front-1")}),
            // Overtaking. Both harnesses keep the ego in CENTER between dashed markings.
            Arguments.of("overtake a slower front vehicle, left lane clear", 60.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)}),
            Arguments.of("slower front vehicle, left lane never reported clear", 60.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1")}),
            Arguments.of("no overtaking with a pedestrian in the path", 60.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY),
                new PedestrianDetectedEvent(1_000, "camera", 60.0, "ped-1", true)}),
            Arguments.of("go around a stationary vehicle from standstill", 0.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 12.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)}),
            Arguments.of("red light ahead forbids going around", 0.0, new Object[]{
                new FrontDistanceEvent(1_000, "front-radar", 12.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY),
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 30.0, "light-1")}),
            Arguments.of("slower left lane vehicle: closing in", 60.0, new Object[]{
                new LeftLaneVehicleEvent(1_000, "front-radar", 50.0, 6.0, "left-1")}),
            Arguments.of("slower left lane vehicle: hold behind it", 30.0, new Object[]{
                new LeftLaneVehicleEvent(1_000, "front-radar", 36.0, -0.5, "left-1")}),
            Arguments.of("slower left lane vehicle: fall back", 30.0, new Object[]{
                new LeftLaneVehicleEvent(1_000, "front-radar", 30.0, -0.2, "left-1")}),
            // Obstacles, potholes, barriers. Ego in CENTER; a side lane counts as occupied
            // until the blind spot reports it clear.
            Arguments.of("pothole ahead, right lane clear", 60.0, new Object[]{
                new PotholeDetectedEvent(1_000, "camera", 30.0, "pothole-1", HazardSeverity.MEDIUM, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY)}),
            Arguments.of("pothole ahead, only left lane clear", 60.0, new Object[]{
                new PotholeDetectedEvent(1_000, "camera", 30.0, "pothole-1", HazardSeverity.MEDIUM, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)}),
            Arguments.of("pothole close, no lane free", 50.0, new Object[]{
                new PotholeDetectedEvent(1_000, "camera", 12.0, "pothole-1", HazardSeverity.MEDIUM, Lane.CENTER)}),
            Arguments.of("pothole farther, no lane free", 50.0, new Object[]{
                new PotholeDetectedEvent(1_000, "camera", 30.0, "pothole-1", HazardSeverity.MEDIUM, Lane.CENTER)}),
            Arguments.of("obstacle ahead, left lane clear", 60.0, new Object[]{
                new ObstacleDetectedEvent(1_000, "camera", 30.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)}),
            Arguments.of("obstacle ahead, only right lane clear", 60.0, new Object[]{
                new ObstacleDetectedEvent(1_000, "camera", 30.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY)}),
            Arguments.of("obstacle, no lane free, room to brake", 30.0, new Object[]{
                new ObstacleDetectedEvent(1_000, "camera", 40.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.CENTER)}),
            Arguments.of("obstacle, no lane free, too fast", 50.0, new Object[]{
                new ObstacleDetectedEvent(1_000, "camera", 40.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.CENTER)}),
            Arguments.of("obstacle, no lane free, at the stopping point", 5.0, new Object[]{
                new ObstacleDetectedEvent(1_000, "camera", 10.3, "obstacle-1", HazardSeverity.MEDIUM, Lane.CENTER)}),
            Arguments.of("high severity obstacle critically close", 20.0, new Object[]{
                new ObstacleDetectedEvent(1_000, "camera", 6.0, "obstacle-1", HazardSeverity.HIGH, Lane.CENTER)}),
            Arguments.of("obstacle in another lane", 50.0, new Object[]{
                new ObstacleDetectedEvent(1_000, "camera", 30.0, "obstacle-1", HazardSeverity.HIGH, Lane.LEFT)}),
            Arguments.of("closed barrier, no adjacent lane safe", 40.0, new Object[]{
                new BarrierDetectedEvent(1_000, "camera", 40.0, "barrier-1", false, Lane.CENTER)}),
            Arguments.of("open barrier", 50.0, new Object[]{
                new BarrierDetectedEvent(1_000, "camera", 30.0, "barrier-1", true, Lane.CENTER)}),
            // Weather.
            Arguments.of("rain", 90.0, new Object[]{
                new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.9, 0.9)}),
            Arguments.of("rain with low visibility and grip", 90.0, new Object[]{
                new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.7, 0.65)}),
            Arguments.of("reduced visibility", 88.0, new Object[]{
                new ReducedVisibilityEvent(1_000, "environment", 0.55)}),
            Arguments.of("reduced grip", 88.0, new Object[]{
                new ReducedGripEvent(1_000, "environment", 0.55)}),
            Arguments.of("reduced visibility and grip", 88.0, new Object[]{
                new ReducedVisibilityEvent(1_000, "environment", 0.55),
                new ReducedGripEvent(1_000, "environment", 0.55)}),
            Arguments.of("clear weather", 90.0, new Object[]{
                new WeatherEvent(1_000, "env", WeatherCondition.CLEAR, 1.0, 1.0)}),
            Arguments.of("green light and pedestrian", 50.0, new Object[]{
                new TrafficLightEvent(1_000, "camera", TrafficLightState.GREEN, 20.0, "light-1"),
                new PedestrianDetectedEvent(1_000, "camera", 14.0, "ped-1", true)})
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @Timeout(20)
    void bothBackendsDecideAlike(String name, double speedKmh, Object[] events) {
        try (DroolsTestHarness drools = new DroolsTestHarness(speedKmh);
             PrologTestHarness prolog = new PrologTestHarness()) {
            DrivingDecision expected = drools.cycle(1_000, speedKmh, events);
            DrivingDecision actual = prolog.cycle(1_000, speedKmh, events);
            assertEquals(expected.getAction(), actual.getAction(), "action; Drools rule: " + expected.getSourceRule()
                + ", Prolog rule: " + actual.getSourceRule());
            assertEquals(expected.getRecommendedSpeedKmh(), actual.getRecommendedSpeedKmh(), 0.01, "recommended speed");
            assertEquals(expected.getPriority(), actual.getPriority(), "priority");
            assertEquals(expected.getSourceRule(), baseRuleName(actual.getSourceRule()), "rule");
        }
    }

    /** One cycle of a multi-cycle comparison: time, speed and the events seen then. */
    private record Step(long timestampMillis, double speedKmh, Object... events) {
    }

    static Stream<Arguments> sequences() {
        return Stream.of(
            Arguments.of("stop sign: approach, stop, hold 1.2 s, proceed", new Step[]{
                new Step(1_000, 50.0, new StopSignEvent(1_000, "camera", 35, "stop-1")),
                new Step(2_000, 4.0, new StopSignEvent(2_000, "camera", 3.0, "stop-1")),
                new Step(2_200, 0.0, new StopSignEvent(2_200, "camera", 2.5, "stop-1")),
                new Step(3_000, 0.0, new StopSignEvent(3_000, "camera", 2.5, "stop-1")),
                new Step(3_600, 0.0, new StopSignEvent(3_600, "camera", 1.0, "stop-1"))}),
            Arguments.of("stop sign: emergency brake held, then released", new Step[]{
                new Step(1_000, 50.0, new StopSignEvent(1_000, "camera", 25, "stop-1")),
                new Step(1_100, 40.0, new StopSignEvent(1_100, "camera", 22, "stop-1")),
                new Step(1_200, 30.0, new StopSignEvent(1_200, "camera", 19, "stop-1"))}),
            Arguments.of("front vehicle: closing trend in the distance window", new Step[]{
                new Step(1_000, 40.0, new FrontDistanceEvent(1_000, "front-radar", 48.0, "front-1")),
                new Step(1_100, 40.0, new FrontDistanceEvent(1_100, "front-radar", 45.0, "front-1")),
                new Step(1_200, 40.0, new FrontDistanceEvent(1_200, "front-radar", 42.0, "front-1"))}),
            Arguments.of("front vehicle: samples of two vehicles are no trend", new Step[]{
                new Step(1_000, 40.0, new FrontDistanceEvent(1_000, "front-radar", 48.0, "front-1")),
                new Step(1_100, 40.0, new FrontDistanceEvent(1_100, "front-radar", 47.5, "front-1")),
                new Step(1_200, 40.0, new FrontDistanceEvent(1_200, "front-radar", 40.0, "front-2"))}),
            Arguments.of("front vehicle: a 0 m reading clears the target", new Step[]{
                new Step(1_000, 5.0,
                    new FrontDistanceEvent(1_000, "front-radar", 3.0, "front-1"),
                    new RelativeSpeedEvent(1_000, "front-radar", 0.5, "front-1")),
                new Step(1_100, 5.0,
                    new FrontDistanceEvent(1_100, "front-radar", 0.0, "front-1"),
                    new RelativeSpeedEvent(1_100, "front-radar", 0.5, "front-1"))}),
            Arguments.of("front vehicle: forgotten after missed radar readings", new Step[]{
                new Step(1_000, 50.0,
                    new FrontDistanceEvent(1_000, "front-radar", 10.0, "front-1"),
                    new RelativeSpeedEvent(1_000, "front-radar", 3.0, "front-1")),
                new Step(1_300, 50.0)}),
            Arguments.of("overtake that never leaves its lane: following again, cancelled after 1 s, restarted", new Step[]{
                new Step(1_000, 60.0,
                    new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                    new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1"),
                    new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)),
                new Step(1_100, 60.0,
                    new FrontDistanceEvent(1_100, "front-radar", 39.5, "front-1"),
                    new RelativeSpeedEvent(1_100, "front-radar", 5.0, "front-1"),
                    new LaneOccupancyEvent(1_100, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)),
                new Step(2_100, 60.0,
                    new FrontDistanceEvent(2_100, "front-radar", 34.5, "front-1"),
                    new RelativeSpeedEvent(2_100, "front-radar", 5.0, "front-1"),
                    new LaneOccupancyEvent(2_100, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("obstacle: forgotten 1 s after the last reading", new Step[]{
                new Step(1_000, 30.0,
                    new ObstacleDetectedEvent(1_000, "camera", 40.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.CENTER)),
                new Step(2_100, 30.0)}),
            Arguments.of("high severity obstacle: emergency only on a recent reading", new Step[]{
                new Step(1_000, 20.0,
                    new ObstacleDetectedEvent(1_000, "camera", 6.0, "obstacle-1", HazardSeverity.HIGH, Lane.CENTER)),
                new Step(1_400, 20.0)}),
            Arguments.of("rain and persistent low TTC", new Step[]{
                new Step(1_000, 80.0, new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.7, 0.65)),
                new Step(1_300, 80.0,
                    new TTCEvent(1_200, "radar", 2.9, "front-1"),
                    new TTCEvent(1_290, "radar", 2.8, "front-1"))}),
            Arguments.of("low TTC without rain", new Step[]{
                new Step(1_000, 80.0, new WeatherEvent(1_000, "env", WeatherCondition.CLEAR, 1.0, 1.0)),
                new Step(1_300, 80.0,
                    new TTCEvent(1_200, "radar", 2.9, "front-1"),
                    new TTCEvent(1_290, "radar", 2.8, "front-1"))}),
            Arguments.of("rain persists after the last weather reading", new Step[]{
                new Step(1_000, 90.0, new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.9, 0.9)),
                new Step(5_000, 90.0)}),
            Arguments.of("reduced visibility counts for 500 ms", new Step[]{
                new Step(1_000, 88.0, new ReducedVisibilityEvent(1_000, "environment", 0.55)),
                new Step(1_400, 88.0),
                new Step(1_600, 88.0)}),
            Arguments.of("move off from a standstill until close to the target", new Step[]{
                new Step(1_000, 0.0),
                new Step(1_100, 10.0),
                new Step(1_200, 29.5)}),
            Arguments.of("a vehicle that only slowed down is not sped up", new Step[]{
                new Step(1_000, 20.0),
                new Step(1_100, 20.0)}),
            Arguments.of("held for a pedestrian, moves off once nothing holds it", new Step[]{
                new Step(1_000, 0.0, new PedestrianDetectedEvent(1_000, "camera", 20.0, "ped-1", true)),
                new Step(1_100, 0.0, new PedestrianDetectedEvent(1_100, "camera", 0.0, "none", false)),
                new Step(1_200, 0.0),
                new Step(1_300, 5.0)}),
            Arguments.of("move off under a 20 km/h limit", new Step[]{
                new Step(1_000, 0.0, new SpeedLimitDetectedEvent(1_000, "camera", 20, 10, "sign-20")),
                new Step(1_100, 8.0)}),
            Arguments.of("keep right: right lane clear alongside and 80 m ahead", new Step[]{
                new Step(5_000, 60.0,
                    new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("keep right: not in the first 4 s", new Step[]{
                new Step(3_000, 60.0,
                    new LaneOccupancyEvent(3_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("keep right: right lane free only 60 m ahead", new Step[]{
                new Step(5_000, 60.0,
                    new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY,
                        true, 60.0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY))}),
            Arguments.of("keep right: not with a vehicle 70 m ahead", new Step[]{
                new Step(5_000, 60.0,
                    new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY),
                    new FrontDistanceEvent(5_000, "front-radar", 70.0, "front-1"),
                    new RelativeSpeedEvent(5_000, "front-radar", 0.0, "front-1"))}),
            Arguments.of("keep right: not with an obstacle ahead in another lane", new Step[]{
                new Step(5_000, 60.0,
                    new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY),
                    new ObstacleDetectedEvent(5_000, "camera", 90.0, "obstacle-1", HazardSeverity.LOW, Lane.LEFT))}),
            Arguments.of("keep right from a standstill: moving off wins the tie", new Step[]{
                new Step(5_000, 0.0,
                    new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("driver asks for left, left lane clear", new Step[]{
                new Step(1_000, 60.0,
                    new LaneChangeRequestEvent(1_000, "ui", LaneChangeDirection.LEFT),
                    new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("driver asks for left, left lane occupied", new Step[]{
                new Step(1_000, 60.0, new LaneChangeRequestEvent(1_000, "ui", LaneChangeDirection.LEFT))}),
            Arguments.of("driver asks for right, right lane clear", new Step[]{
                new Step(1_000, 60.0,
                    new LaneChangeRequestEvent(1_000, "ui", LaneChangeDirection.RIGHT),
                    new LaneOccupancyEvent(1_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("driver lane change request expires after 1.5 s", new Step[]{
                new Step(1_000, 60.0,
                    new LaneChangeRequestEvent(1_000, "ui", LaneChangeDirection.LEFT),
                    new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)),
                new Step(2_000, 60.0,
                    new LaneOccupancyEvent(2_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY)),
                new Step(2_600, 60.0,
                    new LaneOccupancyEvent(2_600, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("a pending driver request holds back keep right", new Step[]{
                new Step(5_000, 60.0,
                    new LaneChangeRequestEvent(5_000, "ui", LaneChangeDirection.LEFT),
                    new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("a pending driver request holds back moving off", new Step[]{
                new Step(1_000, 0.0,
                    new LaneChangeRequestEvent(1_000, "ui", LaneChangeDirection.LEFT),
                    new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY))}),
            Arguments.of("driver acceleration request accepted, then expired", new Step[]{
                new Step(1_000, 60.0, new AccelerationRequestEvent(1_000, "ui", 90.0)),
                new Step(2_000, 70.0),
                new Step(2_600, 80.0)}),
            Arguments.of("driver acceleration request against a lower signed limit", new Step[]{
                new Step(1_000, 40.0,
                    new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50"),
                    new AccelerationRequestEvent(1_000, "ui", 90.0))}),
            Arguments.of("stop sign: forgotten once out of sight", new Step[]{
                new Step(1_000, 50.0, new StopSignEvent(1_000, "camera", 35, "stop-1")),
                new Step(2_600, 50.0)})
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sequences")
    @Timeout(20)
    void bothBackendsDecideAlikeOverSeveralCycles(String name, Step[] steps) {
        try (DroolsTestHarness drools = new DroolsTestHarness(steps[0].speedKmh());
             PrologTestHarness prolog = new PrologTestHarness()) {
            for (Step step : steps) {
                DrivingDecision expected = drools.cycle(step.timestampMillis(), step.speedKmh(), step.events());
                DrivingDecision actual = prolog.cycle(step.timestampMillis(), step.speedKmh(), step.events());
                String at = " at t=" + step.timestampMillis() + "; Drools rule: " + expected.getSourceRule()
                    + ", Prolog rule: " + actual.getSourceRule();
                assertEquals(expected.getAction(), actual.getAction(), "action" + at);
                assertEquals(expected.getRecommendedSpeedKmh(), actual.getRecommendedSpeedKmh(), 0.01, "speed" + at);
                assertEquals(expected.getPriority(), actual.getPriority(), "priority" + at);
                assertEquals(expected.getSourceRule(), baseRuleName(actual.getSourceRule()), "rule" + at);
            }
        }
    }

    private static String baseRuleName(String prologRuleId) {
        int suffix = prologRuleId.indexOf(" (");
        return suffix < 0 ? prologRuleId : prologRuleId.substring(0, suffix);
    }
}
