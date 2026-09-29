package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.HazardSeverity;

public class ObstacleDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String obstacleId;
    private final HazardSeverity severity;

    public ObstacleDetectedEvent(long timestamp, String sourceId, double distanceMeters, String obstacleId, HazardSeverity severity) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.obstacleId = obstacleId;
        this.severity = severity;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getObstacleId() {
        return obstacleId;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        return "ObstacleDetectedEvent{distanceMeters=" + distanceMeters + ", severity=" + severity + "}";
    }
}
