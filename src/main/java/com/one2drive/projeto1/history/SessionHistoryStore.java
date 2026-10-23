package com.one2drive.projeto1.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Stores completed sessions as individual JSON files so RESET and process restart do not erase them. */
@Repository
public class SessionHistoryStore {
    private final Path directory;
    private final ObjectMapper mapper;

    public SessionHistoryStore(@Value("${history.directory:${user.home}/.projeto-1/sessions}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public synchronized void save(SimulationSession session) {
        try {
            Files.createDirectories(directory);
            mapper.writeValue(directory.resolve(session.id() + ".json").toFile(), session);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist simulation session " + session.id(), e);
        }
    }

    public synchronized List<SimulationSession> list() {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(this::read)
                .sorted(Comparator.comparing(SimulationSession::startedAt).reversed())
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read simulation history", e);
        }
    }

    public synchronized Optional<SimulationSession> get(String id) {
        Path file = directory.resolve(id + ".json").normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file)) return Optional.empty();
        return Optional.of(read(file));
    }

    private SimulationSession read(Path file) {
        try {
            return mapper.readValue(file.toFile(), SimulationSession.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read simulation session " + file.getFileName(), e);
        }
    }
}
