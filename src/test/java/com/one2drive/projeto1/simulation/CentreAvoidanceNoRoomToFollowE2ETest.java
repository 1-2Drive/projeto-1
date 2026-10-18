package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.StaticObstacle;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A side reported clear with no room to follow the vehicle ahead in it is not a safe way past
 * a hazard (Art. 35 and 18 CE). From the centre the vehicle then takes the other side when
 * that one is safe, and slows down for a pothole it cannot avoid at all (Art. 24 CE).
 */
class CentreAvoidanceNoRoomToFollowE2ETest {

    private static SimulationEngine engine(ExpertBackendType backend, Lane egoLane) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("CENTRE_AVOIDANCE_NO_ROOM_TO_FOLLOW");
        scenario.setName("Hazard ahead, one side clear alongside but with a vehicle too close to follow");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(egoLane);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.start();
        return engine;
    }

    private static void addVehicle(SimulationEngine engine, Lane lane, double metersAhead, double speedKmh) {
        ScenarioActorDefinition vehicle = new ScenarioActorDefinition();
        vehicle.setType(ScenarioActorType.FRONT_VEHICLE);
        vehicle.setLane(lane);
        vehicle.setPositionMeters(metersAhead);
        vehicle.setSpeedKmh(speedKmh);
        engine.addActorAhead(vehicle);
    }

    private static double addHazard(SimulationEngine engine, ScenarioRoadElementType type, Lane lane, double metersAhead) {
        ScenarioRoadElementDefinition hazard = new ScenarioRoadElementDefinition();
        hazard.setType(type);
        hazard.setLane(lane);
        hazard.setPositionMeters(metersAhead);
        hazard.setSeverity(HazardSeverity.HIGH);
        engine.addRoadElementAhead(hazard);
        engine.tick();
        Class<?> kind = type == ScenarioRoadElementType.POTHOLE ? Pothole.class : StaticObstacle.class;
        return engine.getWorldState().getRoadElements().stream()
            .filter(kind::isInstance).findFirst().orElseThrow().getPositionMeters();
    }

    /** The first lane maneuver decided, and how far before the hazard it was decided. */
    private record Avoidance(String rule, double metersBefore) {
    }

    private static Avoidance firstAvoidance(SimulationEngine engine, double hazardAt) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                engine.tick();
            }
            if (engine.getCurrentDecision().getLaneAction().isLaneManeuver()) {
                return new Avoidance(engine.getCurrentDecision().getLaneSourceRule(), hazardAt - ego.getPositionMeters());
            }
        }
        return new Avoidance(null, Double.NaN);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("pothole in the centre: right clear but no room to follow, so it goes round on the left")
    void potholeIsGoneRoundOnTheLeftWhenTheRightHasNoRoomToFollow(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.CENTER)) {
            addVehicle(engine, Lane.RIGHT, 90.0, 0.0);
            double potholeAt = addHazard(engine, ScenarioRoadElementType.POTHOLE, Lane.CENTER, 55.0);
            Avoidance avoidance = firstAvoidance(engine, potholeAt);
            assertEquals("Avoid pothole in centre lane by changing left when right is occupied", avoidance.rule());
            assertTrue(avoidance.metersBefore() > 40.0,
                "the avoidance started only " + avoidance.metersBefore() + " m before the pothole");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("pothole that cannot be avoided safely: the vehicle slows down for it")
    void potholeWithNoSafeSideIsDrivenOverSlowly(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.RIGHT)) {
            addVehicle(engine, Lane.CENTER, 90.0, 0.0);
            double potholeAt = addHazard(engine, ScenarioRoadElementType.POTHOLE, Lane.RIGHT, 45.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            boolean slowedForIt = false;
            for (int i = 0; i < 100 && ego.getPositionMeters() < potholeAt
                && ego.getLaneChangeState() == LaneChangeState.IDLE; i++) {
                engine.tick();
                slowedForIt |= engine.getCurrentDecision().getSpeedAction() == DrivingAction.REDUCE_SPEED
                    && "Pothole ahead reduce speed".equals(engine.getCurrentDecision().getSpeedSourceRule());
            }
            assertTrue(slowedForIt, "with no safe side the pothole must be driven over slowly (Art. 24 CE)");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("obstacle in the centre: left clear but no room to follow, so it goes round on the right")
    void obstacleIsGoneRoundOnTheRightWhenTheLeftHasNoRoomToFollow(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.CENTER)) {
            addVehicle(engine, Lane.LEFT, 90.0, 0.0);
            double obstacleAt = addHazard(engine, ScenarioRoadElementType.STATIC_OBSTACLE, Lane.CENTER, 55.0);
            Avoidance avoidance = firstAvoidance(engine, obstacleAt);
            assertEquals("Avoid obstacle in centre lane by changing right when left is occupied", avoidance.rule());
            assertTrue(avoidance.metersBefore() > 40.0,
                "the avoidance started only " + avoidance.metersBefore() + " m before the obstacle");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("after going round on the right, a slower vehicle on the left is not passed on its right")
    void slowerVehicleOnTheLeftIsNotPassedOnItsRightAfterTheAvoidance(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.CENTER)) {
            addVehicle(engine, Lane.LEFT, 60.0, 20.0);
            addHazard(engine, ScenarioRoadElementType.STATIC_OBSTACLE, Lane.CENTER, 55.0);
            EgoVehicleState ego = engine.getWorldState().getEgo();
            OtherVehicle slow = engine.getWorldState().getActors().stream()
                .filter(OtherVehicle.class::isInstance).map(OtherVehicle.class::cast)
                .filter(v -> v.getLane() == Lane.LEFT).findFirst().orElseThrow();
            boolean passedOnItsRight = false;
            for (int i = 0; i < 300; i++) {
                engine.tick();
                passedOnItsRight |= ego.getLane() == Lane.RIGHT
                    && ego.getPositionMeters() > slow.getPositionMeters() + 5.0
                    && ego.getSpeedKmh() > slow.getSpeedMps() * 3.6 + 5.0;
            }
            assertFalse(passedOnItsRight, "a slower vehicle on the left must not be passed on its right (Art. 36 CE)");
        }
    }
}
