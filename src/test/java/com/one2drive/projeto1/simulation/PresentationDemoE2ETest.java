package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.decision.DecisionPriority;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.domain.PedestrianCrossingState;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The presentation demo end to end: a 50 km/h limit, then rain, then a 12 km/h vehicle
 * ahead in the ego lane, then a pedestrian crossing. The arbitration by channel must let
 * the car overtake the slow vehicle while the rain keeps its speed down, never tailgate
 * it into an emergency brake, and still stop for the pedestrian.
 */
class PresentationDemoE2ETest {
    private static final String SCENARIO_ID = "S12_PRESENTATION_DEMO";
    private static final long RAIN_FROM_MILLIS = 14_000;
    private static final long VEHICLE_FROM_MILLIS = 22_000;
    private static final long PEDESTRIAN_FROM_MILLIS = 47_000;
    private static final long CLEAR_FROM_MILLIS = 65_000;
    private static final long RUN_MILLIS = 95_000;
    /** The grip ceiling of the rain the scenario sets (grip 0.65 -> max(25, 80 * 0.65)). */
    private static final double RAIN_CEILING_KMH = 52.0;

    private record Sample(long timeMillis, double speedKmh, Lane lane, DrivingAction speedAction, int priority,
                          double frontGapMeters, boolean pedestrianCrossingAhead) {
    }

    private static List<Sample> run(ExpertBackendType backend) {
        ScenarioDefinition scenario = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json")
            .findById(SCENARIO_ID).orElseThrow();
        SimulationEngine engine = BackendTestSupport.engine(scenario, backend);
        engine.start();
        List<Sample> samples = new ArrayList<>();
        long tick = engine.getConfig().getTickMillis();
        for (long t = 0; t < RUN_MILLIS; t += tick) {
            SimulationSnapshot snapshot = engine.tick();
            DrivingDecision decision = engine.getCurrentDecision();
            WorldState world = engine.getWorldState();
            double gap = Double.POSITIVE_INFINITY;
            boolean crossingAhead = false;
            for (DynamicActor actor : world.getActors()) {
                if (actor instanceof OtherVehicle vehicle && vehicle.getLane() == world.getEgo().getLane()) {
                    double distance = world.vehicleSignedDistance(vehicle);
                    if (distance > 0) {
                        gap = Math.min(gap, distance);
                    }
                } else if (actor instanceof Pedestrian pedestrian
                    && pedestrian.getCrossingState() == PedestrianCrossingState.CROSSING
                    && world.signedDistance(pedestrian.getPositionMeters()) > 0) {
                    crossingAhead = true;
                }
            }
            samples.add(new Sample(snapshot.simTimeMillis(), snapshot.egoSpeedKmh(), world.getEgo().getLane(),
                decision.getSpeedAction(), decision.getPriority(), gap, crossingAhead));
        }
        return samples;
    }

    @ParameterizedTest
    @EnumSource(ExpertBackendType.class)
    @Timeout(240)
    @DisplayName("overtakes the slow vehicle in the rain instead of tailgating it, and still stops for the pedestrian")
    void demoOvertakesInsteadOfTailgating(ExpertBackendType backend) {
        List<Sample> samples = run(backend);

        // 1. The slow vehicle is overtaken: the car leaves the CENTER lane for LEFT before the pedestrian appears.
        assertTrue(samples.stream().anyMatch(s -> s.timeMillis() > VEHICLE_FROM_MILLIS
                && s.timeMillis() < PEDESTRIAN_FROM_MILLIS && s.lane() == Lane.LEFT),
            "the car never moved to the left lane to overtake the 12 km/h vehicle");

        // 2. Never on its bumper: no emergency brake, and the gap to a vehicle ahead in the same lane stays open.
        assertTrue(samples.stream().noneMatch(s -> s.speedAction() == DrivingAction.EMERGENCY_BRAKE),
            "an emergency brake happened: " + samples.stream()
                .filter(s -> s.speedAction() == DrivingAction.EMERGENCY_BRAKE).findFirst().orElse(null));
        double minGap = samples.stream().mapToDouble(Sample::frontGapMeters).min().orElse(Double.POSITIVE_INFINITY);
        assertTrue(minGap > 5.0, "closed to " + minGap + " m behind the front vehicle");

        // 3. The rain ceiling binds even while the following rules allow more.
        assertTrue(samples.stream().filter(s -> s.timeMillis() > RAIN_FROM_MILLIS + 4_000 && s.timeMillis() < CLEAR_FROM_MILLIS)
                .allMatch(s -> s.speedKmh() <= RAIN_CEILING_KMH + 1.0),
            "speed exceeded the rain ceiling: " + samples.stream()
                .filter(s -> s.timeMillis() > RAIN_FROM_MILLIS + 4_000 && s.timeMillis() < CLEAR_FROM_MILLIS
                    && s.speedKmh() > RAIN_CEILING_KMH + 1.0).findFirst().orElse(null));

        // 4. The pedestrian is still obeyed: a mandatory stop decision while it is crossing ahead.
        assertTrue(samples.stream().anyMatch(s -> s.pedestrianCrossingAhead()
                && s.priority() >= DecisionPriority.LEGAL_MANDATORY_STOP),
            "no mandatory stop decision while the pedestrian was crossing");

        // 5. And the run ends with the car moving again after the pedestrian has crossed.
        Sample last = samples.get(samples.size() - 1);
        assertTrue(last.speedKmh() > 5.0, "the car did not move off again after the pedestrian: " + last);
        assertEquals(RUN_MILLIS / engineTick(samples), samples.size(), "the whole demo ran");
    }

    private static long engineTick(List<Sample> samples) {
        return samples.size() > 1 ? samples.get(1).timeMillis() - samples.get(0).timeMillis() : 100;
    }
}
