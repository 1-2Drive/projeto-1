package com.one2drive.projeto1.domain;

public class SpeedLimitSign extends TrafficSign {
    private int limitKmh;

    public SpeedLimitSign(double positionMeters, Lane lane, int limitKmh) {
        super(positionMeters, lane);
        this.limitKmh = limitKmh;
    }

    public int getLimitKmh() {
        return limitKmh;
    }

    public void setLimitKmh(int limitKmh) {
        this.limitKmh = limitKmh;
    }

    @Override
    public String toString() {
        return super.toString() + " limitKmh=" + limitKmh;
    }
}
