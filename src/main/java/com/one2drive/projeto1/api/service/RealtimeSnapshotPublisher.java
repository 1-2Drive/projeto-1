package com.one2drive.projeto1.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiSnapshotPayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pushes snapshots to the WebSocket clients without ever making the caller wait for one.
 *
 * <p>The simulation loop publishes every tick. A browser that stops reading (busy, throttled
 * in a background tab) fills its socket buffer, and a blocking write then stalls until the
 * server's write timeout. Done on the simulation thread, that froze the simulation and every
 * API call waiting on it for the whole timeout. So each client has its own sender: at most one
 * write in flight, and only the latest snapshot waiting. A slow client skips the snapshots it
 * could not take (each one is a full state, so nothing is lost but intermediate frames) and a
 * stuck client only times out its own connection. One sender per session also keeps writes to
 * a {@link WebSocketSession}, which is not thread-safe, one at a time.
 *
 * <p>A client that connects, or reconnects, gets the last published snapshot at once instead
 * of waiting for the next tick.
 */
@Component
public class RealtimeSnapshotPublisher {
    public static final String SNAPSHOT_EVENT = "snapshot";

    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeSnapshotPublisher.class);

    private final ObjectMapper objectMapper;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final ExecutorService senders = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ws-sender");
        thread.setDaemon(true);
        return thread;
    });
    /** Guarded by this: a client registering never misses, nor reorders, a concurrent publish. */
    private ApiSnapshotPayload lastPublished;

    /** {@code objectMapper} is Spring's, so a frame is the same JSON as the REST snapshot. */
    public RealtimeSnapshotPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session) {
        Client client = new Client(session);
        synchronized (this) {
            clients.put(session.getId(), client);
            if (lastPublished != null) {
                client.offer(lastPublished);
            }
        }
    }

    public void unregister(WebSocketSession session) {
        clients.remove(session.getId());
    }

    /** Hands the snapshot to every client's sender and returns at once. */
    public void publish(ApiSimulationSnapshot snapshot) {
        ApiSnapshotPayload payload = new ApiSnapshotPayload(SNAPSHOT_EVENT, snapshot);
        synchronized (this) {
            lastPublished = payload;
            for (Client client : clients.values()) {
                client.offer(payload);
            }
        }
    }

    public int clientCount() {
        return clients.size();
    }

    @PreDestroy
    void shutdown() {
        senders.shutdownNow();
    }

    private final class Client {
        private final WebSocketSession session;
        private final AtomicReference<ApiSnapshotPayload> latest = new AtomicReference<>();
        private final AtomicBoolean sending = new AtomicBoolean(false);

        Client(WebSocketSession session) {
            this.session = session;
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
                    String json;
                    try {
                        json = objectMapper.writeValueAsString(payload);
                    } catch (JsonProcessingException ex) {
                        // A mapping bug, not a broken connection: skip the frame, keep the client.
                        LOGGER.error("Snapshot could not be serialized", ex);
                        continue;
                    }
                    session.sendMessage(new TextMessage(json));
                }
            } catch (IOException | IllegalStateException ex) {
                // The connection is gone (closed, or timed out writing): drop this client.
                // The frontend reconnects and gets a new one.
                clients.remove(session.getId(), this);
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException | IllegalStateException ignored) {
                    // The session may have been closed concurrently.
                }
            }
        }
    }
}
