package com.one2drive.projeto1.domain;

public class Roundabout extends RoadStructure {
    private final double recommendedApproachSpeedKmh;

    public Roundabout(double positionMeters, Lane lane, double recommendedApproachSpeedKmh) {
        super(positionMeters, lane);
        this.recommendedApproachSpeedKmh = recommendedApproachSpeedKmh;
    }

    public double getRecommendedApproachSpeedKmh() {
        return recommendedApproachSpeedKmh;
    }

    @Override
    public String toString() {
        return super.toString() + " recommendedApproachSpeedKmh=" + recommendedApproachSpeedKmh;
    }
}
