package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;

public class ObstacleDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String obstacleId;
    private final HazardSeverity severity;
    private final Lane lane;

    public ObstacleDetectedEvent(long timestamp,
                                 String sourceId,
                                 double distanceMeters,
                                 String obstacleId,
                                 HazardSeverity severity,
                                 Lane lane) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.obstacleId = obstacleId;
        this.severity = severity;
        this.lane = lane;
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

    /** Which lane the obstacle occupies, so rules can tell the vehicle has already avoided it. */
    public Lane getLane() {
        return lane;
    }

    @Override
    public String toString() {
        return "ObstacleDetectedEvent{distanceMeters=" + distanceMeters + ", lane=" + lane
            + ", severity=" + severity + "}";
    }
}
