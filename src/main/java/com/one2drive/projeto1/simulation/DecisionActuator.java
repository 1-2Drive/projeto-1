package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.Lane;

public class DecisionActuator {
    private final SimulationConfig config;

    public DecisionActuator(SimulationConfig config) {
        this.config = config;
    }

    public void applyDecision(WorldState worldState, DrivingDecision decision) {
        EgoVehicleState ego = worldState.getEgo();

        switch (decision.getAction()) {
            case EMERGENCY_BRAKE -> ego.setAccelerationMps2(-config.getEmergencyDecelerationMps2());
            case BRAKE, STOP, WAIT -> ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
            case REDUCE_SPEED -> ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
            case ACCELERATE -> ego.setAccelerationMps2(config.getMaxAccelerationMps2());
            case CHANGE_LANE_LEFT -> {
                ego.setAccelerationMps2(0);
                ego.setLane(ego.getLane().left());
            }
            case CHANGE_LANE_RIGHT -> {
                ego.setAccelerationMps2(0);
                ego.setLane(ego.getLane().right());
            }
            case KEEP_LANE -> ego.setAccelerationMps2(0);
            case MAINTAIN_SPEED, CONTINUE -> ego.setAccelerationMps2(0);
        }

        double recommendedSpeed = decision.getRecommendedSpeedKmh();
        if (!Double.isNaN(recommendedSpeed)) {
            double current = ego.getSpeedKmh();
            if (current > recommendedSpeed + 1) {
                ego.setAccelerationMps2(-config.getComfortableDecelerationMps2());
            } else if (current < recommendedSpeed - 1) {
                ego.setAccelerationMps2(config.getMaxAccelerationMps2() * 0.5);
            } else if (decision.getAction() == DrivingAction.MAINTAIN_SPEED || decision.getAction() == DrivingAction.CONTINUE) {
                ego.setAccelerationMps2(0);
            }
        }

        if (decision.getAction() == DrivingAction.STOP || decision.getAction() == DrivingAction.WAIT) {
            if (ego.getSpeedKmh() < 2.0) {
                ego.setSpeedMps(0);
                ego.setAccelerationMps2(0);
            }
        }

        if (decision.getAction() == DrivingAction.KEEP_LANE) {
            Lane lane = ego.getLane();
            ego.setLane(lane);
        }
    }
}
