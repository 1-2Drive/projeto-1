package com.one2drive.projeto1.scenario;

import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.TrafficLightState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Draws random objects ahead of the vehicle as it drives: a random kind, lane and
 * parameters, one spawn every so many metres travelled. The frequency, from 0 (none) to
 * 100 (one object every 8.75 m on average; 35 gives one every 25 m), sets the spacing and
 * can change at any time.
 * Spacing by distance rather than time keeps a stopped vehicle from piling objects up in
 * front of it. The same seed, driven the same way at the same frequency, draws the same objects.
 */
public final class RandomObjectSpawner {
    public static final int MIN_FREQUENCY = 0;
    public static final int MAX_FREQUENCY = 100;
    /** About one object every 90 m. */
    public static final int DEFAULT_FREQUENCY = 10;
    /** Mean spacing at the highest frequency; below it the spacing grows as 100 / frequency. */
    static final double DENSEST_SPACING_METERS = 8.75;
    /** Distance ahead of the vehicle where an object appears: around the camera range, so it is seen coming. */
    static final int MIN_AHEAD_METERS = 90;
    static final int MAX_AHEAD_METERS = 160;

    private static final Lane[] LANES = {Lane.LEFT, Lane.CENTER, Lane.RIGHT};
    private static final int[] SPEED_LIMITS_KMH = {30, 50, 70, 90};

    /** What can appear, with its relative frequency. */
    private enum Spawn {
        SPEED_LIMIT(3),
        STOP_SIGN(2),
        TRAFFIC_LIGHT(2),
        POTHOLE(3),
        OBSTACLE(3),
        BARRIER(2),
        CROSSWALK(2),
        PEDESTRIAN(2),
        VEHICLE(4),
        WEATHER(1);

        private static final int TOTAL_WEIGHT = Arrays.stream(values()).mapToInt(s -> s.weight).sum();

        private final int weight;

        Spawn(int weight) {
            this.weight = weight;
        }
    }

    private final Random random;
    private int frequency;
    private double lastSpawnMeters = Double.NaN;
    /** This gap as a fraction of the mean spacing, drawn once so a frequency change only rescales it. */
    private double gapFactor;

    public RandomObjectSpawner(long seed, int frequency) {
        this.random = new Random(seed);
        this.frequency = checkFrequency(frequency);
        this.gapFactor = nextGapFactor();
    }

