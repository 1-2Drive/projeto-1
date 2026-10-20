package com.one2drive.projeto1.history;

import com.one2drive.projeto1.api.model.ApiRoadElement;
import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiWorkingMemoryRow;

import java.util.List;

/** Physical ego state captured from the simulation at a tick boundary. */
public record VehicleSample(long simulationTimeMillis, double speedKmh, double positionMeters,
                            String lane, double accelerationMps2, double lateralPositionMeters,
                            String targetLane, String laneChangeState, double laneChangeProgress,
                            String drivingState, String indicatorState, String steeringState,
                            double headingDegrees, String action, String reason, String sourceRule,
                            double recommendedSpeedKmh, ApiSimulationSnapshot.EnvironmentView environment,
                            ApiSimulationSnapshot.SensorTelemetryView sensors, List<ApiRoadElement> worldObjects,
                            List<ApiWorkingMemoryRow> workingMemory) {
}
