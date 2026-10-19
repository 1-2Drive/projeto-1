package com.one2drive.projeto1.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.one2drive.projeto1.api.config.CorsConfig;
import com.one2drive.projeto1.api.config.WebSocketConfig;
import com.one2drive.projeto1.api.service.RealtimeSnapshotPublisher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The realtime channel end to end: the real Spring Boot server on a free port, a real
 * WebSocket client, and the Drools session deciding. Commands go over REST; every update
 * comes back over the socket, with no request asking for it.
 */
class RealtimeWebSocketE2ETest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static ConfigurableApplicationContext server;
    private static HttpClient http;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = new SpringApplicationBuilder(DrivingExpertSystemServerApplication.class)
            .properties("server.port=0", "expert.backend=drools", "spring.main.banner-mode=off")
            .run();
        port = ((WebServerApplicationContext) server).getWebServer().getPort();
        http = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void stoppedOnTheBaselineScenario() throws Exception {
        post("/api/simulation/reset");
    }

    @Test
    @Timeout(30)
    void aClientGetsTheCurrentSnapshotAsSoonAsItConnects() throws Exception {
        try (SnapshotClient client = SnapshotClient.connect(null)) {
            JsonNode frame = client.nextFrame(Duration.ofSeconds(2));

            assertEquals("snapshot", frame.path("event").asText());
            JsonNode payload = frame.path("payload");
            assertEquals("STOPPED", payload.path("status").asText());
            assertEquals("DROOLS", payload.path("expertBackend").asText());
            assertEquals(0L, payload.path("simulationTimeMillis").asLong());
        }
    }

    @Test
    @Timeout(60)
    void aDroolsDecisionReachesTheClientInTheTickItIsInferred() throws Exception {
        try (SnapshotClient client = SnapshotClient.connect(null)) {
            // S8: a slow vehicle 40 m ahead at a 65 km/h closing speed, a persistent critical TTC.
            post("/api/scenarios/S8/load");
            post("/api/simulation/start");

            JsonNode running = client.awaitPayload("the simulation running",
                payload -> "RUNNING".equals(payload.path("status").asText())
                    && payload.path("simulationTimeMillis").asLong() > 0, Duration.ofSeconds(5));
            long startedAt = running.path("simulationTimeMillis").asLong();
            client.awaitPayload("the clock moving on, pushed tick by tick",
                payload -> payload.path("simulationTimeMillis").asLong() >= startedAt + 500, Duration.ofSeconds(5));

            JsonNode braking = client.awaitPayload("the emergency brake Drools inferred",
                payload -> "EMERGENCY_BRAKE".equals(payload.path("currentDecision").path("speedAction").asText()),
                Duration.ofSeconds(15));
            JsonNode decision = braking.path("currentDecision");
            assertTrue(decision.path("speedSourceRule").asText().startsWith("Emergency brake"),
                "the rule that decided travels with the decision: " + decision.path("speedSourceRule").asText());
            assertEquals(braking.path("simulationTimeMillis").asLong(), decision.path("timestamp").asLong(),
                "the decision is the one inferred in the tick the frame reports");
            assertTrue(braking.path("recentRules").size() > 0, "the rules fired by the agenda travel too");
            assertTrue(braking.path("workingMemory").size() > 0, "and the working memory");
        }
    }

    @Test
    @Timeout(30)
    void aClientThatLeavesIsDroppedAndOneThatReconnectsResumesAtOnce() throws Exception {
        RealtimeSnapshotPublisher publisher = server.getBean(RealtimeSnapshotPublisher.class);

        SnapshotClient first = SnapshotClient.connect(null);
        first.nextFrame(Duration.ofSeconds(2));
        first.close();
        waitUntil(() -> publisher.clientCount() == 0, Duration.ofSeconds(5));
        assertEquals(0, publisher.clientCount(), "the closed session is unregistered");

        post("/api/simulation/start");

        try (SnapshotClient second = SnapshotClient.connect(null)) {
            JsonNode frame = second.nextFrame(Duration.ofSeconds(2));
            assertEquals(1, publisher.clientCount());
            // The first frame is the last snapshot published; START published one at once.
            assertEquals("RUNNING", frame.path("payload").path("status").asText());
        }
    }

    @Test
    @Timeout(30)
    void onlyTheFrontendOriginMayOpenTheSocketFromABrowser() throws Exception {
        try (SnapshotClient client = SnapshotClient.connect(CorsConfig.FRONTEND_ORIGIN)) {
            assertEquals("snapshot", client.nextFrame(Duration.ofSeconds(2)).path("event").asText());
        }

        ExecutionException refused = assertThrows(ExecutionException.class,
            () -> SnapshotClient.connect("http://evil.example"));
        WebSocketHandshakeException handshake = assertInstanceOf(WebSocketHandshakeException.class, refused.getCause());
        assertEquals(403, handshake.getResponse().statusCode());
    }

    private static void post(String path) throws Exception {
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path + " -> " + response.body());
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    /** A WebSocket client that queues every frame the server pushes, parsed. */
    private static final class SnapshotClient implements WebSocket.Listener, AutoCloseable {
        private final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        private WebSocket socket;

        static SnapshotClient connect(String origin) throws Exception {
            SnapshotClient client = new SnapshotClient();
            WebSocket.Builder builder = http.newWebSocketBuilder();
            if (origin != null) {
                builder.header("Origin", origin);
            }
            client.socket = builder
                .buildAsync(URI.create("ws://localhost:" + port + WebSocketConfig.REALTIME_PATH), client)
                .get(10, TimeUnit.SECONDS);
            return client;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            // A large frame may arrive in parts.
            partial.append(data);
            if (last) {
                try {
                    frames.add(JSON.readTree(partial.toString()));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("frame is not JSON", e);
                }
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        JsonNode nextFrame(Duration timeout) throws InterruptedException {
            JsonNode frame = frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (frame == null) {
                fail("no frame within " + timeout);
            }
            return frame;
        }

        /** Reads frames until one's payload matches; the frames before it are discarded. */
        JsonNode awaitPayload(String what, Predicate<JsonNode> condition, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                JsonNode frame = frames.poll(Math.max(1, (deadline - System.nanoTime()) / 1_000_000), TimeUnit.MILLISECONDS);
                if (frame != null && condition.test(frame.path("payload"))) {
                    return frame.path("payload");
                }
            }
            return fail("no frame showed " + what + " within " + timeout);
        }

        @Override
        public void close() throws Exception {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }
}
