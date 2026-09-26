package pt.ipp.conducao.service;

import org.springframework.stereotype.Service;
import pt.ipp.conducao.engine.DroolsAdapter;
import pt.ipp.conducao.engine.PrologAdapter;
import pt.ipp.conducao.knowledge.KnowledgeBaseService;
import pt.ipp.conducao.model.EngineResult;
import pt.ipp.conducao.model.EvaluationRequest;
import pt.ipp.conducao.model.EvaluationResponse;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvaluationService {
    private final PrologAdapter prolog;
    private final DroolsAdapter drools;
    private final KnowledgeBaseService knowledge;

    public EvaluationService(PrologAdapter prolog, DroolsAdapter drools, KnowledgeBaseService knowledge) {
        this.prolog = prolog;
        this.drools = drools;
        this.knowledge = knowledge;
    }

    public EvaluationResponse evaluate(EvaluationRequest request) {
        String mode = request.engine() == null ? "both" : request.engine().toLowerCase();
        if (!List.of("both", "prolog", "drools").contains(mode)) {
            throw new IllegalArgumentException("Motor invalido. Use both, prolog ou drools.");
        }

        EngineResult prologResult = mode.equals("drools") ? null
                : prolog.evaluate(request.sensors(), knowledge.get());
        EngineResult droolsResult = mode.equals("prolog") ? null
                : drools.evaluate(request.sensors(), knowledge.get());
        Boolean agree = mode.equals("both") ? equivalent(prologResult, droolsResult) : null;

        List<String> notes = new ArrayList<>();
        if (Boolean.TRUE.equals(agree)) notes.add("Os motores produziram uma inferencia equivalente.");
        if (Boolean.FALSE.equals(agree)) notes.add("Divergencia detetada entre os motores.");
        if (prologResult != null && !prologResult.successful()) notes.add("Falha no Prolog: " + prologResult.error());
        if (droolsResult != null && !droolsResult.successful()) notes.add("Falha no Drools: " + droolsResult.error());
        return new EvaluationResponse(request.sensors(), mode, prologResult, droolsResult, agree, notes);
    }

    private boolean equivalent(EngineResult first, EngineResult second) {
        return first.successful() && second.successful()
                && first.selectedDecision().equals(second.selectedDecision())
                && first.activeRules().equals(second.activeRules())
                && first.inactiveRules().equals(second.inactiveRules())
                && first.conflicts().equals(second.conflicts());
    }
}