    public static int checkFrequency(int frequency) {
        if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) {
            throw new IllegalArgumentException("Random object frequency must be between "
                + MIN_FREQUENCY + " and " + MAX_FREQUENCY + ", got " + frequency);
        }
        return frequency;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = checkFrequency(frequency);
    }

    /** Mean distance between two spawns; infinite at frequency 0. */
    static double meanSpacingMeters(int frequency) {
        return frequency == 0 ? Double.POSITIVE_INFINITY : DENSEST_SPACING_METERS * MAX_FREQUENCY / frequency;
    }

    /**
     * What appears now that the vehicle has travelled {@code travelledMeters}: nothing, or one
     * object (a crosswalk may bring its pedestrian along). The first object comes at once.
     * The actions are due immediately; their trigger time is not used.
     */
    public List<ScenarioTimedAction> due(double travelledMeters, boolean raining) {
        if (frequency == 0) {
            return List.of();
        }
        if (!Double.isNaN(lastSpawnMeters)
            && travelledMeters - lastSpawnMeters < gapFactor * meanSpacingMeters(frequency)) {
            return List.of();
        }
        lastSpawnMeters = travelledMeters;
        gapFactor = nextGapFactor();
        return draw(raining);
    }

    /** Between half and one and a half times the mean spacing. */
    private double nextGapFactor() {
        return 0.5 + random.nextDouble();
    }

    private List<ScenarioTimedAction> draw(boolean raining) {
        List<ScenarioTimedAction> actions = new ArrayList<>();
        int ahead = MIN_AHEAD_METERS + random.nextInt(MAX_AHEAD_METERS - MIN_AHEAD_METERS + 1);
        switch (pickSpawn()) {
            // Signs, lights, stop signs and crosswalks govern the whole road; they stand on the right.
            case SPEED_LIMIT -> {
                ScenarioRoadElementDefinition sign = element(ScenarioRoadElementType.SPEED_LIMIT, ahead, Lane.RIGHT);
                sign.setSpeedLimitKmh(SPEED_LIMITS_KMH[random.nextInt(SPEED_LIMITS_KMH.length)]);
                actions.add(addElement(sign));
            }
            case STOP_SIGN -> actions.add(addElement(element(ScenarioRoadElementType.STOP_SIGN, ahead, Lane.RIGHT)));
            case TRAFFIC_LIGHT -> {
                ScenarioRoadElementDefinition light = element(ScenarioRoadElementType.TRAFFIC_LIGHT, ahead, Lane.RIGHT);
                light.setTrafficLightState(pick(TrafficLightState.values()));
                actions.add(addElement(light));
            }
            // Hazards block only their own lane.
            case POTHOLE -> {
                ScenarioRoadElementDefinition pothole = element(ScenarioRoadElementType.POTHOLE, ahead, pick(LANES));
                pothole.setSeverity(pick(HazardSeverity.values()));
                actions.add(addElement(pothole));
            }
            case OBSTACLE -> {
                ScenarioRoadElementDefinition obstacle = element(ScenarioRoadElementType.STATIC_OBSTACLE, ahead, pick(LANES));
                obstacle.setSeverity(random.nextBoolean() ? HazardSeverity.HIGH : HazardSeverity.MEDIUM);
                actions.add(addElement(obstacle));
            }
            case BARRIER -> {
                ScenarioRoadElementDefinition barrier = element(ScenarioRoadElementType.BARRIER, ahead, pick(LANES));
                barrier.setBarrierOpen(false);
                actions.add(addElement(barrier));
            }
            // A crosswalk, half the time with a pedestrian already stepping onto it.
            case CROSSWALK -> {
                actions.add(addElement(element(ScenarioRoadElementType.CROSSWALK, ahead, Lane.CENTER)));
                if (random.nextBoolean()) {
                    actions.add(addActor(pedestrian(ahead)));
                }
            }
            case PEDESTRIAN -> actions.add(addActor(pedestrian(ahead)));
            case VEHICLE -> {
                ScenarioActorDefinition vehicle = actor(ScenarioActorType.FRONT_VEHICLE, ahead, pick(LANES));
                vehicle.setSpeedKmh(10 + random.nextInt(31));
                actions.add(addActor(vehicle));
            }
            case WEATHER -> actions.add(action(raining ? ScenarioTimedActionType.CLEAR_WEATHER : ScenarioTimedActionType.SET_RAIN));
        }
        return actions;
    }

    /** A pedestrian at the road edge, crossing towards the far side. */
    private ScenarioActorDefinition pedestrian(int ahead) {
        ScenarioActorDefinition pedestrian =
            actor(ScenarioActorType.PEDESTRIAN, ahead, random.nextBoolean() ? Lane.LEFT : Lane.RIGHT);
        pedestrian.setSpeedKmh(2 + random.nextInt(3));
        return pedestrian;
    }

    private Spawn pickSpawn() {
        int roll = random.nextInt(Spawn.TOTAL_WEIGHT);
        for (Spawn spawn : Spawn.values()) {
            roll -= spawn.weight;
            if (roll < 0) {
                return spawn;
            }
        }
        throw new IllegalStateException("Spawn weights do not add up");
    }

    private <T> T pick(T[] options) {
        return options[random.nextInt(options.length)];
    }

    private static ScenarioRoadElementDefinition element(ScenarioRoadElementType type, int ahead, Lane lane) {
        ScenarioRoadElementDefinition element = new ScenarioRoadElementDefinition();
        element.setType(type);
        element.setPositionMeters(ahead);
        element.setLane(lane);
        return element;
    }

    private static ScenarioActorDefinition actor(ScenarioActorType type, int ahead, Lane lane) {
        ScenarioActorDefinition actor = new ScenarioActorDefinition();
        actor.setType(type);
        actor.setPositionMeters(ahead);
        actor.setLane(lane);
        return actor;
    }

    private static ScenarioTimedAction addElement(ScenarioRoadElementDefinition element) {
        ScenarioTimedAction action = action(ScenarioTimedActionType.ADD_ROAD_ELEMENT);
        action.setRoadElement(element);
        return action;
    }

    private static ScenarioTimedAction addActor(ScenarioActorDefinition actor) {
        ScenarioTimedAction action = action(ScenarioTimedActionType.ADD_ACTOR);
        action.setActor(actor);
        return action;
    }

    private static ScenarioTimedAction action(ScenarioTimedActionType type) {
        ScenarioTimedAction action = new ScenarioTimedAction();
        action.setType(type);
        return action;
    }
}
