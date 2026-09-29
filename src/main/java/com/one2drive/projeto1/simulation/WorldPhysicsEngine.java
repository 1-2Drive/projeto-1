package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.EgoVehicleState;

public class WorldPhysicsEngine {
    private final SimulationConfig config;

    public WorldPhysicsEngine(SimulationConfig config) {
        this.config = config;
    }

    public void updateWorld(WorldState worldState, double dtSeconds) {
        EgoVehicleState ego = worldState.getEgo();
        ego.advance(dtSeconds);
        if (ego.getPositionMeters() > config.getRoadLengthMeters()) {
            ego.setPositionMeters(config.getRoadLengthMeters());
            ego.setSpeedMps(0);
            ego.setAccelerationMps2(0);
        }

        for (DynamicActor actor : worldState.getActors()) {
            if (!actor.isActive()) {
                continue;
            }
            actor.advance(dtSeconds);
        }
    }
}
