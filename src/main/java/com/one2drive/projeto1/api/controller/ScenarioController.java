package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiCommandResponse;
import com.one2drive.projeto1.api.model.ApiScenarioSummary;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private final ScenarioLibrary scenarioLibrary;
    private final SimulationRuntimeService runtime;

    public ScenarioController(ScenarioLibrary scenarioLibrary, SimulationRuntimeService runtime) {
        this.scenarioLibrary = scenarioLibrary;
        this.runtime = runtime;
    }

    @GetMapping
    public ResponseEntity<List<ApiScenarioSummary>> list() {
        return ResponseEntity.ok(
            scenarioLibrary.getScenarios().stream()
                .map(s -> new ApiScenarioSummary(
                    s.getId(),
                    s.getName(),
                    s.getInitialSpeedKmh(),
                    s.getInitialWeather().name(),
                    s.getRoadElements().size(),
                    s.getActors().size(),
                    s.getTimedActions().size()
                ))
                .toList()
        );
    }

    @PostMapping("/{id}/load")
    public ResponseEntity<ApiCommandResponse> load(@PathVariable("id") String id) {
        runtime.loadScenario(id);
        return ResponseEntity.ok(new ApiCommandResponse(true, "Scenario loaded: " + id));
    }
}
