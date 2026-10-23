package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
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
 * A pothole in the centre lane is not a hazard of the right lane (Art. 24 CE): the vehicle
 * driving in RIGHT passes it at its cruise speed instead of slowing down for it.
 */
class PotholeOtherLaneE2ETest {
    private static final double POTHOLE_METERS = 80.0;

    private static SimulationEngine engine(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("POTHOLE_IN_OTHER_LANE");
        scenario.setName("Pothole in the centre lane, vehicle in the right lane");
        scenario.setInitialSpeedKmh(60.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        ScenarioRoadElementDefinition pothole = new ScenarioRoadElementDefinition();
        pothole.setType(ScenarioRoadElementType.POTHOLE);
        pothole.setLane(Lane.CENTER);
        pothole.setPositionMeters(POTHOLE_METERS);
        pothole.setSeverity(HazardSeverity.HIGH);
        scenario.getRoadElements().add(pothole);
        return BackendTestSupport.engine(SimulationConfig.defaultConfig(), scenario, backend);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("does not slow down for a pothole in another lane")
    void ignoresAPotholeInAnotherLane(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend)) {
            engine.start();
            EgoVehicleState ego = engine.getWorldState().getEgo();
            double minSpeed = ego.getSpeedKmh();
            boolean slowedForPothole = false;
            for (int i = 0; i < 200 && ego.getPositionMeters() < POTHOLE_METERS + 10.0; i++) {
                engine.tick();
                minSpeed = Math.min(minSpeed, ego.getSpeedKmh());
                slowedForPothole |= engine.getCurrentDecision().getSpeedAction() == DrivingAction.REDUCE_SPEED
                    && "Pothole ahead reduce speed".equals(engine.getCurrentDecision().getSpeedSourceRule());
            }
            assertTrue(ego.getPositionMeters() > POTHOLE_METERS, "the vehicle must pass the pothole");
            assertEquals(Lane.RIGHT, ego.getLane(), "the vehicle stays in its own lane");
            assertFalse(slowedForPothole, "a pothole in another lane must not reduce the speed");
            assertTrue(minSpeed > 55.0, "slowed to " + minSpeed + " km/h for a pothole in another lane");
        }
    }
}
