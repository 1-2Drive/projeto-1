package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.model.ApiCreateActorRequest;
import com.one2drive.projeto1.api.model.ApiCreateRoadElementRequest;
import com.one2drive.projeto1.api.model.ApiWorldMutationResponse;
import com.one2drive.projeto1.api.service.ApiRequestMapper;
import com.one2drive.projeto1.api.service.SimulationRuntimeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/world")
public class WorldController {
    private final SimulationRuntimeService runtime;
    private final ApiRequestMapper mapper;

    public WorldController(SimulationRuntimeService runtime, ApiRequestMapper mapper) {
        this.runtime = runtime;
        this.mapper = mapper;
    }

    @PostMapping("/elements")
    public ResponseEntity<ApiWorldMutationResponse> createElement(@Valid @RequestBody ApiCreateRoadElementRequest request) {
        String id = runtime.engine().addRoadElementAheadAndGetId(mapper.toRoadElement(request));
        runtime.broadcastSnapshot();
        return ResponseEntity.ok(new ApiWorldMutationResponse(true, id, "Road element added"));
    }

    @PutMapping("/elements/{id}")
    public ResponseEntity<ApiWorldMutationResponse> updateElement(@PathVariable String id,
                                                                  @Valid @RequestBody ApiCreateRoadElementRequest request) {
        boolean updated = runtime.engine().updateRoadElementById(id, mapper.toRoadElement(request));
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        runtime.broadcastSnapshot();
        return ResponseEntity.ok(new ApiWorldMutationResponse(true, id, "Road element updated"));
    }

    @DeleteMapping("/elements/{id}")
    public ResponseEntity<ApiWorldMutationResponse> deleteElement(@PathVariable String id) {
        boolean removed = runtime.engine().removeWorldObjectById(id);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        runtime.broadcastSnapshot();
        return ResponseEntity.ok(new ApiWorldMutationResponse(true, id, "World object removed"));
    }

    @PostMapping("/actors")
    public ResponseEntity<ApiWorldMutationResponse> createActor(@Valid @RequestBody ApiCreateActorRequest request) {
        String id = runtime.engine().addActorAheadAndGetId(mapper.toActor(request));
        runtime.broadcastSnapshot();
        return ResponseEntity.ok(new ApiWorldMutationResponse(true, id, "Actor added"));
    }

    @PutMapping("/actors/{id}")
    public ResponseEntity<ApiWorldMutationResponse> updateActor(@PathVariable String id,
                                                                @Valid @RequestBody ApiCreateActorRequest request) {
        boolean updated = runtime.engine().updateActorById(id, mapper.toActor(request));
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        runtime.broadcastSnapshot();
        return ResponseEntity.ok(new ApiWorldMutationResponse(true, id, "Actor updated"));
    }

    @DeleteMapping("/actors/{id}")
    public ResponseEntity<ApiWorldMutationResponse> deleteActor(@PathVariable String id) {
        boolean removed = runtime.engine().removeWorldObjectById(id);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        runtime.broadcastSnapshot();
        return ResponseEntity.ok(new ApiWorldMutationResponse(true, id, "Actor removed"));
    }
}
