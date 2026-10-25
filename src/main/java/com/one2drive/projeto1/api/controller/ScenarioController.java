package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiCommandResponse;
import com.one2drive.projeto1.api.model.ApiScenarioSummary;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import com.one2drive.projeto1.scenario.RandomScenarioGenerator;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private static final Set<String> VISIBLE_SCENARIOS = Set.of(
        "S1",
        "S12_PRESENTATION_DEMO",
        "S15_URBAN_CONTROLS_DEMO",
        "S16_HAZARD_OVERTAKE_DEMO",
        "S17_WEATHER_EMERGENCY_DEMO",
        "S18_MIXED_SAFETY_DEMO"
    );
    private final ScenarioLibrary scenarioLibrary;
    private final SimulationRuntimeService runtime;

    public ScenarioController(ScenarioLibrary scenarioLibrary, SimulationRuntimeService runtime) {
        this.scenarioLibrary = scenarioLibrary;
        this.runtime = runtime;
    }

    @GetMapping
    public ResponseEntity<List<ApiScenarioSummary>> list() {
        // The random scenario is drawn anew on each load: one draw stands in for its figures.
        ApiScenarioSummary random = summary(RandomScenarioGenerator.ID, RandomScenarioGenerator.NAME,
            RandomScenarioGenerator.generate(0));
        return ResponseEntity.ok(Stream.concat(
            scenarioLibrary.getScenarios().stream()
                .filter(s -> VISIBLE_SCENARIOS.contains(s.getId()))
                .map(s -> summary(s.getId(), s.getName(), s)),
            Stream.of(random)
        ).toList());
    }

    @PostMapping("/{id}/load")
    public ResponseEntity<ApiCommandResponse> load(@PathVariable("id") String id) {
        String loadedId = runtime.loadScenario(id);
        return ResponseEntity.ok(new ApiCommandResponse(true, "Scenario loaded: " + loadedId));
    }

    /** How often random objects appear in the random scenario, from 0 (none) to 100; applies at once. */
    @PostMapping("/random/frequency/{frequency}")
    public ResponseEntity<ApiCommandResponse> setRandomObjectFrequency(@PathVariable("frequency") int frequency) {
        runtime.setRandomObjectFrequency(frequency);
        return ResponseEntity.ok(new ApiCommandResponse(true, "Random object frequency: " + frequency));
    }

    private static ApiScenarioSummary summary(String id, String name, ScenarioDefinition s) {
        return new ApiScenarioSummary(
            id,
            name,
            s.getInitialSpeedKmh(),
            s.getInitialWeather().name(),
            s.occupiedCellCount(),
            s.getActors().size(),
            s.getTimedActions().size()
        );
    }
}
