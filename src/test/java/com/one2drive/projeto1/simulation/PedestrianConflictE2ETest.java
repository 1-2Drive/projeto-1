package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pedestrians that are not in the ego lane yet: the ego must anticipate one whose path will
 * cross its trajectory, leave alone one whose path will not, and go back to the normal
 * rules (a red light included) once the pedestrian has crossed.
 */
class PedestrianConflictE2ETest {
    private static final double EGO_SPEED_KMH = 60.0;
    private static final double WALKING_SPEED_KMH = 6.0;
    private static final double SLOW_WALKING_SPEED_KMH = 2.5;

    private static SimulationEngine runningEngine() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("PEDESTRIAN_CONFLICT");
        scenario.setName("Pedestrian crossing from another lane");
        scenario.setInitialSpeedKmh(EGO_SPEED_KMH);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> {});
        engine.start();
        for (int i = 0; i < 10; i++) {
            engine.tick();
        }
        return engine;
    }

    private static String addPedestrian(SimulationEngine engine, Lane lane, double metersAhead, double speedKmh) {
        ScenarioActorDefinition pedestrian = new ScenarioActorDefinition();
        pedestrian.setType(ScenarioActorType.PEDESTRIAN);
        pedestrian.setLane(lane);
        pedestrian.setPositionMeters(metersAhead);
        pedestrian.setSpeedKmh(speedKmh);
        return engine.addActorAheadAndGetId(pedestrian);
    }

    private static Optional<Pedestrian> pedestrian(SimulationEngine engine, String id) {
        return engine.getWorldState().findActorById(id).map(Pedestrian.class::cast);
    }

    private static boolean isBraking(SimulationSnapshot snapshot) {
        String decision = snapshot.currentDecision();
        return decision.equals(DrivingAction.BRAKE.name())
            || decision.equals(DrivingAction.STOP.name())
            || decision.equals(DrivingAction.EMERGENCY_BRAKE.name());
    }

    @Test
    @Timeout(30)
    @DisplayName("ego in RIGHT, pedestrian crossing from LEFT towards its lane: braking starts early and the ego stops short of the crossing")
    void anticipatesPedestrianCrossingFromLeftTowardsTheEgoLane() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double crossingPosition = ego.getPositionMeters() + 90.0;
            String id = addPedestrian(engine, Lane.LEFT, 90.0, WALKING_SPEED_KMH);

            double firstReactionGap = Double.NaN;
            Lane pedestrianLaneAtFirstReaction = null;
            boolean sawEmergencyBrake = false;
            double closestWhileOnTheRoad = Double.MAX_VALUE;
            for (int i = 0; i < 200; i++) {
                SimulationSnapshot snapshot = engine.tick();
                Optional<Pedestrian> walker = pedestrian(engine, id);
                boolean onTheRoad = walker.map(Pedestrian::isInRoadway).orElse(false);
                double gap = crossingPosition - ego.getPositionMeters();
                if (onTheRoad) {
                    closestWhileOnTheRoad = Math.min(closestWhileOnTheRoad, gap);
                    sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                    if (Double.isNaN(firstReactionGap) && isBraking(snapshot)
                        && snapshot.sourceRule().toLowerCase().contains("pedestrian")) {
                        firstReactionGap = gap;
                        pedestrianLaneAtFirstReaction = walker.get().getLane();
                    }
                }
            }

            assertFalse(Double.isNaN(firstReactionGap), "the ego must react to the crossing pedestrian");
            assertTrue(firstReactionGap > 45.0,
                "the reaction must come early, well before the 6 m emergency distance; it came at " + firstReactionGap + " m");
            assertTrue(pedestrianLaneAtFirstReaction != Lane.RIGHT,
                "the ego must react while the pedestrian is still in another lane, it was in " + pedestrianLaneAtFirstReaction);
            assertFalse(sawEmergencyBrake, "anticipating the pedestrian must not need the emergency brake");
            assertTrue(closestWhileOnTheRoad > 10.0,
                "the ego must stay short of the crossing while the pedestrian is on the road, closest was "
                    + closestWhileOnTheRoad + " m");
            assertTrue(pedestrian(engine, id).isEmpty(),
                "a pedestrian that reached the sidewalk must leave the scene, not stay drawn in the ego's lane");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("pedestrian standing in the ego lane: the ego stops near the pedestrian, neither far behind nor too close")
    void stopsNearAPedestrianStandingInItsLane() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double pedestrianPosition = ego.getPositionMeters() + 100.0;
            addPedestrian(engine, Lane.RIGHT, 100.0, 0.0);

            boolean sawEmergencyBrake = false;
            for (int i = 0; i < 250; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
            }

            double gap = pedestrianPosition - ego.getPositionMeters();
            assertEquals(0.0, ego.getSpeedKmh(), 0.5, "the ego must be stopped");
            assertFalse(sawEmergencyBrake, "a pedestrian seen from afar must not need the emergency brake");
            assertTrue(gap >= 8.0 && gap <= 24.0, "the ego must stop near the pedestrian, it stopped " + gap + " m away");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("ego in RIGHT, pedestrian standing in LEFT: no conflict, the ego does not brake")
    void doesNotBrakeForPedestrianInLeftLaneThatWillNotCross() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            addPedestrian(engine, Lane.LEFT, 90.0, 0.0);

            boolean braked = false;
            double slowest = Double.MAX_VALUE;
            boolean seenByCamera = false;
            for (int i = 0; i < 90; i++) {
                SimulationSnapshot snapshot = engine.tick();
                braked |= isBraking(snapshot);
                slowest = Math.min(slowest, ego.getSpeedKmh());
                seenByCamera |= !snapshot.cameraDetectedObjectIds().isEmpty();
            }

            assertTrue(seenByCamera, "the pedestrian must still be perceived, to be monitored");
            assertFalse(braked, "a pedestrian that will not cross the ego's trajectory must not make it brake");
            assertEquals(EGO_SPEED_KMH, slowest, 1.0, "the ego must keep its speed");
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("ego in RIGHT, pedestrian crossing LEFT -> CENTER -> RIGHT: the ego never speeds up while the crossing is occupied, and resumes only after it")
    void neverSpeedsUpWhileThePedestrianIsStillOnTheCrossing() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            String id = addPedestrian(engine, Lane.LEFT, 90.0, SLOW_WALKING_SPEED_KMH);

            boolean reacted = false;
            boolean spedUpWhileCrossing = false;
            boolean stoppedWhileCrossing = false;
            boolean sawEmergencyBrake = false;
            boolean seenInCenter = false;
            boolean seenInRight = false;
            double speedWhenCrossingEnded = Double.NaN;
            double previousSpeed = ego.getSpeedKmh();
            for (int i = 0; i < 300; i++) {
                SimulationSnapshot snapshot = engine.tick();
                Optional<Pedestrian> walker = pedestrian(engine, id);
                boolean crossing = walker.map(Pedestrian::isCrossingInProgress).orElse(false);
                if (crossing) {
                    seenInCenter |= walker.get().getLane() == Lane.CENTER;
                    seenInRight |= walker.get().getLane() == Lane.RIGHT;
                    reacted |= isBraking(snapshot);
                    spedUpWhileCrossing |= reacted && ego.getSpeedKmh() > previousSpeed + 0.01;
                    stoppedWhileCrossing |= ego.getSpeedKmh() < 0.5;
                    sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                } else if (Double.isNaN(speedWhenCrossingEnded) && reacted) {
                    speedWhenCrossingEnded = ego.getSpeedKmh();
                }
                previousSpeed = ego.getSpeedKmh();
            }

            assertTrue(seenInCenter && seenInRight, "the pedestrian must have walked LEFT -> CENTER -> RIGHT");
            assertTrue(reacted, "the ego must give way to the crossing pedestrian");
            assertFalse(spedUpWhileCrossing, "the ego must not speed up while the pedestrian is still on the crossing");
            assertTrue(stoppedWhileCrossing, "with a slow pedestrian the ego must come to a stop and wait");
            assertFalse(sawEmergencyBrake, "a pedestrian seen from afar must not need the emergency brake");
            assertTrue(speedWhenCrossingEnded < 1.0, "the ego must still be stopped when the crossing ends");
            assertTrue(ego.getSpeedKmh() > 30.0, "once the crossing is free the ego must drive on, it is at " + ego.getSpeedKmh());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("pedestrian has left the ego lane but is still on the crossing: the ego stays stopped until it is across")
    void staysStoppedAfterThePedestrianLeavesItsLaneButIsStillCrossing() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            // Starts in the ego's lane and walks away from it, across CENTER and LEFT.
            String id = addPedestrian(engine, Lane.RIGHT, 90.0, SLOW_WALKING_SPEED_KMH);

            int stoppedTicksWithPedestrianOutOfLane = 0;
            boolean movedOffWhileCrossing = false;
            boolean wasStopped = false;
            for (int i = 0; i < 300; i++) {
                engine.tick();
                Optional<Pedestrian> walker = pedestrian(engine, id);
                boolean crossing = walker.map(Pedestrian::isCrossingInProgress).orElse(false);
                if (crossing) {
                    boolean stopped = ego.getSpeedKmh() < 0.5;
                    movedOffWhileCrossing |= wasStopped && !stopped;
                    wasStopped |= stopped;
                    if (stopped && walker.get().getLane() != Lane.RIGHT) {
                        stoppedTicksWithPedestrianOutOfLane++;
                    }
                }
            }

            assertTrue(stoppedTicksWithPedestrianOutOfLane > 10,
                "the ego must wait while the pedestrian crosses the other lanes, it waited "
                    + stoppedTicksWithPedestrianOutOfLane + " ticks");
            assertFalse(movedOffWhileCrossing, "the ego must not move off before the pedestrian is across");
            assertTrue(pedestrian(engine, id).isEmpty(), "the pedestrian must have finished crossing");
            assertTrue(ego.getSpeedKmh() > 30.0, "once the crossing is free the ego must drive on, it is at " + ego.getSpeedKmh());
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("red light beyond a crossing pedestrian: the ego waits for the pedestrian, then still stops at the red light")
    void afterThePedestrianHasCrossedTheRedLightIsStillRespected() {
        try (SimulationEngine engine = runningEngine()) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double lightPosition = ego.getPositionMeters() + 130.0;
            ScenarioRoadElementDefinition red = new ScenarioRoadElementDefinition();
            red.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
            red.setLane(Lane.RIGHT);
            red.setPositionMeters(130.0);
            red.setTrafficLightState(TrafficLightState.RED);
            engine.addRoadElementAhead(red);
            double crossingPosition = ego.getPositionMeters() + 85.0;
            String id = addPedestrian(engine, Lane.LEFT, 85.0, WALKING_SPEED_KMH);

            boolean reactedToPedestrian = false;
            boolean passedCrossingWhilePedestrianOnRoad = false;
            boolean sawEmergencyBrake = false;
            for (int i = 0; i < 500; i++) {
                SimulationSnapshot snapshot = engine.tick();
                boolean onTheRoad = pedestrian(engine, id).map(Pedestrian::isInRoadway).orElse(false);
                if (onTheRoad) {
                    reactedToPedestrian |= isBraking(snapshot) && snapshot.sourceRule().toLowerCase().contains("pedestrian");
                    passedCrossingWhilePedestrianOnRoad |= ego.getPositionMeters() >= crossingPosition - 6.0;
                }
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
            }

            SimulationSnapshot last = engine.snapshot();
            assertTrue(reactedToPedestrian, "the pedestrian must prevail over the approach to the red light");
            assertFalse(passedCrossingWhilePedestrianOnRoad, "the ego must not reach the crossing while the pedestrian is on the road");
            assertFalse(sawEmergencyBrake, "neither the pedestrian nor the red light should need an emergency brake here");
            assertTrue(ego.getPositionMeters() > crossingPosition,
                "once the pedestrian has crossed the ego must move on, it stayed at " + ego.getPositionMeters());
            assertTrue(ego.getPositionMeters() < lightPosition,
                "the ego must not run the red light, light at " + lightPosition + ", ego at " + ego.getPositionMeters());
            assertEquals(0.0, ego.getSpeedKmh(), 0.5, "the ego must be stopped at the red light");
            assertEquals(DrivingAction.STOP.name(), last.currentDecision());
            assertTrue(last.sourceRule().toLowerCase().contains("red traffic light"),
                "the stop must come from the red light rules, it came from: " + last.sourceRule());
        }
    }
}
