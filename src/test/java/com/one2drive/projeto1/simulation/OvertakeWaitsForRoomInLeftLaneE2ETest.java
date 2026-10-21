package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Overtaking a slow vehicle needs a left lane the ego can stay in: with a stopped vehicle
 * 100 m ahead in the left lane the move out is held (the roomToFollow sub-goal of the
 * lane-safety query) until that vehicle is behind, instead of moving out at 90 km/h and
 * braking hard behind it.
 */
class OvertakeWaitsForRoomInLeftLaneE2ETest {

    private static SimulationEngine runningEngine(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("OVERTAKE_WAITS_FOR_ROOM");
        scenario.setName("Slow vehicle ahead, stopped vehicle in the left lane");
        scenario.setInitialSpeedKmh(90.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.start();
        for (int i = 0; i < 5; i++) {
            engine.tick();
        }
        return engine;
    }

    /** Queues the vehicle; it enters the world on the next tick, together with the others queued. */
    private static String addVehicle(SimulationEngine engine, Lane lane, double metersAhead, double speedKmh) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(metersAhead);
        vehicle.setSpeedKmh(speedKmh);
        return engine.addActorAheadAndGetId(vehicle);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(90)
    @DisplayName("the move out behind a slow vehicle waits until the stopped vehicle in the left lane is behind")
    void doesNotMoveOutBehindAStoppedVehicleInTheLeftLane(ExpertBackendType backend) {
        try (SimulationEngine engine = runningEngine(backend)) {
            addVehicle(engine, Lane.CENTER, 60.0, 30.0);
            String stoppedId = addVehicle(engine, Lane.LEFT, 100.0, 0.0);
            engine.tick();
            OtherVehicle stopped = (OtherVehicle) engine.getWorldState().findActorById(stoppedId).orElseThrow();
            EgoVehicleState ego = engine.getWorldState().getEgo();
            boolean movedOutWhileStoppedAhead = false;
            boolean overtookLater = false;
            double closestSameLane = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 900; i++) {
                engine.tick();
                boolean movingLeft = ego.getLaneChangeState() == LaneChangeState.CHANGING && ego.getTargetLane() == Lane.LEFT;
                double stoppedAhead = engine.getWorldState().vehicleSignedDistance(stopped);
                if (movingLeft && stoppedAhead > 0.0) {
                    movedOutWhileStoppedAhead = true;
                }
                if (ego.getLane() == Lane.LEFT && stoppedAhead < 0.0) {
                    overtookLater = true;
                }
                for (DynamicActor actor : engine.getWorldState().getActors()) {
                    if (actor instanceof OtherVehicle other
                        && Math.abs(ego.getLateralPositionMeters() - other.getLane().centreMeters()) < 2.0) {
                        closestSameLane = Math.min(closestSameLane, Math.abs(engine.getWorldState().vehicleSignedDistance(other)));
                    }
                }
            }
            assertFalse(movedOutWhileStoppedAhead, "moved out to the left with a stopped vehicle ahead in that lane");
            assertTrue(overtookLater, "once the stopped vehicle is behind, the slow vehicle is overtaken on the left");
            assertTrue(closestSameLane > 5.0, "came within " + closestSameLane + " m of another vehicle in its lane");
        }
    }
}
