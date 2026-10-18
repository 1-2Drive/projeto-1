package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stop sign first seen with its stop line already behind (Art. 21 CE, sign B2): the
 * vehicle can no longer stop at the line, so it brakes hard, comes to rest as soon as it
 * can and makes the full stop there before going on, instead of driving through at speed.
 */
class StopSignSeenLateE2ETest {
    private static final double SIGN_METERS = 8.0;

    private static SimulationEngine engine(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("STOP_SIGN_SEEN_LATE");
        scenario.setName("Stop sign first seen past its stop line");
        scenario.setInitialSpeedKmh(60.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
        sign.setType(ScenarioRoadElementType.STOP_SIGN);
        sign.setLane(Lane.RIGHT);
        sign.setPositionMeters(SIGN_METERS);
        scenario.getRoadElements().add(sign);
        return BackendTestSupport.engine(SimulationConfig.defaultConfig(), scenario, backend);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("brakes hard and makes the full stop when the stop line is already behind")
    void stopsAsSoonAsPossibleWhenTheLineIsBehind(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend)) {
            engine.start();
            EgoVehicleState ego = engine.getWorldState().getEgo();
            assertTrue(SIGN_METERS - ego.getPositionMeters() < StoppingPolicy.STOP_SIGN_OFFSET_METERS,
                "the stop line must already be behind the vehicle");
            boolean emergency = false;
            int stoppedTicks = 0;
            int longestStop = 0;
            for (int i = 0; i < 100; i++) {
                engine.tick();
                emergency |= engine.getCurrentDecision().getSpeedAction() == DrivingAction.EMERGENCY_BRAKE;
                stoppedTicks = ego.getSpeedKmh() < 0.6 ? stoppedTicks + 1 : 0;
                longestStop = Math.max(longestStop, stoppedTicks);
            }
            assertTrue(emergency, "comfortable braking cannot stop in time: the vehicle must brake hard");
            assertTrue(longestStop >= 12, "the vehicle must make a full stop of 1.2 s, longest was "
                + longestStop * 100 + " ms");
            assertTrue(ego.getSpeedKmh() > 0.6, "after the full stop the vehicle goes on");
        }
    }
}
