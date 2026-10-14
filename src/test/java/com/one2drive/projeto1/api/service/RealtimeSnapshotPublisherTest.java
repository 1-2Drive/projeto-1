package com.one2drive.projeto1.api.service;

import com.one2drive.projeto1.api.model.ApiSimulationSnapshot;
import com.one2drive.projeto1.api.model.ApiSnapshotPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A browser that stops reading must not stall the simulation: the regression behind
 * "SocketTimeoutException in RealtimeSnapshotPublisher.publish" with the whole GUI frozen.
 */
class RealtimeSnapshotPublisherTest {
    private final RealtimeSnapshotPublisher publisher = new RealtimeSnapshotPublisher();

    /** An emitter that records what it is sent and can be made to block or fail. */
    private static final class ProbeEmitter extends SseEmitter {
        final List<ApiSimulationSnapshot> received = new CopyOnWriteArrayList<>();
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch firstSendStarted = new CountDownLatch(1);
        volatile boolean blockFirstSend;
        volatile boolean fail;

        ProbeEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
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
            builder.build().stream()
                .map(part -> part.getData())
                .filter(ApiSnapshotPayload.class::isInstance)
                .map(ApiSnapshotPayload.class::cast)
                .forEach(payload -> received.add(payload.payload()));
        }
    }

    @AfterEach
    void stop() {
        publisher.shutdown();
    }

    @Test
    @Timeout(10)
    void aStalledClientNeitherBlocksThePublisherNorTheOtherClients() throws Exception {
        ProbeEmitter stalled = new ProbeEmitter();
        stalled.blockFirstSend = true;
        ProbeEmitter healthy = new ProbeEmitter();
        publisher.register(stalled);
        publisher.register(healthy);

        ApiSimulationSnapshot first = snapshot(0);
        publisher.publish(first);
        assertTrue(stalled.firstSendStarted.await(5, TimeUnit.SECONDS), "the stalled client's send started");

        // While that client's write hangs, 100 more ticks publish at once.
        long start = System.nanoTime();
        ApiSimulationSnapshot last = null;
        for (int i = 1; i <= 100; i++) {
            last = snapshot(i);
            publisher.publish(last);
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMillis < 1_000, "publish must not wait for a stalled client, took " + elapsedMillis + " ms");

        ApiSimulationSnapshot expectedLast = last;
        waitUntil(() -> lastOf(healthy) == expectedLast);
        assertSame(expectedLast, lastOf(healthy), "the other client keeps receiving while one is stalled");
        assertTrue(stalled.received.isEmpty(), "the stalled client is still blocked in its first write");

        stalled.release.countDown();
        waitUntil(() -> lastOf(stalled) == expectedLast);
        assertTrue(stalled.received.size() <= 3,
            "a client that fell behind gets the latest snapshot, not the backlog: got " + stalled.received.size());
        assertSame(expectedLast, lastOf(stalled));
    }

    @Test
    @Timeout(10)
    void aClientWhoseWriteFailsIsDropped() throws Exception {
        ProbeEmitter broken = new ProbeEmitter();
        broken.fail = true;
        publisher.register(broken);
        publisher.publish(snapshot(1));
        waitUntil(() -> publisher.clientCount() == 0);
        assertEquals(0, publisher.clientCount());
    }

    private static ApiSimulationSnapshot lastOf(ProbeEmitter emitter) {
        List<ApiSimulationSnapshot> received = emitter.received;
        return received.isEmpty() ? null : received.get(received.size() - 1);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }

    /** A distinct snapshot; only its identity matters here, so every field is a default. */
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
