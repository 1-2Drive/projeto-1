package com.one2drive.projeto1.drools.facts;

/**
 * The speed limit sign the camera has read ahead but the vehicle has not reached yet. A
 * limit starts at its sign, so until then it is only pending: the rules slow down in time
 * for it, and it replaces {@link CurrentSpeedLimit} when the vehicle gets to the sign.
 */
public class PendingSpeedLimit {
    /** signId while no sign is waiting ahead. */
    public static final String NONE = "none";

    private String signId;
    private int limitKmh;
    /** Where the sign stands, in metres travelled by the vehicle. */
    private double positionMeters;
    /** Time of the last camera reading taken into account. */
    private long timestamp;

    public PendingSpeedLimit() {
        this.signId = NONE;
        this.positionMeters = Double.POSITIVE_INFINITY;
        this.timestamp = -1L;
    }

    public String getSignId() {
        return signId;
    }

    public void setSignId(String signId) {
        this.signId = signId;
    }

    public int getLimitKmh() {
        return limitKmh;
    }

    public void setLimitKmh(int limitKmh) {
        this.limitKmh = limitKmh;
    }

    public double getPositionMeters() {
        return positionMeters;
    }

    public void setPositionMeters(double positionMeters) {
        this.positionMeters = positionMeters;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** Forgets the sign but keeps the reading time, so an older reading cannot bring it back. */
    public void clear() {
        this.signId = NONE;
        this.limitKmh = 0;
        this.positionMeters = Double.POSITIVE_INFINITY;
    }

    @Override
    public String toString() {
        return "PendingSpeedLimit{limitKmh=" + limitKmh + ", signId='" + signId
            + "', positionMeters=" + positionMeters + "}";
    }
}
