package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.StoppingPolicy;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The red time of a traffic light is counted from the moment the ego is at rest at the stop
 * line, not from the moment the light turns red: however long the ego takes to stop, it
 * then waits SimulationConfig.RED_HOLD_AFTER_STOP_MS before the light turns green.
 */
class RedLightHoldTimerE2ETest {
    private static final long TICK_MILLIS = SimulationConfig.defaultConfig().getTickMillis();
    private static final long HOLD_TICKS = SimulationConfig.RED_HOLD_AFTER_STOP_MS / TICK_MILLIS;

    /** A light that starts yellow runs its cycle: red after the yellow time, then green. */
    private static SimulationEngine approachingAYellowLight(double speedKmh, double lightPositionMeters) {
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId("RED_HOLD_TIMER");
        scenario.setInitialSpeedKmh(speedKmh);
        scenario.setInitialLane(Lane.RIGHT);
        ScenarioRoadElementDefinition light = new ScenarioRoadElementDefinition();
        light.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        light.setLane(Lane.RIGHT);
        light.setPositionMeters(lightPositionMeters);
        light.setTrafficLightState(TrafficLightState.YELLOW);
        scenario.getRoadElements().add(light);
        SimulationEngine engine = new SimulationEngine(SimulationConfig.defaultConfig(), scenario, true, d -> { });
        engine.start();
        return engine;
    }

    private static TrafficLight onlyLight(SimulationEngine engine) {
        return engine.getWorldState().getRoadElements().stream()
            .filter(TrafficLight.class::isInstance).map(TrafficLight.class::cast).findFirst().orElseThrow();
    }

    /** One run up to the green light, as the world saw it. */
    private record Run(int firstRedTick, int stopTick, int greenTick, double restPositionMeters,
                       boolean timerRanWhileMoving, boolean movedBeforeGreen) {
    }

    private static Run runUntilGreen(SimulationEngine engine, TrafficLight light) {
        EgoVehicleState ego = engine.getWorldState().getEgo();
        int firstRed = -1;
        int stopTick = -1;
        int greenTick = -1;
        double restPosition = Double.NaN;
        boolean timerRanWhileMoving = false;
        boolean movedBeforeGreen = false;
        for (int tick = 1; tick <= 600 && greenTick < 0; tick++) {
            boolean atRestBeforeTheTick = ego.getSpeedKmh() < 0.1;
            engine.tick();
            if (light.getState() == TrafficLightState.RED) {
                if (firstRed < 0) {
                    firstRed = tick;
                }
                // The world advances at the start of a tick, with the speed the ego had then.
                timerRanWhileMoving |= !atRestBeforeTheTick && light.getPhaseElapsedMillis() > 0;
                if (stopTick < 0 && ego.getSpeedKmh() < 0.1) {
                    stopTick = tick;
                    restPosition = ego.getPositionMeters();
                } else if (stopTick >= 0) {
                    movedBeforeGreen |= Math.abs(ego.getPositionMeters() - restPosition) > 0.01;
                }
            }
            if (light.getState() == TrafficLightState.GREEN) {
                greenTick = tick;
            }
        }
        return new Run(firstRed, stopTick, greenTick, restPosition, timerRanWhileMoving, movedBeforeGreen);
    }

