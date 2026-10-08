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
import com.one2drive.projeto1.drools.events.LaneChangeRequestEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.ReducedGripEvent;
import com.one2drive.projeto1.drools.events.ReducedVisibilityEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.KieServices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrivingExpertSystemRulesTest {

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
            assertEquals(DrivingAction.STOP, decision.getAction());
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
