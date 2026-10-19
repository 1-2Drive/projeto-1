package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.PedestrianCrossingState;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RoadMarkingType;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.LeftLaneVehicleEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrologRulesTest {
    /** Stop sign events measure to the sign; the vehicle stops at the line this far before it. */
    private static final double STOP_LINE = com.one2drive.projeto1.decision.StoppingPolicy.STOP_SIGN_OFFSET_METERS;

    @Test
    @Timeout(10)
    void speedLimitTriggersReduceSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 90.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50"));
            // 20 m before the sign the limit is still pending: the vehicle slows towards it,
            // and it only becomes the limit in force at the sign.
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertTrue(decision.getSourceRule().startsWith("Slow down in time for the speed limit ahead"));
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
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 60.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 30, 110, "sign-30"));
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(60.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void sessionSeedLimitAloneIsNotATargetToSpeedUpTo() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 60.0);
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(60.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void signAboveCurrentSpeedBecomesTheTargetToBuildUpTo() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 60.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 90, 80, "sign-90"));
            assertEquals(60.0, decision.getRecommendedSpeedKmh(), 0.01,
                "a higher limit ahead is not a target before its sign");

            harness.moveTo(80.0);
            decision = harness.cycle(1_200, 60.0);
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(90.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Maintain speed when compliant with detected limit", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void limitPersistsAfterTheSignIsNoLongerSeen() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 70.0, new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50"));
            harness.moveTo(20.0);
            harness.cycle(1_200, 70.0);
            DrivingDecision decision = harness.cycle(5_000, 70.0);
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(50.0, decision.getRecommendedSpeedKmh(), 0.01);
        }
    }

    @Test
    @Timeout(10)
    void clearRoadBuildsBackUpToTheCruiseSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness(50.0)) {
            DrivingDecision decision = harness.cycle(1_000, 30.0);
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(50.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Build back up to the scenario cruise speed when no sign governs the road",
                decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void redLightTriggersStop() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 60.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 18, "light-1"));
            assertTrue(decision.getAction() == DrivingAction.STOP
                || decision.getAction() == DrivingAction.EMERGENCY_BRAKE);
        }
    }

    @Test
    @Timeout(10)
    void yellowFarAwayCommitsToStopAndTheCommitmentSurvivesTheRed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // 50 km/h needs about 59 m for a comfortable stop at the line: 100 m is room enough.
            DrivingDecision decision = harness.cycle(1_000, 50.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 100, "light-1"));
            assertTrue(factPresent(harness, "light_commitment(light-1,stop)"));
            decision = harness.cycle(1_100, 50.0,
                new TrafficLightEvent(1_100, "camera", TrafficLightState.YELLOW, 55, "light-1"));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            decision = harness.cycle(1_200, 30.0,
                new TrafficLightEvent(1_200, "camera", TrafficLightState.RED, 30, "light-1"));
            assertEquals("Red traffic light progressive approach (brake)", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void yellowTooCloseCommitsToProceedWithoutBraking() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 50.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.YELLOW, 30, "light-1"));
            assertTrue(factPresent(harness, "light_commitment(light-1,proceed)"));
            DrivingDecision decision = harness.cycle(1_100, 50.0,
                new TrafficLightEvent(1_100, "camera", TrafficLightState.RED, 28, "light-1"));
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction(),
                "a vehicle that committed to drive on at yellow keeps going when it turns red");
        }
    }

    @Test
    @Timeout(10)
    void greenReleasesAStoppedVehicle() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 0.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.GREEN, 20, "light-1"));
            assertEquals(DrivingAction.RESUME, decision.getAction());
            assertEquals(30.0, decision.getRecommendedSpeedKmh(), 0.01);
        }
    }

    @Test
    @Timeout(10)
    void lightIsForgottenOnceNoLongerSeen() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 50.0, new TrafficLightEvent(1_000, "camera", TrafficLightState.RED, 60, "light-1"));
            DrivingDecision decision = harness.cycle(1_500, 50.0);
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianTriggersBraking() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 50.0,
                new PedestrianDetectedEvent(1_000, "camera", 18.0, "ped-1", true));
            assertTrue(decision.getAction() == DrivingAction.BRAKE || decision.getAction() == DrivingAction.STOP);
        }
    }

    @Test
    @Timeout(10)
    void pedestrianExtremelyCloseTriggersEmergencyBrake() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 30.0,
                new PedestrianDetectedEvent(1_000, "camera", 5.0, "ped-1", false, PedestrianCrossingState.CROSSING));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
            assertTrue(decision.getEvidence().stream().anyMatch(line -> line.contains("pedestrian_conflict(ped-1)")),
                "the evidence names the intermediate conclusion and the rule behind it: " + decision.getEvidence());
        }
    }

    @Test
    @Timeout(10)
    void pedestrianNotInConflictIsOnlyTracked() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 50.0,
                new PedestrianDetectedEvent(1_000, "camera", 15.0, "ped-1", false, PedestrianCrossingState.WAITING));
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void ttcCriticalTriggersEmergency() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 90.0,
                new TTCEvent(700, "radar", 2.3, "front-1"),
                new TTCEvent(850, "radar", 2.1, "front-1"),
                new TTCEvent(980, "radar", 1.9, "front-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
            assertEquals("Emergency brake when persistent TTC is critical", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void isolatedTtcSampleDoesNotTriggerPersistentRule() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 90.0, new TTCEvent(1_000, "radar", 2.2, "front-1"));
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void repeatedTtcInsideWindowTriggersPersistentEmergency() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 85.0, new TTCEvent(1_000, "radar", 2.4, "front-1"));
            harness.cycle(1_400, 85.0, new TTCEvent(1_400, "radar", 2.3, "front-1"));
            DrivingDecision decision = harness.cycle(1_700, 85.0, new TTCEvent(1_700, "radar", 2.2, "front-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void ttcSamplesOutsideTheWindowDoNotCount() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 85.0, new TTCEvent(1_000, "radar", 2.4, "front-1"));
            harness.cycle(1_400, 85.0, new TTCEvent(1_400, "radar", 2.3, "front-1"));
            DrivingDecision decision = harness.cycle(3_500, 85.0, new TTCEvent(3_500, "radar", 2.2, "front-1"));
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void immediateTtcTriggersEmergency() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 90.0, new TTCEvent(1_000, "radar", 1.0, "front-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
            assertEquals("Emergency brake when TTC is immediately critical", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void conflictGreenLightAndPedestrianPedestrianWins() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 50.0,
                new TrafficLightEvent(1_000, "camera", TrafficLightState.GREEN, 20.0, "light-1"),
                new PedestrianDetectedEvent(1_000, "camera", 14.0, "ped-1", true));
            assertTrue(decision.getAction() == DrivingAction.STOP || decision.getAction() == DrivingAction.BRAKE);
        }
    }

    @Test
    @Timeout(10)
    void stopSignTriggersStopSequence() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // 35 m is the closest point at which comfortable braking (2.8 m/s2) can still
            // stop from 50 km/h, so the approach must be a plain reduction.
            DrivingDecision approach = harness.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", STOP_LINE + 35, "stop-1"));
            assertEquals(DrivingAction.REDUCE_SPEED, approach.getAction());

            DrivingDecision near = harness.cycle(2_000, 4.0, new StopSignEvent(2_000, "camera", STOP_LINE + 3.0, "stop-1"));
            assertEquals(DrivingAction.STOP, near.getAction());

            harness.cycle(2_200, 0.0, new StopSignEvent(2_200, "camera", STOP_LINE + 2.5, "stop-1"));
            assertTrue(factPresent(harness, "stop_hold_started(stop-1,2200)"), "the stop timer starts at rest");
            DrivingDecision stillHeld = harness.cycle(3_000, 0.0, new StopSignEvent(3_000, "camera", STOP_LINE + 2.5, "stop-1"));
            assertEquals(DrivingAction.STOP, stillHeld.getAction(), "1.2 s at rest are required");

            DrivingDecision proceed = harness.cycle(3_600, 0.0, new StopSignEvent(3_600, "camera", STOP_LINE + 1.0, "stop-1"));
            assertEquals(DrivingAction.CONTINUE, proceed.getAction());
            assertEquals("Proceed after stop sign condition satisfied", proceed.getSourceRule());
            assertEquals(25.0, proceed.getRecommendedSpeedKmh(), 0.01);
        }
    }

    @Test
    @Timeout(10)
    void stopSignTooCloseForComfortableBrakingTriggersEmergencyBrake() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision approach = harness.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", STOP_LINE + 25, "stop-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, approach.getAction());
        }
    }

    @Test
    @Timeout(10)
    void stopSignEmergencyBrakeIsHeldUntilThereIsRoomAgain() {
        // At 40 km/h and 22 m comfortable braking ends 2 m past the margin: a reduction for a
        // vehicle arriving here, but a vehicle already braking hard for the sign keeps doing so.
        try (PrologTestHarness fresh = new PrologTestHarness()) {
            DrivingDecision decision = fresh.cycle(1_000, 40.0, new StopSignEvent(1_000, "camera", STOP_LINE + 22, "stop-1"));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
        }
        try (PrologTestHarness braking = new PrologTestHarness()) {
            assertEquals(DrivingAction.EMERGENCY_BRAKE,
                braking.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", STOP_LINE + 25, "stop-1")).getAction());
            DrivingDecision decision = braking.cycle(1_100, 40.0, new StopSignEvent(1_100, "camera", STOP_LINE + 22, "stop-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void stopSignIsForgottenOnceNoLongerSeen() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", STOP_LINE + 35, "stop-1"));
            DrivingDecision decision = harness.cycle(2_600, 50.0);
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void frontVehicleTooCloseTriggersBrake() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 50.0,
                new FrontDistanceEvent(1_000, "front-radar", 10.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 3.0, "front-1"));
            assertEquals(DrivingAction.BRAKE, decision.getAction());
            assertEquals("Brake when front vehicle distance low", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void closingOnStationaryVehicleFollowsTheSafeDistanceCurve() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // 60 km/h, stationary vehicle 40 m ahead: 28 m of room before the 12 m gap.
            DrivingDecision decision = harness.cycle(1_000, 60.0,
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 60.0 / 3.6, "front-1"));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals("Keep safe following distance behind front vehicle (reduce)", decision.getSourceRule());
            assertEquals(Math.sqrt(2.0 * 2.0 * 28.0) * 3.6, decision.getRecommendedSpeedKmh(), 0.1);
        }
    }

    @Test
    @Timeout(10)
    void stoppedBehindStationaryVehicleHoldsTheStop() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 0.0,
                new FrontDistanceEvent(1_000, "front-radar", 12.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1"));
            assertEquals(DrivingAction.STOP, decision.getAction());
            assertEquals("Keep safe following distance behind front vehicle (stop)", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void relativeSpeedArrivingBeforeTheDistanceStillCounts() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // Event order within a tick must not matter: only the distance adopts a target.
            DrivingDecision decision = harness.cycle(1_000, 60.0,
                new RelativeSpeedEvent(1_000, "front-radar", 60.0 / 3.6, "front-1"),
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void distanceSamplesOfTheSameVehicleStillFormATrend() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 40.0, new FrontDistanceEvent(1_000, "front-radar", 48.0, "front-1"));
            harness.cycle(1_100, 40.0, new FrontDistanceEvent(1_100, "front-radar", 45.0, "front-1"));
            DrivingDecision decision = harness.cycle(1_200, 40.0,
                new FrontDistanceEvent(1_200, "front-radar", 42.0, "front-1"));
            assertEquals("Approaching trend from sliding distance window", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void distanceSamplesOfDifferentVehiclesAreNotCombinedIntoOneTrend() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 40.0, new FrontDistanceEvent(1_000, "front-radar", 48.0, "front-1"));
            harness.cycle(1_100, 40.0, new FrontDistanceEvent(1_100, "front-radar", 47.5, "front-1"));
            DrivingDecision decision = harness.cycle(1_200, 40.0,
                new FrontDistanceEvent(1_200, "front-radar", 40.0, "front-2"));
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void frontVehicleIsForgottenAfterMissedRadarReadings() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 50.0,
                new FrontDistanceEvent(1_000, "front-radar", 10.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 3.0, "front-1"));
            DrivingDecision decision = harness.cycle(1_300, 50.0);
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void noTrendWhileChangingLane() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var session = harness.session();
            var context = new com.one2drive.projeto1.decision.DecisionContext();
            for (long t : new long[]{1_000, 1_100}) {
                session.cycle(context, t, 40.0, com.one2drive.projeto1.domain.Lane.RIGHT,
                    com.one2drive.projeto1.domain.LaneChangeState.CHANGING,
                    java.util.List.of(new FrontDistanceEvent(t, "front-radar", 48.0 - (t - 1_000) / 100 * 3.0, "front-1")), null);
            }
            PrologCycleResult result = session.cycle(context, 1_200, 40.0, com.one2drive.projeto1.domain.Lane.RIGHT,
                com.one2drive.projeto1.domain.LaneChangeState.CHANGING,
                java.util.List.of(new FrontDistanceEvent(1_200, "front-radar", 42.0, "front-1")), null);
            assertTrue(result.proposals().stream()
                    .noneMatch(p -> p.rule().equals("Approaching trend from sliding distance window")),
                "the trend only counts while the vehicle keeps its lane: " + result.proposals());
        }
    }

    @Test
    @Timeout(10)
    void overtakeIsALaneChangeWithoutTargetSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 60.0,
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY));
            // Updated expectation: the lane change is arbitrated in its own (lateral) channel,
            // so the decision's recommended speed is the speed channel's (the following rules no
            // longer step aside for the overtake); the overtake's own proposal carries no speed.
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getAction());
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction());
            assertEquals("Overtake slow front vehicle via left lane", decision.getLaneSourceRule());
            assertTrue(decision.getProposals().stream()
                    .filter(p -> p.getSourceRule().equals("Overtake slow front vehicle via left lane"))
                    .allMatch(p -> Double.isNaN(p.getRecommendedSpeedKmh())),
                "a lane change carries no target speed");
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getSpeedAction(),
                "the speed channel keeps slowing for the vehicle ahead in the same cycle");
            assertTrue(factPresent(harness, "overtaking_result(true,center,false,1000,front-1)"),
                "the working memory shows the overtake the cycle ended with");
        }
    }

    @Test
    @Timeout(10)
    void vehicleFollowedAtItsSpeedIsOvertakenWhenTheLeftLaneIsClear() {
        // No scenario cruise speed: the desired speed is the limit in force (120 km/h). Following
        // a 40 km/h vehicle 20 m ahead at its speed, in CENTER, with the left lane clear far beyond
        // it: the closing-in overtake cannot start (not closing), so this is the exit from following.
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 40.0,
                new FrontDistanceEvent(1_000, "front-radar", 20.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 0.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getAction());
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction());
            assertEquals("Overtake the vehicle being followed", decision.getLaneSourceRule());
            assertTrue(factPresent(harness, "overtaking_result(true,center,false,1000,front-1)"),
                "the working memory shows the overtake the cycle ended with");
        }
    }

    @Test
    @Timeout(10)
    void followingRulesKeepSlowingWhileTheOvertakeStarts() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // Updated expectation: the following rules no longer step aside in the cycle the
            // overtake starts; the speed channel slows down in that cycle and in the next one.
            DrivingDecision first = harness.cycle(1_000, 60.0,
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, first.getLaneAction());
            assertEquals(DrivingAction.REDUCE_SPEED, first.getSpeedAction(),
                "the vehicle slows for the vehicle ahead in the very cycle the overtake starts");
            DrivingDecision next = harness.cycle(1_100, 60.0,
                new FrontDistanceEvent(1_100, "front-radar", 39.5, "front-1"),
                new RelativeSpeedEvent(1_100, "front-radar", 5.0, "front-1"),
                new LaneOccupancyEvent(1_100, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.REDUCE_SPEED, next.getSpeedAction(),
                "while the vehicle has not left its lane it keeps slowing for the vehicle ahead");
        }
    }

    /** One Prolog cycle for a vehicle whose markings, lane change and adjacent lanes a test sets. */
    @Test
    @Timeout(10)
    void highSeverityObstacleCloseAheadInTheVehiclesLaneIsAnEmergency() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 50.0, 0.0, Lane.CENTER, LaneChangeState.CHANGING,
                Lane.LEFT, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, false, false, false);
            DrivingDecision decision = decide(harness, vehicle,
                new ObstacleDetectedEvent(1_000, "camera", 5.0, "obstacle-1", HazardSeverity.HIGH, Lane.CENTER));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction());
            assertEquals("Emergency braking for high severity obstacle at critical distance", decision.getSpeedSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void highSeverityObstacleIsNotAnEmergencyOnceTheVehicleHasPhysicallyLeftItsLane() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // Committed lane still CENTER (it settles after the actuator), but the vehicle is
            // already over the line into LEFT: the obstacle in CENTER is no longer in its way.
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 50.0, 0.0, Lane.CENTER, LaneChangeState.CHANGING,
                Lane.LEFT, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, false, false, false)
                .physicallyIn(Lane.LEFT);
            DrivingDecision decision = decide(harness, vehicle,
                new ObstacleDetectedEvent(1_000, "camera", 5.0, "obstacle-1", HazardSeverity.HIGH, Lane.CENTER));
            assertNotEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction(),
                "the vehicle has left the obstacle's lane, so the obstacle is not critical");
        }
    }

    // Pedestrian at 60 m: the 2.0 m/s2 curve allows 46.7 km/h, the 1.4 m/s2 curve 39.0 km/h.
    @Test
    @Timeout(10)
    void brakingForAPedestrianContinuesBetweenTheCurvesThenHoldsTheSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision start = decide(harness,
                PrologKnowledgeSession.VehicleInput.keepingLane(1_000, 60.0, Lane.CENTER),
                new PedestrianDetectedEvent(1_000, "camera", 60.0, "ped-1", true));
            assertEquals(DrivingAction.BRAKE, start.getSpeedAction());
            assertEquals("Brake for pedestrian proximity (brake)", start.getSpeedSourceRule());

            DrivingDecision between = decide(harness,
                PrologKnowledgeSession.VehicleInput.keepingLane(1_200, 44.0, Lane.CENTER),
                new PedestrianDetectedEvent(1_200, "camera", 58.0, "ped-1", true));
            assertEquals(DrivingAction.BRAKE, between.getSpeedAction(),
                "between the curves the vehicle keeps braking once it started");

            DrivingDecision under = decide(harness,
                PrologKnowledgeSession.VehicleInput.keepingLane(1_400, 36.0, Lane.CENTER),
                new PedestrianDetectedEvent(1_400, "camera", 57.0, "ped-1", true));
            assertEquals(DrivingAction.MAINTAIN_SPEED, under.getSpeedAction(),
                "under the lower curve, after braking, the vehicle holds its speed and never speeds up again");
            assertEquals("Brake for pedestrian proximity (hold speed)", under.getSpeedSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void aVehicleBetweenTheCurvesThatWasNotBrakingHoldsItsSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = decide(harness,
                PrologKnowledgeSession.VehicleInput.keepingLane(1_000, 44.0, Lane.CENTER),
                new PedestrianDetectedEvent(1_000, "camera", 58.0, "ped-1", true));
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getSpeedAction());
            assertEquals("Brake for pedestrian proximity (hold speed)", decision.getSpeedSourceRule());
        }
    }

    // Ego at 40 km/h in CENTER with an obstacle 40 m ahead and the left side clear: the
    // avoidance goes left only if the ego can still follow the vehicle the radar sees there
    // (room_to_follow/1 in safe_to_change_lane/1). A 30 km/h vehicle 33 m ahead is too
    // close after the 3 s lane change; at 60 m there is room.
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
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = decide(harness,
                PrologKnowledgeSession.VehicleInput.keepingLane(1_000, 40.0, Lane.CENTER),
                obstacleAheadLeftSideClear(33.0));
            assertNotEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction(),
                "room_to_follow fails, so safe_to_change_lane(left) is not proved and no avoidance goes left");
        }
    }

    @Test
    @Timeout(10)
    void laneChangeIntoALaneWhoseVehicleAheadLeavesRoomToFollow() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = decide(harness,
                PrologKnowledgeSession.VehicleInput.keepingLane(1_000, 40.0, Lane.CENTER),
                obstacleAheadLeftSideClear(60.0));
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getLaneAction());
            assertEquals("Avoid obstacle in centre lane by changing left", decision.getLaneSourceRule());
        }
    }

    private static DrivingDecision decide(PrologTestHarness harness, PrologKnowledgeSession.VehicleInput vehicle,
                                          Object... events) {
        DecisionContext context = new DecisionContext();
        context.beginCycle(vehicle.timestampMillis(), vehicle.speedKmh());
        harness.session().cycle(context, vehicle, java.util.List.of(events), null);
        DrivingDecision decision = new DrivingDecision();
        context.applyTo(decision);
        return decision;
    }

    /** A cycle with the vehicle at {@code positionMeters} along the road. */
    private static PrologCycleResult cycleAt(PrologTestHarness harness, DecisionContext context, long timestamp,
                                             double speedKmh, double positionMeters, Object... events) {
        context.beginCycle(timestamp, speedKmh);
        return harness.session().cycle(context,
            PrologKnowledgeSession.VehicleInput.keepingLane(timestamp, speedKmh, Lane.CENTER).at(positionMeters),
            java.util.List.of(events), null);
    }

    private static DrivingDecision decisionOf(DecisionContext context) {
        DrivingDecision decision = new DrivingDecision();
        context.applyTo(decision);
        return decision;
    }

    private static SpeedLimitDetectedEvent limitSign(long timestamp, int limitKmh, double distanceMeters) {
        return new SpeedLimitDetectedEvent(timestamp, "camera", limitKmh, distanceMeters, "limit-1");
    }

    @Test
    @Timeout(10)
    void speedLimitAheadIsPendingUntilTheVehicleReachesTheSign() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            PrologCycleResult result = cycleAt(harness, context, 1_000, 50.0, 100.0, limitSign(1_000, 30, 100.0));
            assertEquals(120.0, result.speedLimitKmh(), 0.01, "a sign ahead is not in force yet");
            result = cycleAt(harness, context, 1_100, 30.0, 200.0, limitSign(1_100, 30, 0.0));
            assertEquals(30.0, result.speedLimitKmh(), 0.01, "the limit applies from the sign on");
        }
    }

    @Test
    @Timeout(10)
    void vehicleSlowsDownInTimeForALowerLimitAhead() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            // Far from the sign the approach speed is above the current speed: nothing slows yet.
            cycleAt(harness, context, 1_000, 90.0, 0.0, limitSign(1_000, 50, 150.0));
            assertTrue(decisionOf(context).getSourceRule() == null
                    || !decisionOf(context).getSourceRule().startsWith("Slow down in time for the speed limit ahead"),
                "150 m from a 50 km/h sign, 90 km/h is still below the approach speed");
            // 40 m from the sign: sqrt((50/3.6)^2 + 2*2*(40-8)) * 3.6 = 78.9 km/h.
            cycleAt(harness, context, 1_100, 90.0, 110.0, limitSign(1_100, 50, 40.0));
            DrivingDecision decision = decisionOf(context);
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(com.one2drive.projeto1.decision.StoppingPolicy.approachSpeedKmh(50, 40.0),
                decision.getRecommendedSpeedKmh(), 0.01);
            assertTrue(decision.getSourceRule().startsWith("Slow down in time for the speed limit ahead"));
            assertEquals("Speed limit 50 km/h ahead; slowing to reach it at the sign", decision.getReason());
        }
    }

    @Test
    @Timeout(10)
    void pendingLimitIsDroppedOneSecondAfterTheCameraLastSawIt() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            cycleAt(harness, context, 1_000, 90.0, 0.0, limitSign(1_000, 50, 40.0));
            assertTrue(decisionOf(context).getSourceRule().startsWith("Slow down in time for the speed limit ahead"));
            PrologCycleResult result = cycleAt(harness, context, 2_100, 90.0, 10.0);
            assertEquals(120.0, result.speedLimitKmh(), 0.01, "a sign no longer seen never takes effect");
            assertFalse(result.workingMemory().stream().anyMatch(fact -> fact.fact().startsWith("pending_speed_limit(")));
        }
    }

    @Test
    @Timeout(10)
    void closedBarrierIsAvoidedThroughALaneTheSensorReportsClear() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 40.0, 0.0, Lane.CENTER, LaneChangeState.IDLE,
                Lane.CENTER, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, true, false, false);
            // The left side is still the pessimistic seed (occupied); the blind-spot sensor reports
            // the right side clear. The rules decide from that perception, not from the world.
            DrivingDecision decision = decide(harness, vehicle,
                new BarrierDetectedEvent(1_000, "camera", 40.0, "barrier-1", false, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.CHANGE_LANE_RIGHT, decision.getAction());
            assertEquals("Avoid closed barrier by changing right from centre when safe", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void closedBarrierFarAheadIsApproachedOnTheBrakingCurve() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 90.0, 0.0, Lane.CENTER, LaneChangeState.IDLE,
                Lane.CENTER, RoadMarkingType.DASHED, RoadMarkingType.DASHED, true, true, false, false);
            // Both sides clear: the mandatory stop does not apply, the approach rule brakes to
            // the anticipation curve (79 km/h with 120 m of room) as in Drools.
            DrivingDecision decision = decide(harness, vehicle,
                new BarrierDetectedEvent(1_000, "camera", 130.0, "barrier-1", false, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.BRAKE, decision.getSpeedAction());
            assertEquals("Stop before closed barrier while still in its lane (brake)", decision.getSpeedSourceRule());
            assertEquals(78.9, decision.getRecommendedSpeedKmh(), 0.5);
        }
    }

    @Test
    @Timeout(10)
    void closedBarrierTooCloseToGoRoundIsBrakedForInsteadOfAvoided() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 60.0, 0.0, Lane.CENTER, LaneChangeState.IDLE,
                Lane.CENTER, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, true, false, false);
            // 25 m at 60 km/h: a lane change would not leave the lane before the barrier.
            DrivingDecision decision = decide(harness, vehicle,
                new BarrierDetectedEvent(1_000, "camera", 25.0, "barrier-1", false, Lane.CENTER),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY));
            assertFalse(decision.getLaneAction().isLaneManeuver(), "too close to go round: " + decision.getLaneSourceRule());
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction());
            assertEquals("Stop before closed barrier while still in its lane (emergency)", decision.getSpeedSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void severePotholeTooCloseForComfortableSlowingIsBrakedHardFor() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 90.0, 0.0, Lane.CENTER, LaneChangeState.IDLE,
                Lane.CENTER, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, false, false, false);
            DrivingDecision decision = decide(harness, vehicle,
                new PotholeDetectedEvent(1_000, "camera", 40.0, "pth-1", HazardSeverity.HIGH, Lane.CENTER));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction());
            assertEquals("Pothole ahead reduce speed (emergency)", decision.getSpeedSourceRule());
            // The same geometry with a MEDIUM pothole is only slowed for, with the situation explained.
            DrivingDecision lighter = decide(harness, vehicle,
                new PotholeDetectedEvent(1_100, "camera", 40.0, "pth-1", HazardSeverity.MEDIUM, Lane.CENTER));
            assertEquals(DrivingAction.REDUCE_SPEED, lighter.getSpeedAction());
            assertEquals("Pothole too close to go round; crossing it slowly (Art. 24 CE)", lighter.getReason());
        }
    }

    @Test
    @Timeout(10)
    void fastEgoBrakesHardRatherThanPassASlowerLeftLaneVehicleOnItsRight() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 90.0, 0.0, Lane.RIGHT, LaneChangeState.IDLE,
                Lane.RIGHT, RoadMarkingType.DASHED, RoadMarkingType.DASHED, true, false, false, false);
            // 90 km/h, 30 km/h vehicle 40 m ahead on the left: comfortable braking would gain 58 m.
            DrivingDecision decision = decide(harness, vehicle,
                new LeftLaneVehicleEvent(1_000, "front-radar", 40.0, 60.0 / 3.6, "left-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getSpeedAction());
            assertEquals("Do not pass a slower left lane vehicle on its right (emergency)", decision.getSpeedSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void continuousLineBesideTheEgoLaneStopsBeforeTheObstacle() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // Updated expectation: the line the ego in RIGHT would cross towards CENTER is the
            // right marking (WorldState.getMarkingBetween), so it is the continuous one here; the
            // blind-spot occupancy reports the free left side as not safe to enter across it.
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 30.0, 0.0, Lane.RIGHT, LaneChangeState.IDLE,
                Lane.RIGHT, RoadMarkingType.DASHED, RoadMarkingType.CONTINUOUS, false, false, false, false);
            DrivingDecision decision = decide(harness, vehicle,
                new ObstacleDetectedEvent(1_000, "camera", 40.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.RIGHT),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY, false,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
            // At 30 km/h the obstacle at 40 m is still above the comfortable braking curve
            // (46.7 km/h stops in the 30 m left), so the speed is held, not yet reduced.
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction(),
                "the left lane is free but the continuous line forbids it");
            assertEquals(DrivingAction.KEEP_LANE, decision.getLaneAction());
            assertEquals("Stop before obstacle when lane change prohibited by continuous line (hold)",
                decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void ongoingLaneChangeKeepsItsDecision() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 50.0, 0.0, Lane.CENTER, LaneChangeState.CHANGING,
                Lane.LEFT, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, false, false, false);
            DrivingDecision decision = decide(harness, vehicle);
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getAction());
            assertEquals("Maintain ongoing lane change left avoidance", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void sensorEventsAppearOnTheTimelineAsDroolsDescribesThem() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            com.one2drive.projeto1.config.SimulationConfig config = com.one2drive.projeto1.config.SimulationConfig.defaultConfig();
            try (PrologBackend backend = new PrologBackend(config, d -> { }, 0.0)) {
                var event = new ObstacleDetectedEvent(1_000, "camera", 30.0, "obstacle-1", HazardSeverity.HIGH, Lane.CENTER);
                backend.insertEvent(event);
                String expected = "FACT INSERTED " + com.one2drive.projeto1.drools.FactDescription.describe(event);
                assertTrue(backend.getTrace().snapshot().stream().anyMatch(e -> e.getMessage().equals(expected)),
                    "expected the Drools fact description on the timeline: " + expected);
            }
        }
    }

    @Test
    @Timeout(10)
    void rainAdjustsSafeSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // Fuzzy weather controller: visibility 0.74 is fair (0.73) and good (0.27), grip 0.9
            // is good, so rain concludes prudent 0.27 and the visibility moderate 0.73.
            DrivingDecision decision = harness.cycle(1_000, 90.0,
                new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.74, 0.9));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(58.05, decision.getRecommendedSpeedKmh(), 0.01,
                "centroid of moderate 0.73 and prudent 0.27");
            assertEquals("Defuzzify weather speed ceiling", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void reducedVisibilityAndGripTriggerConservativeReduction() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            // Updated expectation: the visibility and grip rules read the persistent weather
            // state (updated by the weather event), no longer the 500 ms reduced-visibility and
            // reduced-grip event windows, which both engines now ignore.
            DrivingDecision decision = harness.cycle(1_000, 88.0,
                new WeatherEvent(1_000, "env", WeatherCondition.CLEAR, 0.55, 0.55));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(44.64, decision.getRecommendedSpeedKmh(), 0.01, "centroid of slow 0.75 and moderate 0.25");
            assertEquals("Defuzzify weather speed ceiling", decision.getSourceRule());

            DrivingDecision later = harness.cycle(1_600, 88.0);
            assertEquals(DrivingAction.REDUCE_SPEED, later.getAction(),
                "the weather is a persistent condition: the ceiling must not lapse between sensor readings");
            assertEquals(44.64, later.getRecommendedSpeedKmh(), 0.01);
        }
    }

    @Test
    @Timeout(10)
    void rainAndPersistentLowTtcBrake() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 80.0, new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.7, 0.65));
            DrivingDecision decision = harness.cycle(1_300, 80.0,
                new TTCEvent(1_200, "radar", 2.9, "front-1"),
                new TTCEvent(1_290, "radar", 2.8, "front-1"));
            assertEquals(DrivingAction.BRAKE, decision.getAction());
            assertEquals(15.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Persistent low TTC in rain increases conservatism", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void moveOffIntentSurvivesBetweenCyclesUntilAtSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision start = harness.cycle(1_000, 0.0);
            assertEquals(DrivingAction.CONTINUE, start.getAction());
            assertEquals(30.0, start.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Move off when stopped and nothing holds the vehicle", start.getSourceRule());
            // Above 3 km/h the start rule no longer applies; the kept intent still moves it off.
            DrivingDecision moving = harness.cycle(1_100, 15.0);
            assertEquals("Move off when stopped and nothing holds the vehicle", moving.getSourceRule());
            assertTrue(factPresent(harness, "move_off_state(true,1000)"));
            DrivingDecision atSpeed = harness.cycle(1_200, 29.5);
            assertEquals("Default maintain speed baseline", atSpeed.getSourceRule());
        }
    }

    private static PrologKnowledgeSession.VehicleInput inCenter(long timestamp, boolean rightLaneBlockedAhead,
                                                                boolean presentationDemo) {
        return new PrologKnowledgeSession.VehicleInput(timestamp, 60.0, 0.0, Lane.CENTER, LaneChangeState.IDLE,
            Lane.CENTER, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, false,
            rightLaneBlockedAhead, presentationDemo);
    }

    @Test
    @Timeout(10)
    void keepRightReturnsToTheRightLaneWhenItIsSafe() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = decide(harness, inCenter(5_000, false, false),
                new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.RETURN_RIGHT, decision.getAction());
            assertEquals("Enforce keep right road law when right lane is safe", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void keepRightWaitsWhileAHazardBlocksTheRightLaneAhead() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = decide(harness, inCenter(5_000, true, false),
                new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY));
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void keepRightIsOffDuringThePresentationDemo() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = decide(harness, inCenter(5_000, false, true),
                new LaneOccupancyEvent(5_000, "blind-spot", Lane.RIGHT, false, Double.POSITIVE_INFINITY));
            assertEquals("Default maintain speed baseline", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void whyNotNamesTheConditionThatFailed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 40.0, new SpeedLimitDetectedEvent(1_000, "camera", 50, 0, "sign-50"));
            assertEquals(java.util.List.of("test(40.0>50+0.1)"),
                harness.session().whyNot("Reduce speed when exceeding detected limit"));
        }
    }

    private static boolean factPresent(PrologTestHarness harness, String fact) {
        return harness.lastResult().workingMemory().stream().anyMatch(f -> f.fact().equals(fact));
    }
}
