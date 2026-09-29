package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiCommandResponse;
import com.one2drive.projeto1.api.model.ApiEnvironmentUpdateRequest;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/environment")
public class EnvironmentController {
    private final SimulationRuntimeService runtime;

    public EnvironmentController(SimulationRuntimeService runtime) {
        this.runtime = runtime;
    }

    @PostMapping
    public ResponseEntity<ApiCommandResponse> updateEnvironment(@Valid @RequestBody ApiEnvironmentUpdateRequest request) {
        if (request.getRain() != null) {
            runtime.engine().setRain(request.getRain());
        }

        if (request.getVisibilityFactor() != null || request.getGripFactor() != null) {
            double currentVisibility = runtime.engine().getWorldState().getWeatherState().getVisibilityFactor();
            double currentGrip = runtime.engine().getWorldState().getWeatherState().getGripFactor();
            runtime.engine().setWeatherFactors(
                request.getVisibilityFactor() != null ? request.getVisibilityFactor() : currentVisibility,
                request.getGripFactor() != null ? request.getGripFactor() : currentGrip
            );
        }

        runtime.broadcastSnapshot();

        return ResponseEntity.ok(new ApiCommandResponse(true, "Environment updated"));
    }
}
