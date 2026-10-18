package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.OtherVehicle;
import com.one2drive.projeto1.domain.Pedestrian;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The other vehicles give way to pedestrians too (Art. 103 CE). In S11 a pedestrian starts
 * crossing ahead of the vehicle the ego follows; that vehicle used to drive through it at
 * constant speed. It must stop short of the pedestrian and move on once the crossing is over.
 */
class OtherVehiclesGiveWayToPedestriansE2ETest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(ExpertBackendType.class)
    @Timeout(60)
    @DisplayName("S11: the vehicle ahead stops for the crossing pedestrian, then drives on")
    void theVehicleAheadStopsForTheCrossingPedestrian(ExpertBackendType backend) {
        ScenarioDefinition scenario = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json")
            .findById("S11").orElseThrow();
        try (SimulationEngine engine = BackendTestSupport.engine(scenario, backend)) {
            engine.start();
            boolean sawBoth = false;
            boolean ranOver = false;
            boolean stoppedForIt = false;
            boolean droveOnAfterwards = false;
            for (int i = 0; i < 250; i++) {
                engine.tick();
                OtherVehicle vehicle = null;
                Pedestrian pedestrian = null;
                for (DynamicActor actor : engine.getWorldState().getActors()) {
                    if (actor instanceof OtherVehicle v && v.isActive()) {
                        vehicle = v;
                    } else if (actor instanceof Pedestrian p && p.isActive()) {
                        pedestrian = p;
                    }
                }
                if (vehicle != null && stoppedForIt && vehicle.getSpeedMps() > 1.0) {
                    // The pedestrian is taken out of the world once it has crossed.
                    droveOnAfterwards = true;
                }
                if (vehicle == null || pedestrian == null) {
                    continue;
                }
                sawBoth = true;
                boolean inItsLane = Math.abs(pedestrian.getLateralPositionMeters() - vehicle.getLane().centreMeters()) < 1.75;
                if (inItsLane && Math.abs(pedestrian.getPositionMeters() - vehicle.getPositionMeters()) < 2.5) {
                    ranOver = true;
                }
                if (vehicle.getSpeedMps() < 0.1 && pedestrian.getPositionMeters() > vehicle.getPositionMeters()) {
                    stoppedForIt = true;
                }
            }
            assertTrue(sawBoth, "S11 must put the pedestrian ahead of the other vehicle");
            assertFalse(ranOver, "the other vehicle drove through the pedestrian");
            assertTrue(stoppedForIt, "the other vehicle must stop for the crossing pedestrian");
            assertTrue(droveOnAfterwards, "the other vehicle must drive on once the crossing is over");
        }
    }
}
