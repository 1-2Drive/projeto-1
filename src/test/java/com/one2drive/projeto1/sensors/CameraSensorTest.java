package com.one2drive.projeto1.sensors;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioFactory;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import com.one2drive.projeto1.simulation.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraSensorTest {

    private static WorldState worldWithSignAt(double positionMeters) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("CAMERA_RANGE");
        scenario.setName("Camera range");
        scenario.setInitialSpeedKmh(0.0);
        scenario.setInitialLane(Lane.CENTER);
        scenario.setInitialWeather(WeatherCondition.CLEAR);

        ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
        sign.setType(ScenarioRoadElementType.SPEED_LIMIT);
        sign.setLane(Lane.CENTER);
        sign.setPositionMeters(positionMeters);
        sign.setSpeedLimitKmh(50);
        scenario.getRoadElements().add(sign);

        return ScenarioFactory.buildWorld(scenario, SimulationConfig.defaultConfig());
    }

    private static boolean seesSign(WorldState world) {
        List<Object> events = new CameraSensor(SimulationConfig.defaultConfig()).observe(world, 0L);
        return events.stream().anyMatch(e -> e instanceof SpeedLimitDetectedEvent);
    }

    @Test
    void detectsASignInsideTheNominalRangeInClearWeather() {
        WorldState world = worldWithSignAt(100.0);
        assertTrue(seesSign(world), "120 m camera must see a sign 100 m ahead in clear weather");
    }

    @Test
    void reducedVisibilityShortensTheCameraRange() {
        WorldState far = worldWithSignAt(100.0);
        far.getWeatherState().setVisibilityFactor(0.7);
        assertFalse(seesSign(far), "with visibility 0.7 the camera only reaches 84 m, so a sign at 100 m is not seen");

        WorldState near = worldWithSignAt(80.0);
        near.getWeatherState().setVisibilityFactor(0.7);
        assertTrue(seesSign(near), "a sign at 80 m is still inside the 84 m effective range");
    }

    @Test
    void effectiveRangeIsNeverLongerThanTheNominalRange() {
        WorldState world = worldWithSignAt(125.0);
        world.getWeatherState().setVisibilityFactor(1.5);
        assertFalse(seesSign(world), "a visibility factor above 1 must not extend the camera beyond 120 m");
    }

    @Test
    void effectiveRangeFallsBackToNominalForUnusableVisibility() {
        CameraSensor camera = new CameraSensor(SimulationConfig.defaultConfig());
        WorldState world = worldWithSignAt(100.0);

        world.getWeatherState().setVisibilityFactor(0.0);
        assertEquals(120.0, camera.effectiveRangeMeters(world), 1e-9, "a zero factor is unusable, so the nominal range applies");

        world.getWeatherState().setVisibilityFactor(Double.NaN);
        assertEquals(120.0, camera.effectiveRangeMeters(world), 1e-9, "a NaN factor is unusable, so the nominal range applies");

        world.getWeatherState().setVisibilityFactor(0.2);
        assertEquals(24.0, camera.effectiveRangeMeters(world), 1e-9, "dense fog at 0.2 leaves 24 m of the 120 m range");
    }

    @Test
    void reportsAStopSignOnlyWhileTheVehicleIsInItsLane() {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("STOP_LANE");
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        ScenarioRoadElementDefinition sign = new ScenarioRoadElementDefinition();
        sign.setType(ScenarioRoadElementType.STOP_SIGN);
        sign.setLane(Lane.CENTER);
        sign.setPositionMeters(60.0);
        scenario.getRoadElements().add(sign);
        WorldState world = ScenarioFactory.buildWorld(scenario, SimulationConfig.defaultConfig());
        CameraSensor camera = new CameraSensor(SimulationConfig.defaultConfig());

        assertFalse(seesStopSign(camera, world), "a stop sign in CENTER is not for a vehicle in RIGHT");

        world.getEgo().setLane(Lane.CENTER);
        assertTrue(seesStopSign(camera, world), "after moving into CENTER the sign of that lane applies");

        world.getEgo().setLane(Lane.LEFT);
        assertFalse(seesStopSign(camera, world), "and stops applying once the vehicle has left CENTER");
    }

    private static boolean seesStopSign(CameraSensor camera, WorldState world) {
        return camera.observe(world, 0L).stream().anyMatch(e -> e instanceof StopSignEvent);
    }
}
