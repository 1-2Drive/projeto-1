package com.one2drive.projeto1.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class ScenarioLibrary {
    private final List<ScenarioDefinition> scenarios;

    public ScenarioLibrary(List<ScenarioDefinition> scenarios) {
        this.scenarios = scenarios;
    }

    public static ScenarioLibrary loadFromClasspath(String path) {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = ScenarioLibrary.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Scenario file not found: " + path);
            }
            List<ScenarioDefinition> list = mapper.readValue(in, new TypeReference<>() {
            });
            return new ScenarioLibrary(list);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load scenarios from " + path, e);
        }
    }

    public List<ScenarioDefinition> getScenarios() {
        return scenarios;
    }

    public Optional<ScenarioDefinition> findById(String id) {
        return scenarios.stream().filter(s -> s.getId().equals(id)).findFirst();
    }
}
