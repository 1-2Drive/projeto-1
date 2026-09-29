package com.one2drive.projeto1.api.model;

public record ApiScenarioSummary(
    String id,
    String name,
    double initialSpeedKmh,
    String initialWeather,
    int roadElementCount,
    int actorCount,
    int timedActionCount
) {
}
