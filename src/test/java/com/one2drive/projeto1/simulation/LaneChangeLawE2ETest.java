package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.RoadMarkingType;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane changes decided the way the Highway Code puts it: the driver makes sure the maneuver
 * is safe from what can be perceived (Art. 35 and 38 CE), and only the line actually crossed
 * matters, a continuous line never being crossed.
 */
class LaneChangeLawE2ETest {

    private static SimulationEngine engine(ExpertBackendType backend, Lane egoLane, double speedKmh,
                                           RoadMarkingType leftMarking, RoadMarkingType rightMarking) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("LANE_CHANGE_LAW");
        scenario.setName("Lane changes and the Highway Code");
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialLane(egoLane);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        if (leftMarking != null) {
            scenario.setLeftMarking(leftMarking);
        }
        if (rightMarking != null) {
            scenario.setRightMarking(rightMarking);
        }
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

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("a closed barrier is passed through the lane the blind-spot sensor reports clear")
    void closedBarrierIsPassedThroughTheLaneTheSensorReportsClear(ExpertBackendType backend) {
        try (SimulationEngine engine = engine(backend, Lane.CENTER, 40.0, null, null)) {
            // LEFT is taken alongside for the whole run, so the only way past is RIGHT.
            addVehicle(engine, Lane.LEFT, 0.0, 40.0);
            ScenarioRoadElementDefinition barrier = new ScenarioRoadElementDefinition();
            barrier.setType(ScenarioRoadElementType.BARRIER);
            barrier.setLane(Lane.CENTER);
            barrier.setPositionMeters(80.0);
            barrier.setBarrierOpen(false);
            engine.addRoadElementAhead(barrier);

            boolean avoidedRight = false;
            for (int i = 0; i < 200 && !avoidedRight; i++) {
                engine.tick();
                avoidedRight = "Avoid closed barrier by changing right from centre when safe"
                    .equals(engine.getCurrentDecision().getLaneSourceRule());
            }
            assertTrue(avoidedRight, "the barrier must be avoided through the clear right lane");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(30)
    @DisplayName("overtaking from RIGHT only looks at the line it crosses, not the CENTER-LEFT one")
    void overtakingFromRightIgnoresTheLineItDoesNotCross(ExpertBackendType backend) {
        // A continuous line between CENTER and LEFT, a dashed one between RIGHT and CENTER: the
        // overtake RIGHT -> CENTER crosses only the dashed one, so it is allowed.
        try (SimulationEngine engine = engine(backend, Lane.RIGHT, 60.0, RoadMarkingType.CONTINUOUS, null)) {
            addVehicle(engine, Lane.RIGHT, 60.0, 30.0);

            boolean reachedCenter = false;
            for (int i = 0; i < 300 && !reachedCenter; i++) {
                engine.tick();
                reachedCenter = engine.getWorldState().getEgo().getLane() == Lane.CENTER;
            }
            assertTrue(reachedCenter, "the ego must overtake the slow RIGHT vehicle through CENTER");
            assertEquals(RoadMarkingType.CONTINUOUS, engine.getWorldState().getLeftMarking(),
                "the CENTER-LEFT line stays continuous, and is never crossed");
        }
    }
}
