package com.one2drive.projeto1.drools.events;

import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;

public class PotholeDetectedEvent extends BaseEvent {
    private final double distanceMeters;
    private final String potholeId;
    private final HazardSeverity severity;
    private final Lane lane;

    public PotholeDetectedEvent(long timestamp,
                                String sourceId,
                                double distanceMeters,
                                String potholeId,
                                HazardSeverity severity,
                                Lane lane) {
        super(timestamp, sourceId);
        this.distanceMeters = distanceMeters;
        this.potholeId = potholeId;
        this.severity = severity;
        this.lane = lane;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public String getPotholeId() {
        return potholeId;
    }

    public HazardSeverity getSeverity() {
        return severity;
    }

    /** Which lane the hazard occupies, so rules can tell the vehicle has already avoided it. */
    public Lane getLane() {
        return lane;
    }

    @Override
    public String toString() {
        return "PotholeDetectedEvent{distanceMeters=" + distanceMeters + ", lane=" + lane
            + ", severity=" + severity + "}";
    }
}
