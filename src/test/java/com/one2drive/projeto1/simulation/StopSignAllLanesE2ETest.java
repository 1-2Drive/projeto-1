package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.RoadMarkingType;
import com.one2drive.projeto1.domain.StopSign;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One stop sign, written to a single cell of the road map, is road-wide infrastructure: a
 * vehicle approaching in the LEFT, the CENTER or the RIGHT lane must stop for it, under both
 * rule engines. The sign is a single road element; it is never copied into the other lanes.
 */
class StopSignAllLanesE2ETest {
    private static final double SIGN_METERS = 150.0;

    private static SimulationEngine engine(ExpertBackendType backend, Lane egoLane) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("STOP_ALL_LANES_" + egoLane);
        scenario.setInitialSpeedKmh(50.0);
        scenario.setInitialLane(egoLane);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        scenario.setLeftMarking(RoadMarkingType.CONTINUOUS);
        scenario.setRightMarking(RoadMarkingType.CONTINUOUS);
        ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
        sign.setType(ScenarioRoadElementType.STOP_SIGN);
        sign.setLane(Lane.CENTER);
        sign.setPositionMeters(SIGN_METERS);
        scenario.getRoadElements().add(sign);
        return BackendTestSupport.engine(scenario, backend);
    }

    private static void stopsFromLane(ExpertBackendType backend, Lane egoLane) {
        try (SimulationEngine engine = engine(backend, egoLane)) {
            assertEquals(1, engine.getWorldState().getRoadElements().stream().filter(StopSign.class::isInstance).count(),
                "exactly one stop sign exists");
            StopSign sign = (StopSign) engine.getWorldState().getRoadElements().stream()
                .filter(StopSign.class::isInstance).findFirst().orElseThrow();
            engine.start();

            boolean stopped = false;
            boolean stopRuleSeen = false;
            boolean stopDecisionSeen = false;
            for (int i = 0; i < 500 && !stopped; i++) {
                SimulationSnapshot snapshot = engine.tick();
                var decision = engine.getCurrentDecision();
                stopRuleSeen |= decision.getSpeedSourceRule() != null && decision.getSpeedSourceRule().startsWith("Stop sign");
                stopDecisionSeen |= decision.getSpeedAction() == DrivingAction.STOP
                    || decision.getSpeedAction() == DrivingAction.BRAKE
                    || decision.getSpeedAction() == DrivingAction.REDUCE_SPEED;
                stopped = snapshot.egoSpeedKmh() < 0.6;
                assertEquals(egoLane, engine.getWorldState().getEgo().getLane(), "the vehicle keeps its lane");
            }
            assertTrue(stopped, "a vehicle in " + egoLane + " must stop for the road-wide stop sign");
            assertTrue(stopRuleSeen, "a stop sign rule must have been the speed source in " + egoLane);
            assertTrue(stopDecisionSeen, "a braking decision must have been taken in " + egoLane);
            double rest = engine.getWorldState().signedDistance(sign.getPositionMeters());
            assertTrue(rest >= StoppingPolicy.STOP_SIGN_OFFSET_METERS - 1.0 && rest <= StoppingPolicy.STOP_SIGN_OFFSET_METERS + 4.0,
                "and rest at the stop line before the sign, not on it: " + rest + " m short, from " + egoLane);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(90)
    @DisplayName("a vehicle approaching in the LEFT lane stops for the stop sign")
    void stopsFromTheLeftLane(ExpertBackendType backend) {
        stopsFromLane(backend, Lane.LEFT);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(90)
    @DisplayName("a vehicle approaching in the CENTER lane stops for the stop sign")
    void stopsFromTheCenterLane(ExpertBackendType backend) {
        stopsFromLane(backend, Lane.CENTER);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(90)
    @DisplayName("a vehicle approaching in the RIGHT lane stops for the stop sign")
    void stopsFromTheRightLane(ExpertBackendType backend) {
        stopsFromLane(backend, Lane.RIGHT);
    }
}
