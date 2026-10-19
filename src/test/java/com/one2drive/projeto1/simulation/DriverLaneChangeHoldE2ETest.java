package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lane change the driver asked for is not undone as soon as it settles: the vehicle stays
 * in the lane it moved to for 5 s, and only then keep right (Art. 13 CE) takes it back.
 */
class DriverLaneChangeHoldE2ETest {

    private static SimulationEngine engine(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("DRIVER_LANE_CHANGE_HOLD");
        scenario.setName("Driver asks to change lane left on an empty road");
        scenario.setInitialSpeedKmh(60.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        return BackendTestSupport.engine(SimulationConfig.defaultConfig(), scenario, backend);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("stays in the lane the driver asked for 5 s before keeping right again")
    void holdsTheRequestedLaneBeforeKeepingRight(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend)) {
            engine.start();
            EgoVehicleState ego = engine.getWorldState().getEgo();
            for (int i = 0; i < 20; i++) {
                engine.tick();
            }
            engine.requestLaneChange(LaneChangeDirection.LEFT);
            int i = 0;
            while (i < 60 && !(ego.getLane() == Lane.CENTER && ego.getLaneChangeState() == LaneChangeState.IDLE)) {
                engine.tick();
                i++;
            }
            assertEquals(Lane.CENTER, ego.getLane(), "the requested lane change must be made");
            long arrivedAt = ego.getLaneSettledAtMillis();

            long leftCentreAt = -1;
            for (int t = 0; t < 120 && leftCentreAt < 0; t++) {
                SimulationSnapshot snapshot = engine.tick();
                if (ego.getLaneChangeState() != LaneChangeState.IDLE || ego.getLane() != Lane.CENTER) {
                    leftCentreAt = snapshot.simTimeMillis();
                }
            }
            assertTrue(leftCentreAt > 0, "keep right must eventually take the vehicle back right");
            assertTrue(leftCentreAt - arrivedAt >= 5000,
                "the vehicle left the requested lane " + (leftCentreAt - arrivedAt) + " ms after reaching it");
        }
    }
}
