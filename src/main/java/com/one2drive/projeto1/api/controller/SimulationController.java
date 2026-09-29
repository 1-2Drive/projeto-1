package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiCommandResponse;
import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiSystemStatus;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    private final SimulationRuntimeService runtime;

    public SimulationController(SimulationRuntimeService runtime) {
        this.runtime = runtime;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiCommandResponse> start() {
        runtime.start();
        return ResponseEntity.ok(new ApiCommandResponse(true, "Simulation started"));
    }

    @PostMapping("/pause")
    public ResponseEntity<ApiCommandResponse> pause() {
        runtime.pause();
        return ResponseEntity.ok(new ApiCommandResponse(true, "Simulation paused"));
    }

    @PostMapping("/resume")
    public ResponseEntity<ApiCommandResponse> resume() {
        runtime.resume();
        return ResponseEntity.ok(new ApiCommandResponse(true, "Simulation resumed"));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiCommandResponse> stop() {
        runtime.stop();
        return ResponseEntity.ok(new ApiCommandResponse(true, "Simulation stopped"));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiCommandResponse> reset() {
        runtime.reset();
        return ResponseEntity.ok(new ApiCommandResponse(true, "Simulation reset to baseline scenario"));
    }

    @GetMapping("/state")
    public ResponseEntity<ApiSimulationSnapshot> state() {
        return ResponseEntity.ok(runtime.snapshot());
    }

    @GetMapping("/status")
    public ResponseEntity<ApiSystemStatus> status() {
        return ResponseEntity.ok(runtime.systemStatus());
    }
}
