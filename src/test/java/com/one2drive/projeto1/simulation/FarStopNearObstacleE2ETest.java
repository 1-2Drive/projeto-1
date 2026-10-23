package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
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
 * A mandatory stop far ahead must not keep the vehicle from going around an obstacle
 * close ahead: a red light at 80 m governs the speed on the approach, and an obstacle
 * fills the ego lane at 45 m with the centre lane free: far enough for a lane change to
 * leave the lane before it (StoppingPolicy.roomToLeaveLane, 40 m at 50 km/h). The vehicle
 * moves around the obstacle while slowing down, and then stops at the light's stop line.
 */
class FarStopNearObstacleE2ETest {
    private static final double LIGHT_METERS = 80.0;
    private static final double OBSTACLE_METERS = 45.0;
    private static final double STOP_LINE_METERS = LIGHT_METERS - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

    private static SimulationEngine engine(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("FAR_RED_LIGHT_NEAR_OBSTACLE");
        scenario.setName("Red light ahead, obstacle in lane before it");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        ScenarioRoadElementDefinition light = new ScenarioRoadElementDefinition();
        light.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        light.setLane(Lane.CENTER);
        light.setPositionMeters(LIGHT_METERS);
        light.setTrafficLightState(TrafficLightState.RED);
        scenario.getRoadElements().add(light);
        ScenarioRoadElementDefinition obstacle = new ScenarioRoadElementDefinition();
        obstacle.setType(ScenarioRoadElementType.STATIC_OBSTACLE);
        obstacle.setLane(Lane.RIGHT);
        obstacle.setPositionMeters(OBSTACLE_METERS);
        // MEDIUM: the emergency rule for HIGH obstacles must not be what saves the vehicle.
        obstacle.setSeverity(HazardSeverity.MEDIUM);
        scenario.getRoadElements().add(obstacle);
        return BackendTestSupport.engine(SimulationConfig.defaultConfig(), scenario, backend);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("goes around the obstacle while slowing for the red light, then stops at the line")
    void avoidsTheObstacleAndStopsAtTheLight(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend)) {
            engine.start();
            EgoVehicleState ego = engine.getWorldState().getEgo();
            boolean leftTheLane = false;
            boolean hitObstacle = false;
            boolean emergency = false;
            boolean stopped = false;
            for (int i = 0; i < 600; i++) {
                engine.tick();
                DrivingAction speedAction = engine.getCurrentDecision().getSpeedAction();
                emergency |= speedAction == DrivingAction.EMERGENCY_BRAKE;
                double position = ego.getPositionMeters();
                if (Math.abs(position - OBSTACLE_METERS) < 2.5
                    && Math.abs(ego.getLateralPositionMeters() - Lane.RIGHT.centreMeters()) < 1.75) {
                    hitObstacle = true;
                }
                if (ego.getLane() == Lane.CENTER) {
                    leftTheLane = true;
                }
                if (ego.getSpeedKmh() < 0.5 && position > OBSTACLE_METERS) {
                    stopped = true;
                    break;
                }
            }
            assertTrue(leftTheLane, "the vehicle must go around the obstacle through the centre lane");
            assertFalse(hitObstacle, "the vehicle drove through the obstacle");
            assertFalse(emergency, "the obstacle must be avoided, not survived by the emergency brake");
            assertTrue(stopped, "the vehicle must come to rest for the red light");
            assertTrue(ego.getPositionMeters() <= STOP_LINE_METERS + StoppingPolicy.STOP_LINE_TOLERANCE_METERS,
                "stopped at " + ego.getPositionMeters() + " m, past the stop line at " + STOP_LINE_METERS + " m");
        }
    }
}