    @Test
    @Timeout(30)
    @DisplayName("red with the ego still moving: the red time does not run; it starts when the ego is at rest before the line")
    void redTimeStartsWhenTheEgoIsAtRestBeforeTheLine() {
        try (SimulationEngine engine = approachingAYellowLight(60.0, 80.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            TrafficLight light = onlyLight(engine);
            double stopLine = light.getPositionMeters() - StoppingPolicy.RED_LIGHT_STOP_LINE_OFFSET_METERS;

            // Up to the moment the ego is at rest: red, and nothing counted.
            int tick = 0;
            boolean sawRedWhileMoving = false;
            while (tick < 300 && !(light.getState() == TrafficLightState.RED && ego.getSpeedKmh() < 0.1)) {
                engine.tick();
                tick++;
                if (light.getState() == TrafficLightState.RED && ego.getSpeedKmh() >= 0.1) {
                    sawRedWhileMoving = true;
                    assertEquals(0L, light.getPhaseElapsedMillis(),
                        "the red time must not run while the ego is still moving, at " + ego.getSpeedKmh() + " km/h");
                }
            }
            assertTrue(sawRedWhileMoving, "the light must have been red while the ego was still braking");
            assertEquals(TrafficLightState.RED, light.getState());
            assertTrue(ego.getPositionMeters() <= stopLine,
                "the ego must be at rest before the stop line at " + stopLine + ", it is at " + ego.getPositionMeters());
            assertEquals(0L, light.getPhaseElapsedMillis(), "nothing has been counted before the ego is at rest");

            // From that moment the time runs, one tick at a time.
            engine.tick();
            assertEquals(TICK_MILLIS, light.getPhaseElapsedMillis(), "the red time starts the moment the ego is at rest");

            // 4 s at rest: still red.
            for (int i = 1; i < 40; i++) {
                engine.tick();
            }
            assertEquals(4000L, light.getPhaseElapsedMillis());
            assertEquals(TrafficLightState.RED, light.getState(), "after 4 s at rest the light must still be red");
            assertEquals(0.0, ego.getSpeedKmh(), 0.05, "the ego must still be stopped");

            // Just short of 5 s: still red. At 5 s: green.
            for (int i = 0; i < 9; i++) {
                engine.tick();
            }
            assertEquals(TrafficLightState.RED, light.getState(), "after 4.9 s at rest the light must still be red");
            engine.tick();
            assertEquals(TrafficLightState.GREEN, light.getState(), "after 5 s at rest the light must turn green");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("however long the ego takes to stop, the light turns green 5 s after it has stopped, not 5 s after turning red")
    void redTimeCountsFromTheStopHoweverLongTheApproachTakes() {
        double[][] approaches = {{60.0, 80.0}, {40.0, 115.0}, {30.0, 115.0}};
        for (double[] approach : approaches) {
            try (SimulationEngine engine = approachingAYellowLight(approach[0], approach[1])) {
                TrafficLight light = onlyLight(engine);
                Run run = runUntilGreen(engine, light);
                String label = approach[0] + " km/h, light at " + approach[1] + " m: ";

                assertTrue(run.stopTick() > 0, label + "the ego must come to rest at the red light");
                assertTrue(run.greenTick() > 0, label + "the light must turn green in the end");
                assertEquals(HOLD_TICKS, run.greenTick() - run.stopTick(),
                    label + "green must come exactly 5 s after the ego stopped (stopped at tick " + run.stopTick()
                        + ", red since tick " + run.firstRedTick() + ", green at tick " + run.greenTick() + ")");
                assertTrue(run.greenTick() - run.firstRedTick() > HOLD_TICKS,
                    label + "the light must have stayed red for longer than 5 s in all, the approach included");
                assertTrue(!run.timerRanWhileMoving(), label + "the red time must not run while the ego is moving");
                assertTrue(!run.movedBeforeGreen(), label + "the ego must stay where it stopped until the light is green");
            }
        }
        // The slowest approach takes longer than the red time itself to reach the line.
        try (SimulationEngine engine = approachingAYellowLight(30.0, 115.0)) {
            Run run = runUntilGreen(engine, onlyLight(engine));
            assertTrue(run.stopTick() - run.firstRedTick() > HOLD_TICKS,
                "this approach must take longer than 5 s from red to standstill, it took "
                    + (run.stopTick() - run.firstRedTick()) + " ticks");
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("green after the 5 s does not override a pedestrian: the ego stays stopped while the crossing is occupied")
    void greenDoesNotMoveTheEgoWhileAPedestrianIsCrossing() {
        try (SimulationEngine engine = approachingAYellowLight(60.0, 80.0)) {
            EgoVehicleState ego = engine.getWorldState().getEgo();
            TrafficLight light = onlyLight(engine);
            for (int i = 0; i < 300 && !(light.getState() == TrafficLightState.RED && ego.getSpeedKmh() < 0.1); i++) {
                engine.tick();
            }
            assertEquals(0.0, ego.getSpeedKmh(), 0.05, "the ego must be stopped at the red light");
            double restPosition = ego.getPositionMeters();

            // A slow pedestrian starts crossing between the ego and the light.
            ScenarioActorDefinition walker = new ScenarioActorDefinition();
            walker.setType(ScenarioActorType.PEDESTRIAN);
            walker.setLane(Lane.LEFT);
            walker.setPositionMeters(10.0);
            walker.setSpeedKmh(1.5);
            String id = engine.addActorAheadAndGetId(walker);

            boolean greenWhileCrossing = false;
            boolean movedWhileCrossing = false;
            int ticksStoppedOnGreen = 0;
            boolean crossingFinished = false;
            for (int i = 0; i < 500 && !crossingFinished; i++) {
                SimulationSnapshot snapshot = engine.tick();
                boolean crossing = engine.getWorldState().findActorById(id)
                    .map(Pedestrian.class::cast).map(Pedestrian::isCrossingInProgress).orElse(false);
                if (crossing) {
                    movedWhileCrossing |= Math.abs(ego.getPositionMeters() - restPosition) > 0.01;
                    if (light.getState() == TrafficLightState.GREEN) {
                        greenWhileCrossing = true;
                        ticksStoppedOnGreen++;
                        // The camera reports the green one reading later; from then on only
                        // the pedestrian is holding the ego.
                        if (ticksStoppedOnGreen > 5) {
                            assertTrue(snapshot.sourceRule().toLowerCase().contains("pedestrian"),
                                "on green it must be the pedestrian that holds the ego: " + snapshot.sourceRule());
                        }
                    }
                } else if (i > 5) {
                    crossingFinished = true;
                }
            }

            assertTrue(greenWhileCrossing, "the light must have turned green while the pedestrian was still crossing");
            assertTrue(ticksStoppedOnGreen > 50, "the ego must have waited on green for the pedestrian, it waited "
                + ticksStoppedOnGreen + " ticks");
            assertTrue(!movedWhileCrossing, "the ego must not move while the pedestrian is on the crossing");
            assertTrue(crossingFinished, "the pedestrian must finish crossing");

            for (int i = 0; i < 150; i++) {
                engine.tick();
            }
            assertTrue(ego.getPositionMeters() > light.getPositionMeters(),
                "once the crossing is free and the light is green the ego must drive on, it is at " + ego.getPositionMeters());
        }
    }
}
