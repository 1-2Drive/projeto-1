package com.one2drive.projeto1.drools.facts;

public class CurrentSpeedLimit {
    /**
     * sourceId of the fact every session starts with: no sign has been seen yet, so the
     * value is only the "unrestricted" placeholder and must not be mistaken for a limit
     * a sign actually posted.
     */
    public static final String DEFAULT_SOURCE = "default";

    private int limitKmh;
    private String sourceId;
    private long timestamp;

    public CurrentSpeedLimit(int limitKmh, String sourceId, long timestamp) {
        this.limitKmh = limitKmh;
        this.sourceId = sourceId;
        this.timestamp = timestamp;
    }

    public int getLimitKmh() {
        return limitKmh;
    }

    public void setLimitKmh(int limitKmh) {
        this.limitKmh = limitKmh;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "CurrentSpeedLimit{limitKmh=" + limitKmh + ", sourceId='" + sourceId + "'}";
    }
}
