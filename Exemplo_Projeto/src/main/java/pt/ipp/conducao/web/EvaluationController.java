package pt.ipp.conducao.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ipp.conducao.knowledge.KnowledgeBaseService;
import pt.ipp.conducao.model.EvaluationRequest;
import pt.ipp.conducao.model.EvaluationResponse;
import pt.ipp.conducao.model.KnowledgeBase;
import pt.ipp.conducao.service.EvaluationService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class EvaluationController {
    private final EvaluationService evaluations;
    private final KnowledgeBaseService knowledge;

    public EvaluationController(EvaluationService evaluations, KnowledgeBaseService knowledge) {
        this.evaluations = evaluations;
        this.knowledge = knowledge;
    }

    @PostMapping("/evaluate")
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request) {
        return evaluations.evaluate(request);
    }

    @GetMapping("/knowledge-base")
    public KnowledgeBase knowledgeBase() {
        return knowledge.get();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "application", "sistema-especialista-conducao");
    }
}
