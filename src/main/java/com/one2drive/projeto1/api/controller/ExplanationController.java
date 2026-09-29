package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiTimelineEntry;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/explanations")
public class ExplanationController {
    private final SimulationRuntimeService runtime;

    public ExplanationController(SimulationRuntimeService runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ApiTimelineEntry>> recent() {
        return ResponseEntity.ok(runtime.snapshot().recentTimeline());
    }
}
