package com.one2drive.projeto1.drools.facts;

/**
 * Scenario cruise speed of the road the vehicle is driving on.
 *
 * <p>The rules can always slow the vehicle down (signs, weather, traffic, hazards), but
 * only a rule that knows the speed the scenario asks for can ever speed it up again.
 * The session seeds this fact from the scenario's initial speed on construction and on
 * every reset, so after a brake, an overtake or a stop the road is clear for, Drools has
 * a target to build back up to instead of freezing at the reduced speed.
 */
public class CruiseTargetFact {
    private double targetKmh;

    public CruiseTargetFact(double targetKmh) {
        this.targetKmh = targetKmh;
    }

    public double getTargetKmh() {
        return targetKmh;
    }

    public void setTargetKmh(double targetKmh) {
        this.targetKmh = targetKmh;
    }

    @Override
    public String toString() {
        return "CruiseTargetFact{targetKmh=" + targetKmh + "}";
    }
}
