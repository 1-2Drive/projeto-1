package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.WeatherCondition;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A scenario drawn at random: an empty road, a random start (lane, speed, weather) and
 * random objects that keep appearing ahead of the vehicle as it drives
 * ({@link RandomObjectSpawner}). The same seed always draws the same scenario, and its id
 * {@code RANDOM-<seed>} loads it again.
 */
public final class RandomScenarioGenerator {
    /** Id offered in the scenario list: each load draws a new seed. */
    public static final String ID = "RANDOM";
    public static final String NAME = "Random objects";

    private static final Lane[] LANES = {Lane.LEFT, Lane.CENTER, Lane.RIGHT};
    private static final int[] INITIAL_SPEEDS_KMH = {50, 60, 70, 80, 90};
    private static final long MAX_DRAWN_SEED = 1_000_000L;

    private RandomScenarioGenerator() {
    }

    /**
     * The random scenario {@code id} names: a new seed for {@link #ID}, the given seed for
     * {@code RANDOM-<seed>}. Empty for any other id.
     */
    public static Optional<ScenarioDefinition> forId(String id) {
        if (ID.equals(id)) {
            return Optional.of(generate(ThreadLocalRandom.current().nextLong(1, MAX_DRAWN_SEED)));
        }
        String prefix = ID + "-";
        if (id == null || !id.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(generate(Long.parseLong(id.substring(prefix.length()))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static ScenarioDefinition generate(long seed) {
        Random random = new Random(seed);
        ScenarioDefinition scenario = new ScenarioDefinition();
        scenario.setId(ID + "-" + seed);
        scenario.setName(NAME + " (seed " + seed + ")");
        scenario.setInitialLane(LANES[random.nextInt(LANES.length)]);
        scenario.setInitialSpeedKmh(INITIAL_SPEEDS_KMH[random.nextInt(INITIAL_SPEEDS_KMH.length)]);
        scenario.setInitialWeather(random.nextInt(4) == 0 ? WeatherCondition.RAIN : WeatherCondition.CLEAR);
        scenario.setRandomObjectSeed(seed);
        return scenario;
    }
}
