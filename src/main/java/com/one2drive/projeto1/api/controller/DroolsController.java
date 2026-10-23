package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiDecisionView;
import com.one2drive.projeto1.api.model.ApiTimelineEntry;
import com.one2drive.projeto1.api.model.ApiWorkingMemoryRow;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drools")
public class DroolsController {
    private final SimulationRuntimeService runtime;

    public DroolsController(SimulationRuntimeService runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/working-memory")
    public ResponseEntity<List<ApiWorkingMemoryRow>> workingMemory() {
        return ResponseEntity.ok(runtime.snapshot().workingMemory());
    }

    @GetMapping("/decision")
    public ResponseEntity<ApiDecisionView> decision() {
        return ResponseEntity.ok(runtime.snapshot().currentDecision());
    }

    @GetMapping("/rules/recent")
    public ResponseEntity<List<String>> recentRules() {
        return ResponseEntity.ok(runtime.snapshot().recentRules());
    }

    @GetMapping("/timeline/recent")
    public ResponseEntity<List<ApiTimelineEntry>> timeline() {
        return ResponseEntity.ok(runtime.snapshot().recentTimeline());
    }
}
