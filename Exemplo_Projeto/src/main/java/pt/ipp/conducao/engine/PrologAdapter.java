package pt.ipp.conducao.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pt.ipp.conducao.model.EngineResult;
import pt.ipp.conducao.model.KnowledgeBase;
import pt.ipp.conducao.model.RuleResult;
import pt.ipp.conducao.model.SensorInput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class PrologAdapter implements InferenceEngine {
    private final ObjectMapper mapper;
    private final Path prologProgram;

    public PrologAdapter(ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        this.prologProgram = Files.createTempFile("driving-rules-", ".pl");
        this.prologProgram.toFile().deleteOnExit();
        try (var source = new ClassPathResource("prolog/driving_rules.pl").getInputStream()) {
            Files.copy(source, prologProgram, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public EngineResult evaluate(SensorInput input, KnowledgeBase knowledgeBase) {
        long start = System.nanoTime();
        Process process = null;
        try {
            process = new ProcessBuilder("swipl", "-q", "-s", prologProgram.toString(),
                    "-g", "main", "-t", "halt").start();
            Map<String, Object> sensors = mapper.convertValue(input, new TypeReference<>() { });
            Object context = sensors.remove("contexto");
            sensors.putAll(SensorInput.contextoPadrao());
            if (context instanceof Map<?, ?> values) {
                values.forEach((key, value) -> sensors.put(String.valueOf(key), value));
            }
            mapper.writeValue(process.getOutputStream(),
                    Map.of("sensors", sensors, "knowledgeBase", knowledgeBase));
            process.getOutputStream().close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("O SWI-Prolog excedeu o limite de cinco segundos.");
            }
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("SWI-Prolog terminou com erro: " + stderr.strip());
            }
            List<RuleResult> results = mapper.readValue(process.getInputStream(), new TypeReference<>() { });
            return ResultPolicy.build("SWI-Prolog", results, start);
        } catch (Exception error) {
            if (process != null) process.destroyForcibly();
            return ResultPolicy.error("SWI-Prolog", start, error);
        }
    }
}
