package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.PedestrianCrossingState;
import com.one2drive.projeto1.domain.RiskLevel;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.events.AccelerationRequestEvent;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.LaneChangeRequestEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.LeftLaneVehicleEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.drools.facts.CruiseTargetFact;
import com.one2drive.projeto1.drools.facts.FuzzyConclusion;
import com.one2drive.projeto1.drools.facts.FuzzyMembership;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.KieServices;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrivingExpertSystemRulesTest {
    /** Distance from a stop sign back to its stop line: the rules measure to the line. */
    private static final double STOP_LINE = com.one2drive.projeto1.decision.StoppingPolicy.STOP_SIGN_OFFSET_METERS;


    /**
     * The knowledge base is compiled once, on first use, and that takes several seconds.
     * Doing it here keeps it out of the per-test timeouts: a compilation interrupted by a
     * timeout leaves a broken knowledge base behind and fails every later test.
     */
    @BeforeAll
    static void compileKnowledgeBaseOnce() {
        new DroolsTestHarness(0.0).close();
    }

    @Test
    @Timeout(10)
    void kieBaseInitializes() {
        var container = KieServices.Factory.get().getKieClasspathContainer();
        assertNotNull(container.verify());
        assertTrue(container.verify().getMessages().isEmpty(), "Knowledge base should compile without DRL errors");
        assertNotNull(container.newKieSession("drivingKsession"));
    }

    @Test
    @Timeout(10)
    void speedLimitTriggersReduceSpeed() {
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                90.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50")
            );
            // 20 m before the sign the limit is still pending: the vehicle slows towards it,
            // and it only becomes the limit in force at the sign.
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals("Slow down in time for the speed limit ahead", decision.getSourceRule());
            assertTrue(decision.getRecommendedSpeedKmh() > 50.0 && decision.getRecommendedSpeedKmh() < 60.0,
                "recommended " + decision.getRecommendedSpeedKmh());

            harness.moveTo(20.0);
            DrivingDecision atSign = harness.cycle(1_200, 60.0);
            assertEquals(DrivingAction.REDUCE_SPEED, atSign.getAction());
            assertEquals(50.0, atSign.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Reduce speed when exceeding detected limit", atSign.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void lowerLimitFarAheadDoesNotSlowTheVehicleYet() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 30, 110, "sign-30")
            );
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(60.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void sessionSeedLimitAloneIsNotATargetToSpeedUpTo() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(1_000, 60.0);
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(60.0, decision.getRecommendedSpeedKmh(), 0.01,
                "the placeholder limit must not turn into a target the vehicle chases");
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void signAboveCurrentSpeedBecomesTheTargetToBuildUpTo() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 90, 80, "sign-90")
            );
            assertEquals(60.0, decision.getRecommendedSpeedKmh(), 0.01,
                "a higher limit ahead is not a target before its sign");

            harness.moveTo(80.0);
            decision = harness.cycle(1_200, 60.0);
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(90.0, decision.getRecommendedSpeedKmh(), 0.01,
                "approaching a limit from below means building up to it, not holding the old speed");
            assertEquals("Maintain speed when compliant with detected limit", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void redLightTriggersStop() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 18, "light-1")
            );
            assertTrue(decision.getAction() == DrivingAction.STOP
                || decision.getAction() == DrivingAction.EMERGENCY_BRAKE,
                "At 18 m and 60 km/h the conservative stopping curve may require a continuous emergency brake");
        }
    }

    @Test
    @Timeout(10)
    void stopSignTriggersStopSequence() {
        try (DroolsTestHarness harness = new DroolsTestHarness(55.0)) {
            DrivingDecision approach = harness.cycle(
                1_000,
                50.0,
                // 35 m from the stop line is the closest point at which comfortable braking (2.8 m/s2) can
                // still stop from 50 km/h, so the approach must be a plain reduction.
                new StopSignEvent(1_000, "camera", STOP_LINE + 35, "stop-1")
            );
            assertEquals(DrivingAction.REDUCE_SPEED, approach.getAction());

            DrivingDecision near = harness.cycle(
                2_000,
                4.0,
                new StopSignEvent(2_000, "camera", STOP_LINE + 3.0, "stop-1")
            );
            assertEquals(DrivingAction.STOP, near.getAction());

            harness.cycle(2_200, 0.0, new StopSignEvent(2_200, "camera", STOP_LINE + 2.5, "stop-1"));
            DrivingDecision proceed = harness.cycle(3_600, 0.0, new StopSignEvent(3_600, "camera", STOP_LINE + 1.0, "stop-1"));
            assertEquals(DrivingAction.CONTINUE, proceed.getAction());
        }
    }

    @Test
    @Timeout(10)
    void stopSignTooCloseForComfortableBrakingTriggersEmergencyBrake() {
        try (DroolsTestHarness harness = new DroolsTestHarness(55.0)) {
            DrivingDecision approach = harness.cycle(
                1_000,
                50.0,
                new StopSignEvent(1_000, "camera", STOP_LINE + 25, "stop-1")
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, approach.getAction());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianTriggersBraking() {
        try (DroolsTestHarness harness = new DroolsTestHarness(50.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                50.0,
                new PedestrianDetectedEvent(1_000, "camera", 18.0, "ped-1", true)
            );
            assertTrue(decision.getAction() == DrivingAction.BRAKE || decision.getAction() == DrivingAction.STOP);
        }
    }

    @Test
    @Timeout(10)
    void ttcCriticalTriggersEmergency() {
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                90.0,
                new TTCEvent(700, "radar", 2.3, "front-1"),
                new TTCEvent(850, "radar", 2.1, "front-1"),
                new TTCEvent(980, "radar", 1.9, "front-1")
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void isolatedTtcSampleDoesNotTriggerPersistentRule() {
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                90.0,
                new TTCEvent(1_000, "radar", 2.2, "front-1")
            );
            assertTrue(decision.getAction() == DrivingAction.MAINTAIN_SPEED || decision.getAction() == DrivingAction.CONTINUE);
        }
    }

    @Test
    @Timeout(10)
    void repeatedTtcInsideWindowTriggersPersistentEmergency() {
        try (DroolsTestHarness harness = new DroolsTestHarness(85.0)) {
            harness.cycle(1_000, 85.0, new TTCEvent(1_000, "radar", 2.4, "front-1"));
            harness.cycle(1_400, 85.0, new TTCEvent(1_400, "radar", 2.3, "front-1"));
            DrivingDecision decision = harness.cycle(1_700, 85.0, new TTCEvent(1_700, "radar", 2.2, "front-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void laneChangeBlocked() {
        try (DroolsTestHarness harness = new DroolsTestHarness(70.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                70.0,
                new LaneChangeRequestEvent(1_000, "ui", LaneChangeDirection.LEFT),
                new LaneOccupancyEvent(1_000, "blind", Lane.LEFT, true, 8.0)
            );
            // The lane decision is arbitrated in its own channel: KEEP_LANE is the lane
            // decision, while the headline action stays the speed action.
            assertEquals(DrivingAction.KEEP_LANE, decision.getLaneAction());
            assertEquals("Block lane change when target lane occupied", decision.getLaneSourceRule());
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void laneChangeSafe() {
        try (DroolsTestHarness harness = new DroolsTestHarness(70.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                70.0,
                new LaneChangeRequestEvent(1_000, "ui", LaneChangeDirection.RIGHT),
                new LaneOccupancyEvent(1_000, "blind", Lane.RIGHT, false, Double.POSITIVE_INFINITY)
            );
            assertEquals(DrivingAction.CHANGE_LANE_RIGHT, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void potholeAdjustsSpeed() {
        try (DroolsTestHarness harness = new DroolsTestHarness(75.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                75.0,
                new PotholeDetectedEvent(1_000, "camera", 25.0, "pth-1", HazardSeverity.HIGH, Lane.CENTER)
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertTrue(decision.getRecommendedSpeedKmh() <= 30.0);
        }
    }

    @Test
    @Timeout(10)
    void barrierClosedTriggersStop() {
        // 30 km/h: comfortable braking stops well within the 22 m left.
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                30.0,
                new BarrierDetectedEvent(1_000, "camera", 22.0, "bar-1", false)
            );
            assertEquals(DrivingAction.STOP, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void barrierClosedTooCloseForComfortableBrakingTriggersEmergencyBrake() {
        // 60 km/h needs ~50 m to stop comfortably; with 22 m left it must brake hard.
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new BarrierDetectedEvent(1_000, "camera", 22.0, "bar-1", false)
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
            assertEquals("Closed barrier requires stop", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void closedBarrierFarAheadIsApproachedOnTheBrakingCurve() {
        // 90 km/h, barrier 130 m ahead (the approach binds from 134 m), both sides clear: the
        // mandatory stop needs no side to be safe, so the approach rule alone brakes to the
        // anticipation curve, 79 km/h with 120 m of room before the stopping point 10 m short
        // of the barrier.
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                90.0,
                new BarrierDetectedEvent(1_000, "camera", 130.0, "bar-1", false, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind", Lane.LEFT, false, Double.POSITIVE_INFINITY),
                new LaneOccupancyEvent(1_000, "blind", Lane.RIGHT, false, Double.POSITIVE_INFINITY)
            );
            assertEquals(DrivingAction.BRAKE, decision.getSpeedAction());
            assertEquals("Stop before closed barrier while still in its lane", decision.getSpeedSourceRule());
            assertEquals(78.9, decision.getRecommendedSpeedKmh(), 0.5);
            assertFalse(decision.getLaneAction().isLaneManeuver(), "130 m is beyond the 55 m the avoidance decides at");
        }
    }

    @Test
    @Timeout(10)
    void closedBarrierTooCloseToGoRoundIsBrakedForInsteadOfAvoided() {
        // 60 km/h, barrier 25 m ahead, right side clear: a lane change would not leave the lane
        // before the barrier (47 m needed), so the vehicle keeps its lane and brakes hard.
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new BarrierDetectedEvent(1_000, "camera", 25.0, "bar-1", false, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind", Lane.RIGHT, false, Double.POSITIVE_INFINITY)
            );
            assertFalse(decision.getLaneAction().isLaneManeuver(), "too close to go round: " + decision.getLaneSourceRule());
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction());
            assertEquals("Stop before closed barrier while still in its lane", decision.getSpeedSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void potholeTooCloseToGoRoundIsCrossedSlowly() {
        // 60 km/h, pothole 20 m ahead, right side clear: no room to leave the lane before it,
        // so it is driven over at the crossing speed instead (Art. 24 CE).
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new PotholeDetectedEvent(1_000, "camera", 20.0, "pth-1", HazardSeverity.HIGH, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind", Lane.RIGHT, false, Double.POSITIVE_INFINITY)
            );
            assertFalse(decision.getLaneAction().isLaneManeuver(), "too close to go round: " + decision.getLaneSourceRule());
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getSpeedAction());
            assertEquals("Pothole ahead reduce speed", decision.getSpeedSourceRule());
            assertEquals(30.0, decision.getRecommendedSpeedKmh(), 0.1);
        }
    }

    @Test
    @Timeout(10)
    void potholeFarAheadIsApproachedOnTheCurveToTheCrossingSpeed() {
        // 90 km/h, pothole 100 m ahead and no safe side: the slow-down starts from the distance
        // a comfortable slow-down to 30 km/h needs (112 m), along the anticipation curve.
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                90.0,
                new PotholeDetectedEvent(1_000, "camera", 100.0, "pth-1", HazardSeverity.HIGH, Lane.CENTER)
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getSpeedAction());
            assertEquals("Pothole ahead reduce speed", decision.getSpeedSourceRule());
            assertEquals(78.1, decision.getRecommendedSpeedKmh(), 0.5);
        }
    }

    @Test
    @Timeout(10)
    void fastEgoBrakesHardRatherThanPassASlowerLeftLaneVehicleOnItsRight() {
        // 90 km/h, a vehicle doing 30 km/h 40 m ahead in the lane to the left: comfortable
        // braking would gain 58 m on it, so the ego would draw level and pass on the right.
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                90.0,
                new LeftLaneVehicleEvent(1_000, "front-radar", 40.0, 60.0 / 3.6, "left-1")
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction());
            assertEquals("Do not pass a slower left lane vehicle on its right", decision.getSpeedSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void rainAdjustsSafeSpeed() {
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                90.0,
                new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.7, 0.65)
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertTrue(decision.getRecommendedSpeedKmh() <= 65.0);
        }
    }

    @Test
    @Timeout(10)
    void reducedVisibilityAndGripTriggerConservativeReduction() {
        try (DroolsTestHarness harness = new DroolsTestHarness(88.0)) {
            // The visibility and grip rules read the persistent weather state (updated by the
            // weather event), no longer the short-lived reduced-visibility/grip events.
            DrivingDecision decision = harness.cycle(
                1_000,
                88.0,
                new WeatherEvent(1_000, "env", WeatherCondition.CLEAR, 0.55, 0.55)
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertTrue(decision.getRecommendedSpeedKmh() <= 50.0);
            // 0.55 is reduced (0.75) and fair (0.25) for both inputs: slow 0.75, moderate 0.25,
            // whose centroid is the ceiling.
            assertEquals("Defuzzify weather speed ceiling", decision.getSourceRule());
            assertEquals(44.64, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals(RiskLevel.HIGH, decision.getRiskLevel());

            DrivingDecision later = harness.cycle(1_600, 88.0);
            assertEquals(DrivingAction.REDUCE_SPEED, later.getAction(),
                "the weather is a persistent condition: the ceiling must not lapse between sensor readings");
            assertEquals(44.64, later.getRecommendedSpeedKmh(), 0.01);
        }
    }

    @Test
    @Timeout(10)
    void rainAloneIsAModerateFuzzyCeilingOf65() {
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(1_000, 90.0,
                new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 1.0, 1.0));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals("Defuzzify weather speed ceiling", decision.getSourceRule());
            assertEquals(65.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals(RiskLevel.MODERATE, decision.getRiskLevel());
        }
    }

    @Test
    @Timeout(10)
    void goodClearWeatherProposesNoFuzzyCeiling() {
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            DrivingDecision decision = harness.cycle(1_000, 90.0,
                new WeatherEvent(1_000, "env", WeatherCondition.CLEAR, 0.85, 0.9));
            assertTrue(decision.getProposals().stream()
                    .noneMatch(p -> "Defuzzify weather speed ceiling".equals(p.getSourceRule())),
                "at 0.85 and above only the non-restrictive term holds: no ceiling");
        }
    }

    @Test
    @Timeout(10)
    void fuzzyFactsAreRetractedWhenTheWeatherClears() {
        try (DroolsTestHarness harness = new DroolsTestHarness(88.0)) {
            harness.cycle(1_000, 88.0, new WeatherEvent(1_000, "env", WeatherCondition.CLEAR, 0.55, 0.55));
            assertTrue(fuzzyConclusions(harness).contains("slow"));

            // Truth maintenance: the conclusions were inserted logically, so the new reading
            // takes away their justification and the engine retracts them.
            DrivingDecision decision = harness.cycle(1_400, 88.0,
                new WeatherEvent(1_400, "env", WeatherCondition.CLEAR, 1.0, 1.0));
            assertEquals(List.of("light"), fuzzyConclusions(harness));
            assertTrue(decision.getProposals().stream()
                .noneMatch(p -> "Defuzzify weather speed ceiling".equals(p.getSourceRule())));
            assertEquals(8, harness.session().getKieSession().getObjects(o -> o instanceof FuzzyMembership).size(),
                "four terms for each of the two inputs, the old degrees gone");
        }
    }

    private static List<String> fuzzyConclusions(DroolsTestHarness harness) {
        return harness.session().getKieSession().getObjects(o -> o instanceof FuzzyConclusion).stream()
            .map(o -> ((FuzzyConclusion) o).getTerm())
            .sorted()
            .toList();
    }

    @Test
    @Timeout(10)
    void conflictGreenLightAndPedestrianPedestrianWins() {
        try (DroolsTestHarness harness = new DroolsTestHarness(50.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                50.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.GREEN, 20.0, "light-1"),
                new PedestrianDetectedEvent(1_000, "camera", 14.0, "ped-1", true)
            );
            assertTrue(decision.getAction() == DrivingAction.STOP || decision.getAction() == DrivingAction.BRAKE);
        }
    }

    @Test
    @Timeout(10)
    void conflictSpeedLimitAndPotholePotholeWins() {
        try (DroolsTestHarness harness = new DroolsTestHarness(95.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                95.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 90, 30, "sign-90"),
                new PotholeDetectedEvent(1_000, "camera", 22.0, "pth-1", HazardSeverity.HIGH, Lane.CENTER)
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertTrue(decision.getRecommendedSpeedKmh() <= 30.0);
        }
    }

    @Test
    @Timeout(10)
    void conflictAccelerationAndCriticalTtcEmergencyWins() {
        try (DroolsTestHarness harness = new DroolsTestHarness(80.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                80.0,
                new AccelerationRequestEvent(1_000, "ui", 120.0),
                new TTCEvent(900, "radar", 2.4, "front-1"),
                new TTCEvent(950, "radar", 2.2, "front-1"),
                new TTCEvent(999, "radar", 2.0, "front-1")
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void conflictRainAndTtcRiskConservativeAction() {
        try (DroolsTestHarness harness = new DroolsTestHarness(80.0)) {
            harness.cycle(1_000, 80.0, new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.7, 0.65));
            DrivingDecision decision = harness.cycle(
                1_300,
                80.0,
                new TTCEvent(1_200, "radar", 2.9, "front-1"),
                new TTCEvent(1_290, "radar", 2.8, "front-1")
            );
            assertEquals(DrivingAction.BRAKE, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void lowTtcCountsInRainOnlyWhenARainReadingCoincidesWithIt() {
        try (DroolsTestHarness harness = new DroolsTestHarness(80.0)) {
            harness.cycle(1_000, 80.0, new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.7, 0.65));
            // Still raining, but the last rain reading is 900 ms before these TTC readings.
            DrivingDecision decision = harness.cycle(
                2_000,
                80.0,
                new TTCEvent(1_900, "radar", 2.9, "front-1"),
                new TTCEvent(1_990, "radar", 2.8, "front-1")
            );
            assertTrue(decision.getProposals().stream()
                    .noneMatch(p -> "Persistent low TTC in rain increases conservatism".equals(p.getSourceRule())),
                "no rain reading within 400 ms of the low TTC readings");

            DrivingDecision withRain = harness.cycle(
                2_100,
                80.0,
                new WeatherEvent(2_100, "env", WeatherCondition.RAIN, 0.7, 0.65)
            );
            assertEquals(DrivingAction.BRAKE, withRain.getAction(),
                "a rain reading within 400 ms of both readings makes them count");
        }
    }

    @Test
    @Timeout(10)
    void closingOnStationaryVehicleFollowsTheSafeDistanceCurve() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            // 60 km/h, stationary vehicle 40 m ahead: 28 m of room before the 12 m gap.
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 60.0 / 3.6, "front-1")
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals("Keep safe following distance behind front vehicle", decision.getSourceRule());
            assertEquals(Math.sqrt(2.0 * 2.0 * 28.0) * 3.6, decision.getRecommendedSpeedKmh(), 0.1);
        }
    }

    @Test
    @Timeout(10)
    void brakingBehindACloseFrontVehicleTargetsBelowItsSpeed() {
        try (DroolsTestHarness harness = new DroolsTestHarness(50.0)) {
            // 50 km/h, closing at 2 m/s on a vehicle 10 m ahead doing 42.8 km/h: the brake
            // targets 3 km/h below that vehicle's speed so the gap reopens.
            DrivingDecision decision = harness.cycle(
                1_000,
                50.0,
                new FrontDistanceEvent(1_000, "front-radar", 10.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 2.0, "front-1")
            );
            assertEquals(DrivingAction.BRAKE, decision.getAction());
            assertEquals("Brake when front vehicle distance low", decision.getSourceRule());
            assertEquals((50.0 / 3.6 - 2.0) * 3.6 - 3.0, decision.getRecommendedSpeedKmh(), 0.1);
        }
    }

    @Test
    @Timeout(10)
    void followingTooCloseBehindAMovingVehicleReopensTheGap() {
        try (DroolsTestHarness harness = new DroolsTestHarness(40.0)) {
            // Same speed as the vehicle 10 m ahead: not closing, so neither the brake nor the
            // following curve says anything, yet the gap is below the 12 m to keep.
            DrivingDecision decision = harness.cycle(
                1_000,
                40.0,
                new FrontDistanceEvent(1_000, "front-radar", 10.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1")
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals("Keep safe following distance behind front vehicle", decision.getSourceRule());
            assertEquals(37.0, decision.getRecommendedSpeedKmh(), 0.1);
            assertTrue(decision.getReason().startsWith("Reopening the following gap"), decision.getReason());
        }
    }

    @Test
    @Timeout(10)
    void vehicleFollowedAtItsSpeedIsOvertakenWhenTheLeftLaneIsClear() {
        try (DroolsTestHarness harness = new DroolsTestHarness(40.0)) {
            // No scenario cruise speed: the desired speed is the limit in force (120 km/h).
            harness.session().getKieSession().insert(new CruiseTargetFact(0.0));
            // Following a 40 km/h vehicle 20 m ahead at its speed, in CENTER, with the left
            // lane clear far beyond it. The closing-in overtake cannot start (not closing),
            // so this is the exit from following. The lane change is decided in the lateral
            // channel, while the speed channel keeps the following distance.
            DrivingDecision decision = harness.cycle(
                1_000,
                40.0,
                new FrontDistanceEvent(1_000, "front-radar", 20.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind", Lane.LEFT, false, Double.POSITIVE_INFINITY, true,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
            );
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getAction());
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction());
            assertEquals("Overtake the vehicle being followed", decision.getLaneSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void stoppedBehindStationaryVehicleHoldsTheStop() {
        try (DroolsTestHarness harness = new DroolsTestHarness(0.0)) {
            // Both vehicles at rest 12 m apart, left lane not reported clear: nothing is closing
            // in any more, and the stop must still hold.
            DrivingDecision decision = harness.cycle(
                1_000,
                0.0,
                new FrontDistanceEvent(1_000, "front-radar", 12.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1")
            );
            assertEquals(DrivingAction.STOP, decision.getAction());
            assertEquals("Keep safe following distance behind front vehicle", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void stoppedBehindStationaryVehicleGoesAroundWhenLeftLaneIsClear() {
        try (DroolsTestHarness harness = new DroolsTestHarness(0.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                0.0,
                new FrontDistanceEvent(1_000, "front-radar", 12.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind", Lane.LEFT, false, Double.POSITIVE_INFINITY)
            );
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getAction());
            assertEquals("Go around stationary front vehicle from standstill", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void redLightAheadForbidsGoingAroundStationaryVehicle() {
        try (DroolsTestHarness harness = new DroolsTestHarness(0.0)) {
            // A queue stopped at a red light is not an obstacle to go around (Art. 41 CE).
            DrivingDecision decision = harness.cycle(
                1_000,
                0.0,
                new FrontDistanceEvent(1_000, "front-radar", 12.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind", Lane.LEFT, false, Double.POSITIVE_INFINITY),
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 30.0, "light-1")
            );
            assertEquals(DrivingAction.STOP, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void distanceSamplesOfDifferentVehiclesAreNotCombinedIntoOneTrend() {
        try (DroolsTestHarness harness = new DroolsTestHarness(40.0)) {
            // Two samples of one vehicle, then the radar switches to another vehicle that is
            // closer. The drop from 48 m to 40 m is between two vehicles, not an approach.
            harness.cycle(1_000, 40.0, new FrontDistanceEvent(1_000, "front-radar", 48.0, "front-1"));
            harness.cycle(1_100, 40.0, new FrontDistanceEvent(1_100, "front-radar", 47.5, "front-1"));
            DrivingDecision decision = harness.cycle(1_200, 40.0,
                new FrontDistanceEvent(1_200, "front-radar", 40.0, "front-2"));
            assertEquals("Default maintain speed baseline", decision.getSourceRule(),
                "samples of two different vehicles must not form a closing trend");
        }
    }

    @Test
    @Timeout(10)
    void distanceSamplesOfTheSameVehicleStillFormATrend() {
        try (DroolsTestHarness harness = new DroolsTestHarness(40.0)) {
            harness.cycle(1_000, 40.0, new FrontDistanceEvent(1_000, "front-radar", 48.0, "front-1"));
            harness.cycle(1_100, 40.0, new FrontDistanceEvent(1_100, "front-radar", 45.0, "front-1"));
            DrivingDecision decision = harness.cycle(1_200, 40.0,
                new FrontDistanceEvent(1_200, "front-radar", 42.0, "front-1"));
            assertEquals("Approaching trend from sliding distance window", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void slowerVehicleInTheLeftLaneIsNotPassedOnItsRight() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            // 60 km/h, a vehicle doing 30 km/h 20 m ahead in the lane to the left: the ego may
            // not draw level with it, so it is slowed down to that vehicle's speed.
            double relativeMps = 30.0 / 3.6;
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new LeftLaneVehicleEvent(1_000, "front-radar", 20.0, relativeMps, "left-1")
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals("Do not pass a slower left lane vehicle on its right", decision.getSourceRule());
            // 20 m is inside the 35 m it must keep, so the target is the other vehicle's speed.
            assertEquals(30.0, decision.getRecommendedSpeedKmh(), 0.1);
        }
    }

    @Test
    @Timeout(10)
    void fasterVehicleInTheLeftLaneDoesNotSlowTheEgoDown() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new LeftLaneVehicleEvent(1_000, "front-radar", 20.0, -10.0 / 3.6, "left-1")
            );
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianAt35MetersPrevailsOverRedLightApproach() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            // At 70 m and 60 km/h the red light approach still allows the current speed.
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 70.0, "light-1"),
                new PedestrianDetectedEvent(1_000, "camera", 35.0, "ped-1", true)
            );
            assertEquals(DrivingAction.BRAKE, decision.getAction());
            assertEquals("Brake for pedestrian proximity", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianBetween10And20MetersStopsTheVehicleBeforeARedLight() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            for (double distance : new double[]{10.0, 15.0, 20.0}) {
                long now = 1_000 + (long) distance * 100;
                DrivingDecision decision = harness.cycle(
                    now,
                    60.0,
                    new TrafficLightEvent(now, "camera", TrafficLightState.RED, 70.0, "light-1"),
                    new PedestrianDetectedEvent(now, "camera", distance, "ped-1", true)
                );
                assertEquals(DrivingAction.STOP, decision.getAction(), "pedestrian at " + distance + " m");
                assertEquals("Stop for pedestrian in crossing zone", decision.getSourceRule());
            }
        }
    }

    @Test
    @Timeout(10)
    void pedestrianWithin6MetersTriggersEmergencyBrake() {
        try (DroolsTestHarness harness = new DroolsTestHarness(30.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                30.0,
                new PedestrianDetectedEvent(1_000, "camera", 6.0, "ped-1", true)
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
            assertEquals("Emergency stop for pedestrian extremely close", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianCrossingIntoThePathIsBrakedForBeforeItIsInThePath() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            // Still in another lane (inPath false), but walking a path that crosses the ego's.
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new PedestrianDetectedEvent(1_000, "camera", 70.0, "ped-1", false, PedestrianCrossingState.CROSSING)
            );
            assertEquals(DrivingAction.BRAKE, decision.getAction());
            assertEquals("Brake for pedestrian proximity", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void visiblePedestrianWithoutConflictIsOnlyMonitored() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new PedestrianDetectedEvent(1_000, "camera", 15.0, "ped-1", false, PedestrianCrossingState.WAITING)
            );
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianStillCrossingOutsideTheEgoLaneKeepsTheVehicleStopped() {
        try (DroolsTestHarness harness = new DroolsTestHarness(0.0)) {
            // Out of the ego's lane (inPath false) but not yet across the road.
            DrivingDecision near = harness.cycle(
                1_000,
                0.0,
                new PedestrianDetectedEvent(1_000, "camera", 15.0, "ped-1", false, PedestrianCrossingState.CROSSING)
            );
            assertEquals(DrivingAction.STOP, near.getAction());
            assertEquals("Stop for pedestrian in crossing zone", near.getSourceRule());

            DrivingDecision furtherBack = harness.cycle(
                1_200,
                0.0,
                new PedestrianDetectedEvent(1_200, "camera", 35.0, "ped-1", false, PedestrianCrossingState.CROSSING)
            );
            assertEquals(DrivingAction.STOP, furtherBack.getAction());
            assertEquals("Hold for pedestrian still crossing", furtherBack.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianThatFinishedCrossingNoLongerHoldsTheVehicle() {
        try (DroolsTestHarness harness = new DroolsTestHarness(0.0)) {
            DrivingDecision crossing = harness.cycle(
                1_000,
                0.0,
                new PedestrianDetectedEvent(1_000, "camera", 15.0, "ped-1", false, PedestrianCrossingState.CROSSING)
            );
            assertEquals(DrivingAction.STOP, crossing.getAction());

            DrivingDecision crossed = harness.cycle(
                1_200,
                0.0,
                new PedestrianDetectedEvent(1_200, "camera", 15.0, "ped-1", false, PedestrianCrossingState.CROSSED)
            );
            assertNotEquals(DrivingAction.STOP, crossed.getAction());
            assertFalse(crossed.getSourceRule().toLowerCase().contains("pedestrian"),
                "a pedestrian that has crossed must not hold the vehicle: " + crossed.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void crossingPedestrianWithin6MetersTriggersEmergencyBrakeEvenOutsideTheEgoLane() {
        try (DroolsTestHarness harness = new DroolsTestHarness(30.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                30.0,
                new PedestrianDetectedEvent(1_000, "camera", 5.0, "ped-1", false, PedestrianCrossingState.CROSSING)
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
            assertEquals("Emergency stop for pedestrian extremely close", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void yellowLightWithRoomToStopCommitsToStopAndDoesNotChangeItsMind() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            // 90 m at 60 km/h is more than the 76 m a comfortable stop before the line needs.
            DrivingDecision far = harness.cycle(
                1_000,
                60.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 90.0, "light-1")
            );
            assertEquals("Yellow traffic light: stop before the line", far.getSourceRule());
            assertEquals(DrivingAction.MAINTAIN_SPEED, far.getAction());

            // Closer than a stop would now need at this speed: the decision to stop stands.
            DrivingDecision closer = harness.cycle(
                1_200,
                60.0,
                new TrafficLightEvent(1_200, "camera", TrafficLightState.YELLOW, 50.0, "light-1")
            );
            assertEquals("Yellow traffic light: stop before the line", closer.getSourceRule());
            assertEquals(DrivingAction.REDUCE_SPEED, closer.getAction());

            DrivingDecision red = harness.cycle(
                1_400,
                40.0,
                new TrafficLightEvent(1_400, "camera", TrafficLightState.RED, 45.0, "light-1")
            );
            assertTrue(red.getSourceRule().startsWith("Red traffic light"), red.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void yellowLightTooCloseToStopCommitsToProceedAndDoesNotChangeItsMind() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            // 50 m at 60 km/h is less than the 76 m a comfortable stop before the line needs.
            DrivingDecision close = harness.cycle(
                1_000,
                60.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 50.0, "light-1")
            );
            assertFalse(close.getSourceRule().toLowerCase().contains("traffic light"), close.getSourceRule());

            // Slower now, so a stop would fit again, and then red: the decision to drive on stands.
            DrivingDecision slower = harness.cycle(
                1_200,
                20.0,
                new TrafficLightEvent(1_200, "camera", TrafficLightState.YELLOW, 45.0, "light-1")
            );
            assertFalse(slower.getSourceRule().toLowerCase().contains("traffic light"), slower.getSourceRule());

            DrivingDecision red = harness.cycle(
                1_400,
                20.0,
                new TrafficLightEvent(1_400, "camera", TrafficLightState.RED, 40.0, "light-1")
            );
            assertFalse(red.getSourceRule().toLowerCase().contains("traffic light"), red.getSourceRule());
            assertNotEquals(DrivingAction.STOP, red.getAction());
            assertNotEquals(DrivingAction.EMERGENCY_BRAKE, red.getAction());
        }
    }

    @Test
    @Timeout(10)
    void yellowLightDetectedFarAwayCommitsToStopAtTheFirstDetection() {
        try (DroolsTestHarness harness = new DroolsTestHarness(30.0)) {
            // 110 m at 30 km/h: far beyond the 40 m at which the brakes start to matter. The
            // decision to stop is made now; there is nothing to do about it yet.
            DrivingDecision far = harness.cycle(
                1_000,
                30.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 110.0, "light-1")
            );
            assertFalse(far.getSourceRule().toLowerCase().contains("traffic light"), far.getSourceRule());

            // Faster and close: a stop would no longer fit if it were decided now. It was
            // decided at the first detection, and it stands.
            DrivingDecision close = harness.cycle(
                1_200,
                60.0,
                new TrafficLightEvent(1_200, "camera", TrafficLightState.YELLOW, 50.0, "light-1")
            );
            assertEquals("Yellow traffic light: stop before the line", close.getSourceRule());
            assertEquals(DrivingAction.REDUCE_SPEED, close.getAction());

            DrivingDecision red = harness.cycle(
                1_400,
                50.0,
                new TrafficLightEvent(1_400, "camera", TrafficLightState.RED, 45.0, "light-1")
            );
            assertTrue(red.getSourceRule().startsWith("Red traffic light"), red.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void committedToProceedAtYellowStillStopsForAPedestrian() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision proceed = harness.cycle(
                1_000,
                60.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 50.0, "light-1")
            );
            assertFalse(proceed.getSourceRule().toLowerCase().contains("traffic light"), proceed.getSourceRule());

            DrivingDecision pedestrian = harness.cycle(
                1_200,
                60.0,
                new TrafficLightEvent(1_200, "camera", TrafficLightState.YELLOW, 46.0, "light-1"),
                new PedestrianDetectedEvent(1_200, "camera", 15.0, "ped-1", true)
            );
            assertEquals(DrivingAction.STOP, pedestrian.getAction());
            assertEquals("Stop for pedestrian in crossing zone", pedestrian.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void redLightAheadAndPastTheStopLineAtSpeedIsNeverLeftToTheBaseline() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            // 15 m to the light: 3 m past the stop line, which is 18 m before it.
            DrivingDecision moving = harness.cycle(
                1_000,
                60.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 15.0, "light-1")
            );
            assertNotEquals(DrivingAction.MAINTAIN_SPEED, moving.getAction());
            assertEquals(DrivingAction.EMERGENCY_BRAKE, moving.getAction());
            assertEquals("Hold at red traffic light", moving.getSourceRule());

            DrivingDecision atRest = harness.cycle(
                1_200,
                0.0,
                new TrafficLightEvent(1_200, "camera", TrafficLightState.RED, 6.0, "light-1")
            );
            assertEquals(DrivingAction.STOP, atRest.getAction());
            assertEquals("Hold at red traffic light", atRest.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void redLightPastTheStopLineIsStoppedForComfortablyWhenThatStillFitsBeforeTheLight() {
        try (DroolsTestHarness harness = new DroolsTestHarness(25.0)) {
            // 15 m to the light at 25 km/h: a comfortable stop takes 8.6 m and 12 m are left
            // once the 3 m tolerance is taken off. No emergency, and never the baseline.
            DrivingDecision slow = harness.cycle(
                1_000,
                25.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 15.0, "light-1")
            );
            assertEquals(DrivingAction.STOP, slow.getAction());
            assertEquals("Hold at red traffic light", slow.getSourceRule());
        }
        try (DroolsTestHarness harness = new DroolsTestHarness(45.0)) {
            // At 45 km/h the comfortable stop takes 27.9 m: it no longer fits in those 12 m.
            DrivingDecision fast = harness.cycle(
                1_000,
                45.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 15.0, "light-1")
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, fast.getAction());
            assertEquals("Hold at red traffic light", fast.getSourceRule());
        }
    }

    @Test
    void zeroRadarDistanceWithClosingSpeedDoesNotLoopForever() {
        // A radar sample clamped to 0 m resets the front vehicle fact; the relative-speed and
        // TTC updates must not re-adopt the target, or the three rules re-trigger each other
        // without end and fireAllRules never returns.
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            try (DroolsTestHarness harness = new DroolsTestHarness(5.0)) {
                harness.cycle(1_000, 5.0,
                    new FrontDistanceEvent(1_000, "front-radar", 3.0, "front-1"),
                    new RelativeSpeedEvent(1_000, "front-radar", 0.5, "front-1"),
                    new TTCEvent(1_000, "front-radar", 6.0, "front-1"));
                DrivingDecision decision = harness.cycle(1_100, 5.0,
                    new FrontDistanceEvent(1_100, "front-radar", 0.0, "front-1"),
                    new RelativeSpeedEvent(1_100, "front-radar", 0.5, "front-1"),
                    new TTCEvent(1_100, "front-radar", 0.0, "front-1"));
                assertNotNull(decision.getAction());
            }
        });
    }

    @Test
    @Timeout(10)
    void listenersReceiveRuleAndFactEvents() {
        try (DroolsTestHarness harness = new DroolsTestHarness(90.0)) {
            harness.cycle(1_000, 90.0, new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50"));
            var timeline = harness.session().getTrace().snapshot();
            assertTrue(timeline.stream().anyMatch(e -> e.getType() == TimelineEntryType.RULE_FIRED));
            assertTrue(timeline.stream().anyMatch(e -> e.getType() == TimelineEntryType.FACT_INSERTED || e.getType() == TimelineEntryType.FACT_UPDATED));
        }
    }

    @Test
    @Timeout(10)
    void queryReturnsCurrentDecision() {
        try (DroolsTestHarness harness = new DroolsTestHarness(85.0)) {
            DrivingDecision decision = harness.cycle(1_000, 85.0, new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50"));
            var results = harness.session().getKieSession().getQueryResults("Current Driving Decision");
            assertTrue(results.size() > 0);
            var row = results.iterator().next();
            DrivingDecision queried = (DrivingDecision) row.get("$decision");
            assertEquals(decision.getAction(), queried.getAction());
        }
    }

    @Test
    @Timeout(10)
    void highSeverityObstacleCloseAheadInTheVehiclesLaneIsAnEmergency() {
        try (DroolsTestHarness harness = new DroolsTestHarness(50.0)) {
            harness.maneuver(LaneChangeState.CHANGING, Lane.CENTER.centreMeters());
            DrivingDecision decision = harness.cycle(
                1_000,
                50.0,
                new ObstacleDetectedEvent(1_000, "camera", 5.0, "obstacle-1", HazardSeverity.HIGH, Lane.CENTER)
            );
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction());
            assertEquals("Emergency braking for high severity obstacle at critical distance", decision.getSpeedSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void highSeverityObstacleIsNotAnEmergencyOnceTheVehicleHasPhysicallyLeftItsLane() {
        try (DroolsTestHarness harness = new DroolsTestHarness(50.0)) {
            // Committed lane still CENTER (it settles after the actuator), but the vehicle is
            // already over the line into LEFT: the obstacle in CENTER is no longer in its way.
            harness.maneuver(LaneChangeState.CHANGING, Lane.LEFT.centreMeters());
            DrivingDecision decision = harness.cycle(
                1_000,
                50.0,
                new ObstacleDetectedEvent(1_000, "camera", 5.0, "obstacle-1", HazardSeverity.HIGH, Lane.CENTER)
            );
            assertNotEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction(),
                "the vehicle has left the obstacle's lane, so the obstacle is not critical");
        }
    }

    // Pedestrian at 60 m: the 2.0 m/s2 curve allows 46.7 km/h, the 1.4 m/s2 curve 39.0 km/h.
    @Test
    @Timeout(10)
    void brakingForAPedestrianContinuesBetweenTheCurvesThenHoldsTheSpeed() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision start = harness.cycle(1_000, 60.0,
                new PedestrianDetectedEvent(1_000, "camera", 60.0, "ped-1", true));
            assertEquals(DrivingAction.BRAKE, start.getSpeedAction());
            assertEquals("Brake for pedestrian proximity", start.getSpeedSourceRule());

            DrivingDecision between = harness.cycle(1_200, 44.0,
                new PedestrianDetectedEvent(1_200, "camera", 58.0, "ped-1", true));
            assertEquals(DrivingAction.BRAKE, between.getSpeedAction(),
                "between the curves the vehicle keeps braking once it started");

            DrivingDecision under = harness.cycle(1_400, 36.0,
                new PedestrianDetectedEvent(1_400, "camera", 57.0, "ped-1", true));
            assertEquals(DrivingAction.MAINTAIN_SPEED, under.getSpeedAction(),
                "under the lower curve, after braking, the vehicle holds its speed and never speeds up again");
            assertEquals("Brake for pedestrian proximity", under.getSpeedSourceRule());
            assertEquals(36.0, under.getRecommendedSpeedKmh(), 0.01);
        }
    }

    @Test
    @Timeout(10)
    void aVehicleBetweenTheCurvesThatWasNotBrakingHoldsItsSpeed() {
        try (DroolsTestHarness harness = new DroolsTestHarness(44.0)) {
            DrivingDecision decision = harness.cycle(1_000, 44.0,
                new PedestrianDetectedEvent(1_000, "camera", 58.0, "ped-1", true));
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getSpeedAction());
            assertEquals("Brake for pedestrian proximity", decision.getSpeedSourceRule());
            assertEquals(44.0, decision.getRecommendedSpeedKmh(), 0.01);
        }
    }

    // Ego at 40 km/h in CENTER with an obstacle 40 m ahead and the left side clear: the
    // avoidance goes left only if the ego can still follow the vehicle the radar sees there.
    // A 30 km/h vehicle 33 m ahead: after the 3 s lane change only 25 m are left, not enough
    // to slow to 30 km/h 12 m behind it at 2 m/s2. At 60 m there is room.
    private static Object[] obstacleAheadLeftSideClear(double leftVehicleMeters) {
        return new Object[] {
            new ObstacleDetectedEvent(1_000, "camera", 40.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.CENTER),
            new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY, true,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
            new LeftLaneVehicleEvent(1_000, "front-radar", leftVehicleMeters, 10.0 / 3.6, "left-1")
        };
    }

    @Test
    @Timeout(10)
    void noLaneChangeIntoALaneWhoseVehicleAheadIsTooCloseToFollow() {
        try (DroolsTestHarness harness = new DroolsTestHarness(40.0)) {
            DrivingDecision decision = harness.cycle(1_000, 40.0, obstacleAheadLeftSideClear(33.0));
            assertNotEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction(),
                "roomToFollow fails, so safeToChangeLane(LEFT) is not proved and no avoidance goes left");
        }
    }

    @Test
    @Timeout(10)
    void laneChangeIntoALaneWhoseVehicleAheadLeavesRoomToFollow() {
        try (DroolsTestHarness harness = new DroolsTestHarness(40.0)) {
            DrivingDecision decision = harness.cycle(1_000, 40.0, obstacleAheadLeftSideClear(60.0));
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction());
            assertEquals("Avoid obstacle in centre lane by changing left", decision.getLaneSourceRule());
        }
    }
}
