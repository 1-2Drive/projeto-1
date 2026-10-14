package com.one2drive.projeto1.prolog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One long-lived {@code swipl} child process running {@code prolog/server.pl}: a JSON
 * request per line on its stdin, a JSON reply per line on its stdout. The example project
 * started a process per request; at one decision every 100 ms that start-up cost is too
 * high, and the Prolog side has to keep its perception state between cycles anyway.
 */
final class PrologProcess implements AutoCloseable {
    /** Prolog sources, copied from the classpath next to each other so they can load each other. */
    private static final List<String> SOURCES = List.of(
        "server.pl", "engine.pl", "driving_state.pl", "driving_rules.pl");

    /** Overrides the {@code swipl} found on PATH. */
    static final String EXECUTABLE_PROPERTY = "prolog.executable";

    private final ObjectMapper mapper;
    private final Path workDir;
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final ExecutorService reader;
    private final long timeoutMillis;

    PrologProcess(ObjectMapper mapper, long timeoutMillis) throws IOException {
        this.mapper = mapper;
        this.timeoutMillis = timeoutMillis;
        this.workDir = copySources();
        String executable = System.getProperty(EXECUTABLE_PROPERTY, "swipl");
        this.process = new ProcessBuilder(executable, "-q", "-g", "main", "-t", "halt",
            workDir.resolve("server.pl").toString())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.reader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "prolog-reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Sends one request and waits for its reply. A reply with {@code ok:false}, a dead
     * process or a timeout is an error: the caller must never mistake it for "no proposal".
     */
    synchronized JsonNode request(Object request) {
        if (!process.isAlive()) {
            throw new PrologEngineException("SWI-Prolog process is not running (exit code " + process.exitValue() + ")");
        }
        try {
            stdin.write(mapper.writeValueAsString(request));
            stdin.newLine();
            stdin.flush();
            Future<String> line = reader.submit(stdout::readLine);
            String reply;
            try {
                reply = line.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // The reply may still arrive and would then answer the next request.
                process.destroyForcibly();
                throw new PrologEngineException("SWI-Prolog did not answer within " + timeoutMillis + " ms");
            }
            if (reply == null) {
                throw new PrologEngineException("SWI-Prolog closed its output");
            }
            JsonNode node = mapper.readTree(reply);
            if (!node.path("ok").asBoolean(false)) {
                throw new PrologEngineException("SWI-Prolog error: " + node.path("error").asText(reply));
            }
            return node;
        } catch (IOException e) {
            throw new PrologEngineException("SWI-Prolog communication failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrologEngineException("Interrupted while waiting for SWI-Prolog", e);
        } catch (ExecutionException e) {
            throw new PrologEngineException("SWI-Prolog communication failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private static Path copySources() throws IOException {
        Path dir = Files.createTempDirectory("prolog-backend-");
        dir.toFile().deleteOnExit();
        for (String source : SOURCES) {
            try (InputStream in = PrologProcess.class.getResourceAsStream("/prolog/" + source)) {
                if (in == null) {
                    throw new IOException("Prolog source not on classpath: prolog/" + source);
                }
                Path target = dir.resolve(source);
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                target.toFile().deleteOnExit();
            }
        }
        return dir;
    }

    @Override
    public synchronized void close() {
        try {
            stdin.close();
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (IOException e) {
            process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            reader.shutdownNow();
        }
    }
}
