package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.domain.Lane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomObjectSpawnerTest {
    /** About what the vehicle covers in one 100 ms tick at 108 km/h. */
    private static final double STEP_METERS = 3.0;

    /** Everything a spawn sets, so two runs can be compared action by action. */
    private static String describe(ScenarioTimedAction a) {
        ScenarioRoadElementDefinition e = a.getRoadElement();
        ScenarioActorDefinition actor = a.getActor();
        return a.getType()
            + (e == null ? "" : " " + e.getType() + " " + e.getLane() + " " + e.getPositionMeters() + " "
                + e.getSpeedLimitKmh() + " " + e.getTrafficLightState() + " " + e.getSeverity() + " " + e.getBarrierOpen())
            + (actor == null ? "" : " " + actor.getType() + " " + actor.getLane() + " " + actor.getPositionMeters()
                + " " + actor.getSpeedKmh());
    }

    /** Drives {@code meters} in ticks and returns every spawn, each prefixed with where it happened. */
    private static List<String> drive(RandomObjectSpawner spawner, double meters) {
        List<String> spawns = new ArrayList<>();
        for (double travelled = 0; travelled <= meters; travelled += STEP_METERS) {
            for (ScenarioTimedAction action : spawner.due(travelled, false)) {
                spawns.add(Math.round(travelled) + " m: " + describe(action));
            }
        }
        return spawns;
    }

    /** Spawns over {@code meters}, a crosswalk and its pedestrian counting as one. */
    private static long spawnCount(int frequency, double meters) {
        RandomObjectSpawner spawner = new RandomObjectSpawner(1, frequency);
        long count = 0;
        for (double travelled = 0; travelled <= meters; travelled += STEP_METERS) {
            if (!spawner.due(travelled, false).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("the same seed at the same frequency spawns the same objects, another seed different ones")
    void seedDecidesTheObjects() {
        assertEquals(drive(new RandomObjectSpawner(42, 50), 3000), drive(new RandomObjectSpawner(42, 50), 3000));
        assertNotEquals(drive(new RandomObjectSpawner(42, 50), 3000), drive(new RandomObjectSpawner(43, 50), 3000));
    }

    @Test
    @DisplayName("the frequency sets how many objects appear per kilometre: none at 0, one every 25 m at 35, every 8.75 m at 100")
    void frequencySetsTheSpacing() {
        double meters = 20_000;
        assertEquals(0, spawnCount(0, meters));
        assertEquals(25.0, RandomObjectSpawner.meanSpacingMeters(35), 1e-9);
        assertEquals(8.75, RandomObjectSpawner.meanSpacingMeters(100), 1e-9);
        for (int frequency : new int[] {10, 35, 70, 100}) {
            // A spawn waits for the first tick past its gap: on average half a step more.
            double expected = meters / (RandomObjectSpawner.meanSpacingMeters(frequency) + STEP_METERS / 2);
            long count = spawnCount(frequency, meters);
            assertTrue(Math.abs(count - expected) < expected * 0.1, "at " + frequency + ": " + count + ", expected about " + expected);
        }
    }

    @Test
    @DisplayName("nothing appears while the vehicle stands still")
    void standingStillSpawnsNothingMore() {
        RandomObjectSpawner spawner = new RandomObjectSpawner(5, 100);
        assertFalse(spawner.due(250, false).isEmpty(), "the first object comes at once");
        for (int tick = 0; tick < 600; tick++) {
            assertTrue(spawner.due(250, false).isEmpty());
        }
    }

    @Test
    @DisplayName("a new frequency applies at once: raised from 0, the next object appears straight away")
    void frequencyChangesApplyAtOnce() {
        RandomObjectSpawner spawner = new RandomObjectSpawner(9, 0);
        assertTrue(drive(spawner, 1000).isEmpty());

        spawner.setFrequency(100);
        assertFalse(spawner.due(1000, false).isEmpty());
        assertEquals(100, spawner.getFrequency());

        spawner.setFrequency(0);
        for (double travelled = 1000; travelled < 3000; travelled += STEP_METERS) {
            assertTrue(spawner.due(travelled, false).isEmpty());
        }
    }

    @Test
    @DisplayName("frequencies outside 0..100 are refused")
    void frequencyIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new RandomObjectSpawner(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new RandomObjectSpawner(1, 101));
        RandomObjectSpawner spawner = new RandomObjectSpawner(1, 50);
        assertThrows(IllegalArgumentException.class, () -> spawner.setFrequency(150));
        assertEquals(50, spawner.getFrequency());
    }

    @Test
    @DisplayName("objects appear ahead of the car, of every kind, hazards in every lane and vehicles never on the left, barriers closed, and the weather turns both ways")
    void spawnsAreAheadAndVaried() {
        Set<ScenarioRoadElementType> elementTypes = EnumSet.noneOf(ScenarioRoadElementType.class);
        Set<ScenarioActorType> actorTypes = EnumSet.noneOf(ScenarioActorType.class);
        Set<Lane> hazardLanes = EnumSet.noneOf(Lane.class);
        Set<Lane> vehicleLanes = EnumSet.noneOf(Lane.class);
        Set<ScenarioTimedActionType> weather = EnumSet.noneOf(ScenarioTimedActionType.class);

        RandomObjectSpawner spawner = new RandomObjectSpawner(3, 100);
        boolean raining = false;
        for (double travelled = 0; travelled < 20_000; travelled += STEP_METERS) {
            for (ScenarioTimedAction action : spawner.due(travelled, raining)) {
                ScenarioRoadElementDefinition element = action.getRoadElement();
                ScenarioActorDefinition actor = action.getActor();
                double ahead = element != null ? element.getPositionMeters()
                    : actor != null ? actor.getPositionMeters() : Double.NaN;
                if (!Double.isNaN(ahead)) {
                    assertTrue(ahead >= RandomObjectSpawner.MIN_AHEAD_METERS
                        && ahead <= RandomObjectSpawner.MAX_AHEAD_METERS, ahead + " m ahead");
                }
                if (element != null) {
                    elementTypes.add(element.getType());
                    if (element.getType() == ScenarioRoadElementType.BARRIER) {
                        assertEquals(Boolean.FALSE, element.getBarrierOpen(), "every random barrier is closed");
                    }
                    if (element.getType() == ScenarioRoadElementType.POTHOLE
                        || element.getType() == ScenarioRoadElementType.STATIC_OBSTACLE
                        || element.getType() == ScenarioRoadElementType.BARRIER) {
                        hazardLanes.add(element.getLane());
                    }
                }
                if (actor != null) {
                    actorTypes.add(actor.getType());
                    if (actor.getType() == ScenarioActorType.FRONT_VEHICLE) {
                        vehicleLanes.add(actor.getLane());
                    }
                }
                if (action.getType() == ScenarioTimedActionType.SET_RAIN) {
                    assertFalse(raining, "rain set while it already rains");
                    raining = true;
                    weather.add(action.getType());
                } else if (action.getType() == ScenarioTimedActionType.CLEAR_WEATHER) {
                    assertTrue(raining, "weather cleared while it is dry");
                    raining = false;
                    weather.add(action.getType());
                }
            }
        }

        assertEquals(EnumSet.allOf(ScenarioRoadElementType.class), elementTypes);
        assertEquals(EnumSet.of(ScenarioActorType.FRONT_VEHICLE, ScenarioActorType.PEDESTRIAN), actorTypes);
        assertEquals(EnumSet.of(Lane.LEFT, Lane.CENTER, Lane.RIGHT), hazardLanes);
        assertEquals(EnumSet.of(Lane.CENTER, Lane.RIGHT), vehicleLanes, "random vehicles keep out of the left lane");
        assertEquals(EnumSet.of(ScenarioTimedActionType.SET_RAIN, ScenarioTimedActionType.CLEAR_WEATHER), weather);
    }
}
