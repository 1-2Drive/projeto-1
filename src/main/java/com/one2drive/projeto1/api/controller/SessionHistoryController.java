package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiSessionDetail;
import com.one2drive.projeto1.api.model.ApiSessionSummary;
import com.one2drive.projeto1.api.model.ApiWhyAnswer;
import com.one2drive.projeto1.api.model.ApiWhyRequest;
import com.one2drive.projeto1.api.service.SessionExplanationService;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import com.one2drive.projeto1.history.SimulationSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history/sessions")
public class SessionHistoryController {
    private final SimulationRuntimeService runtime;
    private final SessionExplanationService explanations;

    public SessionHistoryController(SimulationRuntimeService runtime, SessionExplanationService explanations) {
        this.runtime = runtime;
        this.explanations = explanations;
    }

    @GetMapping
    public List<ApiSessionSummary> list() {
        return runtime.sessions().stream().map(this::summary).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiSessionDetail> get(@PathVariable String id) {
        return ResponseEntity.ok(detail(runtime.session(id)));
    }

    @PostMapping("/{id}/why")
    public ResponseEntity<ApiWhyAnswer> why(@PathVariable String id, @Valid @RequestBody ApiWhyRequest request) {
        return ResponseEntity.ok(explanations.answer(runtime.session(id), request.question(), request.timestampMillis(), request.mode()));
    }

    private ApiSessionSummary summary(SimulationSession session) {
        return new ApiSessionSummary(session.id(), session.startedAt(), session.stoppedAt(), session.durationMillis(),
            session.startedAtSimulationMillis(),
            session.scenarioId(), session.backend(), session.events().size(), session.decisionSnapshots().size());
    }

    private ApiSessionDetail detail(SimulationSession session) {
        return new ApiSessionDetail(session.id(), session.startedAt(), session.stoppedAt(), session.durationMillis(),
            session.startedAtSimulationMillis(),
            session.scenarioId(), session.backend(), session.initialWorld(), session.initialWorldObjects(),
            session.events(), session.vehicleSamples(), session.decisionSnapshots(), session.decisionEventSequences());
    }
}
