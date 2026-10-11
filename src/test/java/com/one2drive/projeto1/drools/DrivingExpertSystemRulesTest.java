package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.events.AccelerationRequestEvent;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.LaneChangeRequestEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.LeftLaneVehicleEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.ReducedGripEvent;
import com.one2drive.projeto1.drools.events.ReducedVisibilityEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.KieServices;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrivingExpertSystemRulesTest {

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
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(50.0, decision.getRecommendedSpeedKmh(), 0.01);
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
                // 35 m is the closest point at which comfortable braking (2.8 m/s2) can
                // still stop from 50 km/h, so the approach must be a plain reduction.
                new StopSignEvent(1_000, "camera", 35, "stop-1")
            );
            assertEquals(DrivingAction.REDUCE_SPEED, approach.getAction());

            DrivingDecision near = harness.cycle(
                2_000,
                4.0,
                new StopSignEvent(2_000, "camera", 3.0, "stop-1")
            );
            assertEquals(DrivingAction.STOP, near.getAction());

            harness.cycle(2_200, 0.0, new StopSignEvent(2_200, "camera", 2.5, "stop-1"));
            DrivingDecision proceed = harness.cycle(3_600, 0.0, new StopSignEvent(3_600, "camera", 1.0, "stop-1"));
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
                new StopSignEvent(1_000, "camera", 25, "stop-1")
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
            assertEquals(DrivingAction.KEEP_LANE, decision.getAction());
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
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            DrivingDecision decision = harness.cycle(
                1_000,
                60.0,
                new BarrierDetectedEvent(1_000, "camera", 22.0, "bar-1", false)
            );
            assertEquals(DrivingAction.STOP, decision.getAction());
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
            DrivingDecision decision = harness.cycle(
                1_000,
                88.0,
                new ReducedVisibilityEvent(1_000, "environment", 0.55),
                new ReducedGripEvent(1_000, "environment", 0.55)
            );
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertTrue(decision.getRecommendedSpeedKmh() <= 50.0);
        }
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
}
