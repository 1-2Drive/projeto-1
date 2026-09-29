package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.service.RealtimeSnapshotPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public class StreamController {
    private final RealtimeSnapshotPublisher publisher;

    public StreamController(RealtimeSnapshotPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping(path = "/snapshots", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter snapshots() {
        return publisher.register();
    }
}
