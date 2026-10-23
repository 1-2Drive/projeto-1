package com.one2drive.projeto1.decision;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.RiskLevel;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioFactory;
import com.one2drive.projeto1.simulation.DecisionActuator;
import com.one2drive.projeto1.simulation.WorldState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SpeedIntent answers "does this decision speed the vehicle up, hold it, or slow it down?"
 * for the explanations; every case is checked against what the actuator really does with it.
 */
class SpeedIntentTest {
    private static final SimulationConfig CONFIG = SimulationConfig.defaultConfig();

    @ParameterizedTest(name = "{1} @{2} to {3} km/h at {0} km/h -> {4}")
    @CsvSource({
        "42, MAINTAIN_SPEED, 20, 50, INCREASE",   // build back up to the cruise speed
        "50, MAINTAIN_SPEED, 10, 50, HOLD",
        "48, REDUCE_SPEED, 40, 30, REDUCE",
        "30, REDUCE_SPEED, 40, 30, HOLD",
        "40, BRAKE, 50, 60, HOLD",                // a braking decision never raises the speed
        "40, BRAKE, 50, 20, REDUCE",
        "20, STOP, 60, 0, REDUCE",
        "0, STOP, 60, 0, HOLD",                   // already stopped: held
        "40, MAINTAIN_SPEED, 60, 80, HOLD",       // a mandatory stop that only stops accelerating
        "40, REDUCE_SPEED, 60, 40, REDUCE",       // under a mandatory stop it brakes whatever its target
        "10, RESUME, 30, 30, INCREASE",
        "50, EMERGENCY_BRAKE, 70, 0, REDUCE",
        "1, CONTINUE, 30, 30, INCREASE",          // moving off
        "60, ACCELERATE, 10, 80, INCREASE",
        "60, CONTINUE, 10, 35, REDUCE"            // open barrier: pass at up to 35 km/h
    })
    void matchesWhatTheActuatorDoes(double current, DrivingAction action, int priority, double target, SpeedIntent expected) {
        assertEquals(expected, SpeedIntent.of(action, priority, target, current));
        assertEquals(expected, actuated(action, priority, target, current), "actuator");
    }

    private static SpeedIntent actuated(DrivingAction action, int priority, double target, double current) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("INTENT");
        scenario.setName("Intent");
        scenario.setInitialSpeedKmh(current);
        scenario.setInitialLane(Lane.RIGHT);
        scenario.setInitialWeather(WeatherCondition.CLEAR);
        WorldState world = ScenarioFactory.buildWorld(scenario, CONFIG);
        world.getEgo().setSpeedKmh(current);
        DrivingDecision decision = new DrivingDecision();
        decision.apply(action, target, RiskLevel.LOW, priority, "test", "test rule", 0L, List.of());
        new DecisionActuator(CONFIG).applyDecision(world, decision);
        double acceleration = world.getEgo().getAccelerationMps2();
        double after = world.getEgo().getSpeedKmh();
        // Inside the dead band the actuator snaps the speed onto the target: that is holding it.
        if (acceleration > 1e-9 || after > current + SpeedIntent.DEAD_BAND_KMH) {
            return SpeedIntent.INCREASE;
        }
        if (acceleration < -1e-9 || after < current - SpeedIntent.DEAD_BAND_KMH) {
            return SpeedIntent.REDUCE;
        }
        return SpeedIntent.HOLD;
    }
}
