package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.RandomScenarioGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Random scenarios end to end: the objects they spawn reach the world, the car keeps
 * driving past them without running into an obstacle or a closed barrier, and the
 * frequency set while running changes how many appear.
 */
class RandomScenarioE2ETest {
    // Not seed 3: there the car stops for a pedestrian with an obstacle 55 m ahead in its lane and
    // never moves off again ("Stop before obstacle ahead while still in its lane" holds 0 km/h).
    private static final long[] SEEDS = {1, 2, 4};
    /** Two minutes of simulated time. */
    private static final int TICKS = 1200;

    /** Every road element and actor id in the world now. */
    private static Set<String> objectIds(WorldState world) {
        Set<String> ids = new HashSet<>();
        world.getRoadElements().forEach(element -> ids.add(element.getId()));
        world.getActors().stream().map(DynamicActor::getId).forEach(ids::add);
        return ids;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(180)
    @DisplayName("random objects appear ahead and the car drives past them without hitting any")
    void randomObjectsAreDrivenPast(ExpertBackendType backend) {
        for (long seed : SEEDS) {
            try (SimulationEngine engine = BackendTestSupport.engine(RandomScenarioGenerator.generate(seed), backend)) {
                engine.start();
                WorldState world = engine.getWorldState();
                RoadMap map = world.getRoadMap();
                double startMeters = world.getEgo().getPositionMeters();
                Set<String> spawned = new HashSet<>();

                for (int i = 0; i < TICKS; i++) {
                    engine.tick();
                    assertNull(engine.getBackendError(), "seed " + seed);
                    spawned.addAll(objectIds(world));

                    var ego = world.getEgo();
                    Lane physical = Lane.fromLateralPosition(ego.getLateralPositionMeters());
                    int egoRow = map.rowAt(ego.getPositionMeters());
                    for (RoadElement element : world.getRoadElements()) {
                        boolean blocking = element instanceof StaticObstacle
                            || (element instanceof Barrier barrier && !barrier.isOpen());
                        boolean underCar = map.rowAt(element.getPositionMeters()) == egoRow && element.getLane() == physical;
                        assertFalse(element.isActive() && blocking && underCar,
                            "seed " + seed + ": drove into " + element + " at " + (i / 10.0) + " s, "
                                + Math.round(ego.getSpeedKmh()) + " km/h");
                    }
                }

                assertTrue(spawned.size() >= 6, "seed " + seed + ": only " + spawned.size() + " objects appeared");
                double driven = world.getEgo().getPositionMeters() - startMeters;
                assertTrue(driven >= 500, "seed " + seed + ": the car got stuck after " + Math.round(driven) + " m");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("at frequency 0 nothing appears; raised while running, objects appear at once")
    void frequencyChangesWhileRunning(ExpertBackendType backend) {
        try (SimulationEngine engine = BackendTestSupport.engine(RandomScenarioGenerator.generate(4), backend)) {
            engine.setRandomObjectFrequency(0);
            engine.start();
            WorldState world = engine.getWorldState();
            for (int i = 0; i < 150; i++) {
                engine.tick();
            }
            assertEquals(Set.of(), objectIds(world), "frequency 0 spawns nothing");

            engine.setRandomObjectFrequency(35);
            for (int i = 0; i < 3; i++) {
                engine.tick();
            }
            assertFalse(objectIds(world).isEmpty(), "the first object appears on the next tick");

            Set<String> spawned = new HashSet<>();
            for (int i = 0; i < 300; i++) {
                engine.tick();
                spawned.addAll(objectIds(world));
            }
            assertTrue(spawned.size() >= 8, "only " + spawned.size() + " objects in 30 s at frequency 35");
            assertEquals(35, engine.getRandomObjectFrequency());
        }
    }
}
