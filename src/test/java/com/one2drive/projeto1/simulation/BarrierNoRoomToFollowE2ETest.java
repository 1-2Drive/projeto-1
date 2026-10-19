package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A closed barrier the vehicle cannot go around safely is stopped for (Art. 18, 24 and 35
 * CE): the centre lane is reported clear alongside, but a vehicle stopped in it is too close to
 * be followed after the lane change, so moving there is not safe yet: the barrier holds the
 * vehicle, which only goes around it once it is slow enough to follow, and never drives into it.
 */
class BarrierNoRoomToFollowE2ETest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("from the centre, a left side with no room to follow sends the vehicle round on the right")
    void goesRoundOnTheRightWhenTheLeftHasNoRoomToFollow(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BARRIER_LEFT_NO_ROOM_TO_FOLLOW");
        scenario.setName("Closed barrier in the centre, left clear alongside but a stopped vehicle ahead in it");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        try (SimulationEngine engine = BackendTestSupport.engine(scenario, backend)) {
            engine.start();
            ScenarioActorDefinition stopped = new ScenarioActorDefinition();
            stopped.setType(ScenarioActorType.FRONT_VEHICLE);
            stopped.setLane(Lane.LEFT);
            stopped.setPositionMeters(90.0);
            stopped.setSpeedKmh(0.0);
            engine.addActorAhead(stopped);
            ScenarioRoadElementDefinition barrier = new ScenarioRoadElementDefinition();
            barrier.setType(ScenarioRoadElementType.BARRIER);
            barrier.setLane(Lane.CENTER);
            barrier.setPositionMeters(55.0);
            barrier.setBarrierOpen(false);
            engine.addRoadElementAhead(barrier);
            engine.tick();
            double barrierAt = engine.getWorldState().getRoadElements().stream()
                .filter(Barrier.class::isInstance).findFirst().orElseThrow().getPositionMeters();

            EgoVehicleState ego = engine.getWorldState().getEgo();
            String firstLaneRule = null;
            double startedAt = Double.NaN;
            for (int i = 0; i < 100 && firstLaneRule == null; i++) {
                if (i > 0) {
                    engine.tick();
                }
                if (engine.getCurrentDecision().getLaneAction().isLaneManeuver()) {
                    firstLaneRule = engine.getCurrentDecision().getLaneSourceRule();
                    startedAt = barrierAt - ego.getPositionMeters();
                }
            }
            assertTrue("Avoid closed barrier by changing right from centre when safe".equals(firstLaneRule),
                "the barrier must be gone round on the right, not " + firstLaneRule);
            // Decided as soon as the barrier is within reach (55 m), not only once the stopped
            // vehicle on the left comes close enough to fill that side.
            assertTrue(startedAt > 40.0, "the avoidance started only " + startedAt + " m before the barrier");
            for (int i = 0; i < 60 && ego.getLaneChangeState() != LaneChangeState.IDLE; i++) {
                engine.tick();
            }
            assertTrue(ego.getLane() == Lane.RIGHT, "the vehicle ends in the right lane, past the barrier's lane");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("a closed barrier holds the vehicle while the free side leaves no room to follow")
    void holdsForTheBarrierWhileTheFreeSideHasNoRoomToFollow(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("BARRIER_NO_ROOM_TO_FOLLOW");
        scenario.setName("Closed barrier, centre lane clear alongside but a stopped vehicle ahead in it");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        try (SimulationEngine engine = BackendTestSupport.engine(scenario, backend)) {
            engine.start();
            ScenarioActorDefinition slow = new ScenarioActorDefinition();
            slow.setType(ScenarioActorType.FRONT_VEHICLE);
            slow.setLane(Lane.CENTER);
            slow.setPositionMeters(90.0);
            slow.setSpeedKmh(0.0);
            engine.addActorAhead(slow);
            ScenarioRoadElementDefinition barrier = new ScenarioRoadElementDefinition();
            barrier.setType(ScenarioRoadElementType.BARRIER);
            barrier.setLane(Lane.RIGHT);
            barrier.setPositionMeters(55.0);
            barrier.setBarrierOpen(false);
            engine.addRoadElementAhead(barrier);
            engine.tick();
            double barrierAt = engine.getWorldState().getRoadElements().stream()
                .filter(Barrier.class::isInstance).findFirst().orElseThrow().getPositionMeters();

            EgoVehicleState ego = engine.getWorldState().getEgo();
            boolean heldForTheBarrier = false;
            boolean hitTheBarrier = false;
            for (int i = 0; i < 200 && ego.getPositionMeters() < barrierAt + 5.0; i++) {
                engine.tick();
                if (ego.getLane() == Lane.RIGHT && ego.getLaneChangeState() == LaneChangeState.IDLE) {
                    // Still in the barrier's lane, the side reported clear but no room to follow.
                    heldForTheBarrier |= "Closed barrier requires stop".equals(engine.getCurrentDecision().getSpeedSourceRule());
                }
                if (Math.abs(ego.getPositionMeters() - barrierAt) < 1.0
                    && Math.abs(ego.getLateralPositionMeters() - Lane.RIGHT.centreMeters()) < 1.75) {
                    hitTheBarrier = true;
                }
                if (ego.getSpeedKmh() < 0.5) {
                    break;
                }
            }
            assertTrue(heldForTheBarrier, "with no safe side the closed barrier must hold the vehicle");
            assertFalse(hitTheBarrier, "the vehicle drove into the closed barrier");
        }
    }
}
