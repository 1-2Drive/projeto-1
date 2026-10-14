package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiSnapshotPayload;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pushes snapshots to the SSE clients without ever making the caller wait for one.
 *
 * <p>The simulation loop publishes every tick. A browser that stops reading (busy, throttled
 * in a background tab) fills its socket buffer, and a blocking write then stalls until the
 * server's write timeout. Done on the simulation thread, that froze the simulation and every
 * API call waiting on it for the whole timeout. So each client has its own sender: at most one
 * write in flight, and only the latest snapshot waiting. A slow client skips the snapshots it
 * could not take (each one is a full state, so nothing is lost but intermediate frames) and a
 * stuck client only times out its own connection.
 */
@Component
public class RealtimeSnapshotPublisher {
    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private final ExecutorService senders = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "sse-sender");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter register() {
        return register(new SseEmitter(0L));
    }

    SseEmitter register(SseEmitter emitter) {
        Client client = new Client(emitter);
        clients.add(client);
        emitter.onCompletion(() -> clients.remove(client));
        emitter.onTimeout(() -> clients.remove(client));
        emitter.onError(error -> clients.remove(client));
        return emitter;
    }

    /** Hands the snapshot to every client's sender and returns at once. */
    public void publish(ApiSimulationSnapshot snapshot) {
        ApiSnapshotPayload payload = new ApiSnapshotPayload("snapshot", snapshot);
        for (Client client : clients) {
            client.offer(payload);
        }
    }

    int clientCount() {
        return clients.size();
    }

    @PreDestroy
    void shutdown() {
        senders.shutdownNow();
    }

    private final class Client {
        private final SseEmitter emitter;
        private final AtomicReference<ApiSnapshotPayload> latest = new AtomicReference<>();
        private final AtomicBoolean sending = new AtomicBoolean(false);

        Client(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void offer(ApiSnapshotPayload payload) {
            latest.set(payload);
            if (sending.compareAndSet(false, true)) {
                senders.execute(this::drain);
            }
        }

        /** Sends the latest snapshot until none is waiting; one drain per client at a time. */
        private void drain() {
            try {
                while (true) {
                    ApiSnapshotPayload payload = latest.getAndSet(null);
                    if (payload == null) {
                        sending.set(false);
                        // A snapshot offered after getAndSet but before the flag was cleared
                        // found the drain still running and did not start another one.
                        if (latest.get() == null || !sending.compareAndSet(false, true)) {
                            return;
                        }
                        continue;
                    }
                    emitter.send(SseEmitter.event().name("snapshot").data(payload));
                }
            } catch (IOException | IllegalStateException ex) {
                // The connection is gone (closed, or timed out writing): drop this client.
                // The frontend reconnects and gets a new one.
                clients.remove(this);
                try {
                    emitter.complete();
                } catch (IllegalStateException ignored) {
                    // The client may have completed the emitter concurrently.
                }
            }
        }
    }
}
