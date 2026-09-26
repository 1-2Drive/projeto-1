package pt.ipp.conducao.engine;

import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.springframework.stereotype.Component;
import pt.ipp.conducao.knowledge.ConditionEvaluator;
import pt.ipp.conducao.model.EngineResult;
import pt.ipp.conducao.model.KnowledgeBase;
import pt.ipp.conducao.model.RuleResult;
import pt.ipp.conducao.model.SensorInput;

import java.util.ArrayList;
import java.util.List;

@Component
public class DroolsAdapter implements InferenceEngine {
    private final ConditionEvaluator evaluator;
    private final KieBase kieBase;

    public DroolsAdapter(ConditionEvaluator evaluator) {
        this.evaluator = evaluator;
        this.kieBase = new KieHelper()
                .addResource(ResourceFactory.newClassPathResource(
                        "pt/ipp/conducao/rules/driving-rules.drl"), ResourceType.DRL)
                .build();
    }

    @Override
    public EngineResult evaluate(SensorInput input, KnowledgeBase knowledgeBase) {
        long start = System.nanoTime();
        List<RuleResult> results = new ArrayList<>();
        KieSession session = kieBase.newKieSession();
        try {
            session.setGlobal("evaluator", evaluator);
            session.setGlobal("results", results);
            session.insert(input);
            knowledgeBase.rules().forEach(session::insert);
            session.fireAllRules();
            return ResultPolicy.build("Drools", results, start);
        } catch (Exception error) {
            return ResultPolicy.error("Drools", start, error);
        } finally {
            session.dispose();
        }
    }
}
