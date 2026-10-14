package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stationary vehicle in the ego lane, added while the simulation is running. The ego
 * must go around it when the left lane is clear, and otherwise stop behind it at a safe
 * distance and wait (Portuguese Highway Code Art. 18, 24, 35 and 38).
 */
class StationaryFrontVehicleE2ETest {

    private static SimulationEngine runningEngine(ExpertBackendType backend, double speedKmh) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("STATIONARY_FRONT_VEHICLE");
        scenario.setName("Stationary vehicle in the ego lane");
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.start();
        for (int i = 0; i < 10; i++) {
            engine.tick();
        }
        return engine;
    }

    private static ScenarioActorDefinition stationaryVehicle(Lane lane, double metersAhead) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(metersAhead);
        vehicle.setSpeedKmh(0.0);
        return vehicle;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("left lane clear: the ego goes around the stationary vehicle and returns to the right lane")
    void goesAroundStationaryVehicleWhenLeftLaneIsClear(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend, 60.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double vehiclePosition = ego.getPositionMeters() + 60.0;
            engine.addActorAhead(stationaryVehicle(Lane.RIGHT, 60.0));

            boolean leftTheLane = false;
            boolean passed = false;
            boolean backInRightLane = false;
            double closestInSameLane = Double.MAX_VALUE;
            for (int i = 0; i < 600 && !backInRightLane; i++) {
                engine.tick();
                double gap = vehiclePosition - ego.getPositionMeters();
                // The committed lane only changes when a maneuver settles, so use the lateral position.
                boolean physicallyInRightLane = Lane.fromLateralPosition(ego.getLateralPositionMeters()) == Lane.RIGHT;
                if (gap > 0 && physicallyInRightLane) {
                    closestInSameLane = Math.min(closestInSameLane, gap);
                }
                if (!physicallyInRightLane) {
                    leftTheLane = true;
                }
                if (gap < 0) {
                    passed = true;
                }
                backInRightLane = passed && ego.getLane() == Lane.RIGHT
                    && ego.getLaneChangeState() == LaneChangeState.IDLE;
            }

            assertTrue(leftTheLane, "ego must change lane to go around the stationary vehicle");
            assertTrue(passed, "ego must get past the stationary vehicle");
            assertTrue(closestInSameLane > 4.0,
                "ego must never close in on the vehicle while still in its lane, closest was " + closestInSameLane + " m");
            assertTrue(backInRightLane, "ego must return to the right lane after passing (Art. 13/38 CE)");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("left lane blocked: the ego stops behind the stationary vehicle, holds, then goes around once the lane clears")
    void stopsBehindStationaryVehicleAndGoesAroundOnceLeftLaneClears(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend, 60.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double vehiclePosition = ego.getPositionMeters() + 80.0;
            engine.addActorAhead(stationaryVehicle(Lane.RIGHT, 80.0));
            String blockerId = engine.addActorAheadAndGetId(stationaryVehicle(Lane.CENTER, 80.0));

            boolean sawEmergencyBrake = false;
            int stoppedAtTick = -1;
            for (int i = 0; i < 300 && stoppedAtTick < 0; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                if (ego.getSpeedKmh() < 0.1) {
                    stoppedAtTick = i;
                }
            }
            assertTrue(stoppedAtTick >= 0, "ego must come to a stop behind the stationary vehicle");
            assertFalse(sawEmergencyBrake, "the approach must be a progressive brake, not an emergency brake (Art. 24 CE)");
            double gapAtStop = vehiclePosition - ego.getPositionMeters();
            assertTrue(gapAtStop >= 6.0 && gapAtStop <= 16.0,
                "ego must stop at a safe distance behind the vehicle (Art. 18 CE), stopped " + gapAtStop + " m behind");
            assertEquals(Lane.RIGHT, ego.getLane(), "ego must not change lane while the left lane is blocked");

            // Held: no stop-and-go while nothing changes.
            double positionAtStop = ego.getPositionMeters();
            for (int i = 0; i < 100; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
            }
            assertTrue(ego.getPositionMeters() - positionAtStop < 1.0,
                "ego must stay stopped behind the vehicle, crept " + (ego.getPositionMeters() - positionAtStop) + " m");
            assertFalse(sawEmergencyBrake, "holding behind the vehicle must not trigger emergency braking");

            // The left lane clears: go around from a standstill.
            assertTrue(engine.removeWorldObjectById(blockerId));
            boolean passed = false;
            for (int i = 0; i < 400 && !passed; i++) {
                engine.tick();
                passed = ego.getPositionMeters() > vehiclePosition + 5.0;
            }
            assertTrue(passed, "ego must go around the stationary vehicle once the left lane is clear");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("stationary vehicle seen from far away: the ego passes it on the move without emergency braking")
    void passesFarStationaryVehicleOnTheMove(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend, 60.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double vehiclePosition = ego.getPositionMeters() + 125.0;
            engine.addActorAhead(stationaryVehicle(Lane.RIGHT, 125.0));

            boolean sawEmergencyBrake = false;
            boolean passed = false;
            double slowest = Double.MAX_VALUE;
            for (int i = 0; i < 300 && !passed; i++) {
                SimulationSnapshot snapshot = engine.tick();
                sawEmergencyBrake |= snapshot.currentDecision().equals(DrivingAction.EMERGENCY_BRAKE.name());
                slowest = Math.min(slowest, ego.getSpeedKmh());
                passed = ego.getPositionMeters() > vehiclePosition + 5.0;
            }

            assertTrue(passed, "ego must get past the stationary vehicle");
            assertFalse(sawEmergencyBrake, "a vehicle seen from far away must be passed without emergency braking");
            assertTrue(slowest > 20.0, "ego must pass on the move, slowest speed was " + slowest + " km/h");
        }
    }
}
