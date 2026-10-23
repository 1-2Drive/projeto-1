package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.TrafficLight;
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

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two red lights 10 m apart: the stop line of the second (18 m before it) lies before the
 * first light. When the first turns green the vehicle must not drive on as if the road were
 * clear: the camera reports the red light beyond, so the vehicle only moves up to that light's
 * line and waits there. It used to see the second light only after passing the first, with
 * its line already behind, and stopped between the two.
 */
class TwoTrafficLightsE2ETest {

    private static ScenarioRoadElementDefinition redLight(double metersAhead) {
        ScenarioRoadElementDefinition light = new ScenarioRoadElementDefinition();
        light.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        light.setLane(Lane.RIGHT);
        light.setPositionMeters(metersAhead);
        light.setTrafficLightState(TrafficLightState.RED);
        return light;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(90)
    @DisplayName("a red light just beyond a green one holds the vehicle at its own stop line")
    void theSecondRedLightHoldsTheVehicleAtItsLine(ExpertBackendType backend) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("TWO_TRAFFIC_LIGHTS");
        scenario.setName("Two red lights 10 m apart");
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        scenario.getRoadElements().add(redLight(80.0));
        scenario.getRoadElements().add(redLight(90.0));
        try (SimulationEngine engine = BackendTestSupport.engine(scenario, backend)) {
            engine.start();
            List<TrafficLight> lights = engine.getWorldState().getRoadElements().stream()
                .filter(TrafficLight.class::isInstance).map(TrafficLight.class::cast)
                .sorted(Comparator.comparingDouble(TrafficLight::getPositionMeters)).toList();
            TrafficLight second = lights.get(1);
            double secondLine = second.getPositionMeters() - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;
            EgoVehicleState ego = engine.getWorldState().getEgo();

            boolean ranTheSecondRed = false;
            boolean passedBoth = false;
            for (int i = 0; i < 700 && !passedBoth; i++) {
                engine.tick();
                if (second.getState() != TrafficLightState.GREEN
                    && ego.getPositionMeters() > secondLine + StoppingPolicy.STOP_LINE_TOLERANCE_METERS) {
                    ranTheSecondRed = true;
                }
                passedBoth = ego.getPositionMeters() > second.getPositionMeters() + 5.0;
            }
            assertFalse(ranTheSecondRed, "the vehicle went past the second light's stop line while it was red");
            assertTrue(passedBoth, "once both lights are green the vehicle must drive on past them");
        }
    }
}
