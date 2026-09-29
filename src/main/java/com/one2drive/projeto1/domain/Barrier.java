package com.one2drive.projeto1.domain;

public class Barrier extends TrafficControl {
    private boolean open;

    public Barrier(double positionMeters, Lane lane, boolean open) {
        super(positionMeters, lane);
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public String toString() {
        return super.toString() + " open=" + open;
    }
}
