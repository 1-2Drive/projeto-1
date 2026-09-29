package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiSnapshotPayload;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RealtimeSnapshotPublisher {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        return emitter;
    }

    public void publish(ApiSimulationSnapshot snapshot) {
        List<SseEmitter> stale = new ArrayList<>();
        ApiSnapshotPayload payload = new ApiSnapshotPayload("snapshot", snapshot);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("snapshot").data(payload));
            } catch (IOException ex) {
                stale.add(emitter);
            }
        }

        for (SseEmitter emitter : stale) {
            emitters.remove(emitter);
            emitter.completeWithError(new IOException("SSE channel closed"));
        }
    }
}
