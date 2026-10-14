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
import com.one2drive.projeto1.drools.events.ReducedGripEvent;
import com.one2drive.projeto1.drools.events.ReducedVisibilityEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrologRulesTest {

    @Test
    @Timeout(10)
    void speedLimitTriggersReduceSpeed() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 90.0,
                new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50"));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(50.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Reduce speed when exceeding detected limit", decision.getSourceRule());
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
            DrivingDecision approach = harness.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", 35, "stop-1"));
            assertEquals(DrivingAction.REDUCE_SPEED, approach.getAction());

            DrivingDecision near = harness.cycle(2_000, 4.0, new StopSignEvent(2_000, "camera", 3.0, "stop-1"));
            assertEquals(DrivingAction.STOP, near.getAction());

            harness.cycle(2_200, 0.0, new StopSignEvent(2_200, "camera", 2.5, "stop-1"));
            assertTrue(factPresent(harness, "stop_hold_started(stop-1,2200)"), "the stop timer starts at rest");
            DrivingDecision stillHeld = harness.cycle(3_000, 0.0, new StopSignEvent(3_000, "camera", 2.5, "stop-1"));
            assertEquals(DrivingAction.STOP, stillHeld.getAction(), "1.2 s at rest are required");

            DrivingDecision proceed = harness.cycle(3_600, 0.0, new StopSignEvent(3_600, "camera", 1.0, "stop-1"));
            assertEquals(DrivingAction.CONTINUE, proceed.getAction());
            assertEquals("Proceed after stop sign condition satisfied", proceed.getSourceRule());
            assertEquals(25.0, proceed.getRecommendedSpeedKmh(), 0.01);
        }
    }

    @Test
    @Timeout(10)
    void stopSignTooCloseForComfortableBrakingTriggersEmergencyBrake() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision approach = harness.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", 25, "stop-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, approach.getAction());
        }
    }

    @Test
    @Timeout(10)
    void stopSignEmergencyBrakeIsHeldUntilThereIsRoomAgain() {
        // At 40 km/h and 22 m comfortable braking ends 2 m past the margin: a reduction for a
        // vehicle arriving here, but a vehicle already braking hard for the sign keeps doing so.
        try (PrologTestHarness fresh = new PrologTestHarness()) {
            DrivingDecision decision = fresh.cycle(1_000, 40.0, new StopSignEvent(1_000, "camera", 22, "stop-1"));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
        }
        try (PrologTestHarness braking = new PrologTestHarness()) {
            assertEquals(DrivingAction.EMERGENCY_BRAKE,
                braking.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", 25, "stop-1")).getAction());
            DrivingDecision decision = braking.cycle(1_100, 40.0, new StopSignEvent(1_100, "camera", 22, "stop-1"));
            assertEquals(DrivingAction.EMERGENCY_BRAKE, decision.getAction());
        }
    }

    @Test
    @Timeout(10)
    void stopSignIsForgottenOnceNoLongerSeen() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 50.0, new StopSignEvent(1_000, "camera", 35, "stop-1"));
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
            assertEquals(DrivingAction.CHANGE_LANE_LEFT, decision.getAction());
            assertTrue(Double.isNaN(decision.getRecommendedSpeedKmh()), "a lane change carries no target speed");
            assertTrue(factPresent(harness, "overtaking_result(true,center,false,1000,front-1)"),
                "the working memory shows the overtake the cycle ended with");
        }
    }

    @Test
    @Timeout(10)
    void followingRulesStepAsideOnlyInTheCycleTheOvertakeStarts() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            harness.cycle(1_000, 60.0,
                new FrontDistanceEvent(1_000, "front-radar", 40.0, "front-1"),
                new RelativeSpeedEvent(1_000, "front-radar", 5.0, "front-1"),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY));
            DrivingDecision next = harness.cycle(1_100, 60.0,
                new FrontDistanceEvent(1_100, "front-radar", 39.5, "front-1"),
                new RelativeSpeedEvent(1_100, "front-radar", 5.0, "front-1"),
                new LaneOccupancyEvent(1_100, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.REDUCE_SPEED, next.getAction(),
                "while the vehicle has not left its lane it keeps slowing for the vehicle ahead");
        }
    }

    /** One Prolog cycle for a vehicle whose markings, lane change and adjacent lanes a test sets. */
    private static DrivingDecision decide(PrologTestHarness harness, PrologKnowledgeSession.VehicleInput vehicle,
                                          Object... events) {
        DecisionContext context = new DecisionContext();
        context.beginCycle(vehicle.timestampMillis(), vehicle.speedKmh());
        harness.session().cycle(context, vehicle, java.util.List.of(events), null);
        DrivingDecision decision = new DrivingDecision();
        context.applyTo(decision);
        return decision;
    }

    /** A cycle with the radar facts Java reads from the world, as PrologBackend sends them. */
    private static PrologCycleResult radarCycle(PrologTestHarness harness, DecisionContext context, long timestamp,
                                                double speedKmh, PrologKnowledgeSession.RadarInput radar,
                                                Object... events) {
        context.beginCycle(timestamp, speedKmh);
        return harness.session().cycle(context,
            PrologKnowledgeSession.VehicleInput.keepingLane(timestamp, speedKmh, Lane.CENTER), radar,
            java.util.List.of(events), null);
    }

    private static DrivingDecision decisionOf(DecisionContext context) {
        DrivingDecision decision = new DrivingDecision();
        context.applyTo(decision);
        return decision;
    }

    private static PrologKnowledgeSession.RadarInput radarAhead(String id, double distance, double limit) {
        return new PrologKnowledgeSession.RadarInput(id, distance, limit, null);
    }

    @Test
    @Timeout(10)
    void speedRadarAheadBringsTheVehicleDownToItsLimit() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            PrologCycleResult result = radarCycle(harness, context, 1_000, 90.0, radarAhead("radar-1", 200.0, 70.0));
            DrivingDecision decision = decisionOf(context);
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(70.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Decelerate approaching speed radar to comply with legal limit", decision.getSourceRule());
            assertEquals("Radar detects speed-control zone ahead; configured limit 70 km/h", decision.getReason());
            assertTrue(result.radar().active(), "the radar is detected and not yet passed: " + result.radar());
        }
    }

    @Test
    @Timeout(10)
    void compliantVehicleHoldsAtOrBelowTheRadarLimit() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            radarCycle(harness, context, 1_000, 60.0, radarAhead("radar-1", 200.0, 70.0));
            DrivingDecision decision = decisionOf(context);
            assertEquals(DrivingAction.MAINTAIN_SPEED, decision.getAction());
            assertEquals(70.0, decision.getRecommendedSpeedKmh(), 0.01);
            assertEquals("Maintain speed at or below the configured radar limit while approaching", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void passedRadarNoLongerConstrainsTheVehicleEvenForALaterRadar() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            radarCycle(harness, context, 1_000, 90.0, radarAhead("radar-1", 20.0, 70.0));
            PrologCycleResult result = radarCycle(harness, context, 1_100, 70.0,
                new PrologKnowledgeSession.RadarInput(null, 0.0, 0.0, "radar-1"));
            assertTrue(result.radar().passed(), "the radar is recorded as passed: " + result.radar());
            assertEquals("Default maintain speed baseline", decisionOf(context).getSourceRule());
            // As in Drools, a passed radar fact is not updated again in this session.
            radarCycle(harness, context, 1_200, 90.0, radarAhead("radar-2", 200.0, 50.0));
            assertEquals("Default maintain speed baseline", decisionOf(context).getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void radarIsForgottenOneSecondAfterItWasLastSeen() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            radarCycle(harness, context, 1_000, 90.0, radarAhead("radar-1", 200.0, 70.0));
            PrologCycleResult result = radarCycle(harness, context, 2_100, 90.0, PrologKnowledgeSession.RadarInput.NONE);
            assertEquals("none", result.radar().radarId());
            assertEquals("Default maintain speed baseline", decisionOf(context).getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void radarSensorReadingUpdatesTheDetectedRadar() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DecisionContext context = new DecisionContext();
            radarCycle(harness, context, 1_000, 90.0, radarAhead("radar-1", 200.0, 70.0));
            PrologCycleResult result = radarCycle(harness, context, 1_100, 90.0, PrologKnowledgeSession.RadarInput.NONE,
                new com.one2drive.projeto1.drools.events.SpeedRadarEvent(1_100, "radar-1", 197.5, 70.0));
            assertEquals(197.5, result.radar().distanceMeters(), 0.01);
            assertEquals(DrivingAction.REDUCE_SPEED, decisionOf(context).getAction());
        }
    }

    @Test
    @Timeout(10)
    void closedBarrierIsAvoidedThroughAVerifiedSafeLane() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 40.0, Lane.CENTER, LaneChangeState.IDLE,
                Lane.CENTER, RoadMarkingType.DASHED, RoadMarkingType.DASHED, false, true, false, false);
            DrivingDecision decision = decide(harness, vehicle,
                new BarrierDetectedEvent(1_000, "camera", 40.0, "barrier-1", false, Lane.CENTER));
            assertEquals(DrivingAction.CHANGE_LANE_RIGHT, decision.getAction());
            assertEquals("Avoid closed barrier by changing right from centre when safe", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void continuousLineOnTheLeftStopsBeforeTheObstacle() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 30.0, Lane.RIGHT, LaneChangeState.IDLE,
                Lane.RIGHT, RoadMarkingType.CONTINUOUS, RoadMarkingType.DASHED, false, false, false, false);
            DrivingDecision decision = decide(harness, vehicle,
                new ObstacleDetectedEvent(1_000, "camera", 40.0, "obstacle-1", HazardSeverity.MEDIUM, Lane.RIGHT),
                new LaneOccupancyEvent(1_000, "blind-spot", Lane.LEFT, false, Double.POSITIVE_INFINITY));
            assertEquals(DrivingAction.BRAKE, decision.getAction(),
                "the left lane is free but the continuous line forbids it");
            assertEquals("Stop before obstacle when lane change prohibited by continuous line (brake)",
                decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void ongoingLaneChangeKeepsItsDecision() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            var vehicle = new PrologKnowledgeSession.VehicleInput(1_000, 50.0, Lane.CENTER, LaneChangeState.CHANGING,
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
            DrivingDecision decision = harness.cycle(1_000, 90.0,
                new WeatherEvent(1_000, "env", WeatherCondition.RAIN, 0.7, 0.65));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(55.0, decision.getRecommendedSpeedKmh(), 0.01,
                "visibility below 0.75 and grip below 0.7 lower the rain speed to 55 km/h");
            assertEquals("Rain lowers recommended safe speed", decision.getSourceRule());
        }
    }

    @Test
    @Timeout(10)
    void reducedVisibilityAndGripTriggerConservativeReduction() {
        try (PrologTestHarness harness = new PrologTestHarness()) {
            DrivingDecision decision = harness.cycle(1_000, 88.0,
                new ReducedVisibilityEvent(1_000, "environment", 0.55),
                new ReducedGripEvent(1_000, "environment", 0.55));
            assertEquals(DrivingAction.REDUCE_SPEED, decision.getAction());
            assertEquals(41.25, decision.getRecommendedSpeedKmh(), 0.01, "max(30, 75 x 0.55) beats max(25, 80 x 0.55)");
            assertEquals("Low visibility requires stronger speed reduction", decision.getSourceRule());
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
        return new PrologKnowledgeSession.VehicleInput(timestamp, 60.0, Lane.CENTER, LaneChangeState.IDLE,
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
            harness.cycle(1_000, 40.0, new SpeedLimitDetectedEvent(1_000, "camera", 50, 20, "sign-50"));
            assertEquals(java.util.List.of("test(40.0>50+0.1)"),
                harness.session().whyNot("Reduce speed when exceeding detected limit"));
        }
    }

    private static boolean factPresent(PrologTestHarness harness, String fact) {
        return harness.lastResult().workingMemory().stream().anyMatch(f -> f.fact().equals(fact));
    }
}
