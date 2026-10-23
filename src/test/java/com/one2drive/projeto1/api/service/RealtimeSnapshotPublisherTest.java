package com.one2drive.projeto1.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A browser that stops reading must not stall the simulation: the regression behind
 * "SocketTimeoutException in RealtimeSnapshotPublisher.publish" with the whole GUI frozen.
 */
class RealtimeSnapshotPublisherTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger SESSION_IDS = new AtomicInteger();

    private final RealtimeSnapshotPublisher publisher = new RealtimeSnapshotPublisher(JSON);

    /** A session that records the snapshot times it is sent and can be made to block or fail. */
    private static final class ProbeSession implements WebSocketSession {
        final String id = "probe-" + SESSION_IDS.incrementAndGet();
        final List<Long> received = new CopyOnWriteArrayList<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch firstSendStarted = new CountDownLatch(1);
        volatile boolean blockFirstSend;
        volatile boolean fail;
        volatile CloseStatus closedWith;

        @Override
        public void sendMessage(WebSocketMessage<?> message) throws IOException {
            firstSendStarted.countDown();
            if (fail) {
                throw new SocketTimeoutException("client stopped reading");
            }
            if (blockFirstSend) {
                blockFirstSend = false;
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            JsonNode frame = JSON.readTree(((TextMessage) message).getPayload());
            events.add(frame.path("event").asText());
            received.add(frame.path("payload").path("simulationTimeMillis").asLong());
        }

        Long last() {
            return received.isEmpty() ? null : received.get(received.size() - 1);
        }

        @Override
        public void close(CloseStatus status) {
            closedWith = status;
        }

        @Override
        public void close() {
            close(CloseStatus.NORMAL);
        }

        @Override
        public boolean isOpen() {
            return closedWith == null;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return URI.create("ws://localhost/api/ws/simulation");
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return new HttpHeaders();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return new HashMap<>();
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 0;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 0;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return List.of();
        }
    }

    @AfterEach
    void stop() {
        publisher.shutdown();
    }

    @Test
    @Timeout(10)
    void aStalledClientNeitherBlocksThePublisherNorTheOtherClients() throws Exception {
        ProbeSession stalled = new ProbeSession();
        stalled.blockFirstSend = true;
        ProbeSession healthy = new ProbeSession();
        publisher.register(stalled);
        publisher.register(healthy);

        publisher.publish(snapshot(0));
        assertTrue(stalled.firstSendStarted.await(5, TimeUnit.SECONDS), "the stalled client's send started");

        // While that client's write hangs, 100 more ticks publish at once.
        long start = System.nanoTime();
        for (int i = 1; i <= 100; i++) {
            publisher.publish(snapshot(i));
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMillis < 1_000, "publish must not wait for a stalled client, took " + elapsedMillis + " ms");

        waitUntil(() -> Long.valueOf(100).equals(healthy.last()));
        assertEquals(100L, healthy.last(), "the other client keeps receiving while one is stalled");
        assertTrue(stalled.received.isEmpty(), "the stalled client is still blocked in its first write");

        stalled.release.countDown();
        waitUntil(() -> Long.valueOf(100).equals(stalled.last()));
        assertTrue(stalled.received.size() <= 3,
            "a client that fell behind gets the latest snapshot, not the backlog: got " + stalled.received.size());
        assertEquals(100L, stalled.last());
    }

    @Test
    @Timeout(10)
    void aClientWhoseWriteFailsIsDroppedAndClosed() throws Exception {
        ProbeSession broken = new ProbeSession();
        broken.fail = true;
        publisher.register(broken);
        publisher.publish(snapshot(1));
        waitUntil(() -> publisher.clientCount() == 0);
        assertEquals(0, publisher.clientCount());
        waitUntil(() -> broken.closedWith != null);
        assertEquals(CloseStatus.SESSION_NOT_RELIABLE, broken.closedWith);
    }

    @Test
    @Timeout(10)
    void aClientThatConnectsGetsTheLastSnapshotAtOnce() throws Exception {
        publisher.publish(snapshot(400));
        publisher.publish(snapshot(500));

        ProbeSession late = new ProbeSession();
        publisher.register(late);

        waitUntil(() -> !late.received.isEmpty());
        assertEquals(List.of(500L), late.received, "only the last snapshot, without waiting for the next tick");
        assertEquals(List.of(RealtimeSnapshotPublisher.SNAPSHOT_EVENT), late.events);
    }

    @Test
    @Timeout(10)
    void aClosedClientIsNoLongerSent() throws Exception {
        ProbeSession client = new ProbeSession();
        publisher.register(client);
        publisher.publish(snapshot(1));
        waitUntil(() -> client.received.size() == 1);

        publisher.unregister(client);
        publisher.publish(snapshot(2));

        assertEquals(0, publisher.clientCount());
        Thread.sleep(50);
        assertEquals(List.of(1L), client.received);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    /** A snapshot told apart by its time; every other field is a default. */
    private static ApiSimulationSnapshot snapshot(long simulationTimeMillis) throws Exception {
        RecordComponent[] components = ApiSimulationSnapshot.class.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = types[i] == long.class ? simulationTimeMillis
                : types[i] == int.class ? 0
                : types[i] == double.class ? 0.0
                : types[i] == boolean.class ? false
                : null;
        }
        Constructor<ApiSimulationSnapshot> constructor = ApiSimulationSnapshot.class.getDeclaredConstructor(types);
        return constructor.newInstance(values);
    }
}
