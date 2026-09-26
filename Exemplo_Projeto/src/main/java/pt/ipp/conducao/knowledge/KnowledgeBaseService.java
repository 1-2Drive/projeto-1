package pt.ipp.conducao.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pt.ipp.conducao.model.KnowledgeBase;

import java.io.IOException;

@Service
public class KnowledgeBaseService {
    private final KnowledgeBase knowledgeBase;

    public KnowledgeBaseService(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("knowledge/knowledge-base.json").getInputStream()) {
            this.knowledgeBase = mapper.readValue(input, KnowledgeBase.class);
        }
        if (knowledgeBase.rules().size() < 8) {
            throw new IllegalStateException("A base de conhecimento deve conter pelo menos 8 regras.");
        }
        long uniqueIds = knowledgeBase.rules().stream().map(rule -> rule.id()).distinct().count();
        if (uniqueIds != knowledgeBase.rules().size()) {
            throw new IllegalStateException("Os identificadores das regras devem ser unicos.");
        }
    }

    public KnowledgeBase get() {
        return knowledgeBase;
    }
}
