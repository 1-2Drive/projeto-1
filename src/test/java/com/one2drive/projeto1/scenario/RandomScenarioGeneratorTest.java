package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.config.SimulationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomScenarioGeneratorTest {

    @Test
    @DisplayName("the same seed draws the same start, and the seed goes on to draw the objects")
    void seedDecidesTheScenario() {
        ScenarioDefinition first = RandomScenarioGenerator.generate(42);
        ScenarioDefinition again = RandomScenarioGenerator.generate(42);

        assertEquals("RANDOM-42", first.getId());
        assertEquals(42L, first.getRandomObjectSeed());
        assertEquals(first.getInitialLane(), again.getInitialLane());
        assertEquals(first.getInitialSpeedKmh(), again.getInitialSpeedKmh());
        assertEquals(first.getInitialWeather(), again.getInitialWeather());
        // An empty road: every object comes from the spawner.
        assertTrue(first.getTimedActions().isEmpty());
        assertEquals(0, first.occupiedCellCount());
        assertNotNull(ScenarioFactory.buildWorld(first, SimulationConfig.defaultConfig()));
    }

    @Test
    @DisplayName("RANDOM draws a new seed, RANDOM-<seed> repeats one, other ids are not random scenarios")
    void idsResolveToRandomScenarios() {
        String drawn = RandomScenarioGenerator.forId("RANDOM").orElseThrow().getId();
        assertTrue(drawn.matches("RANDOM-\\d+"), drawn);
        assertEquals(7L, RandomScenarioGenerator.forId("RANDOM-7").orElseThrow().getRandomObjectSeed());

        assertTrue(RandomScenarioGenerator.forId("S1").isEmpty());
        assertTrue(RandomScenarioGenerator.forId("RANDOM-abc").isEmpty());
        assertTrue(RandomScenarioGenerator.forId(null).isEmpty());
    }
}
