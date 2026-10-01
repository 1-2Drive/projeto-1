package com.one2drive.projeto1.drools.facts;

import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;

/**
 * Latest static obstacle perceived by the front camera, including the lane it sits
 * in so avoidance rules can tell that the vehicle has already steered clear of it.
 */
public class ObstacleStateFact {
    private String obstacleId;
    private double distanceMeters;
    private HazardSeverity severity;
    private Lane lane;
    private long timestamp;

    public ObstacleStateFact(String obstacleId,
                             double distanceMeters,
                             HazardSeverity severity,
                             Lane lane,
                             long timestamp) {
        this.obstacleId = obstacleId;
        this.distanceMeters = distanceMeters;
        this.severity = severity;
        this.lane = lane;
        this.timestamp = timestamp;
    }

    public String getObstacleId() {
        return obstacleId;
    }

    public void setObstacleId(String obstacleId) {
        this.obstacleId = obstacleId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(HazardSeverity severity) {
        this.severity = severity;
    }

    public Lane getLane() {
        return lane;
    }

    public void setLane(Lane lane) {
        this.lane = lane;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ObstacleStateFact{obstacleId='" + obstacleId + "', distanceMeters=" + distanceMeters
            + ", lane=" + lane + ", severity=" + severity + "}";
    }
}
