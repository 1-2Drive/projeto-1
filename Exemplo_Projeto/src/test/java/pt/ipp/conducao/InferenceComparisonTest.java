package pt.ipp.conducao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pt.ipp.conducao.knowledge.KnowledgeBaseService;
import pt.ipp.conducao.model.EvaluationRequest;
import pt.ipp.conducao.model.SensorInput;
import pt.ipp.conducao.service.EvaluationService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InferenceComparisonTest {
    @Autowired EvaluationService evaluations;
    @Autowired KnowledgeBaseService knowledge;

    @Test
    void knowledgeBaseContainsExpandedRules() {
        assertEquals(18, knowledge.get().rules().size());
    }

    @Test
    void bothEnginesBrakeInCriticalScenario() {
        var sensors = new SensorInput(85, 90, 5, true,
                false, false, false, false, false);
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "both"));

        assertTrue(response.enginesAgree());
        assertEquals("TRAVAR_EMERGENCIA", response.prolog().selectedDecision());
        assertEquals("TRAVAR_EMERGENCIA", response.drools().selectedDecision());
        assertEquals("R01_TRAVAGEM_CRITICA", response.prolog().activeRules().get(0).ruleId());
    }

    @Test
    void detectsContradictoryLaneChangesAndUsesPriority() {
        var sensors = new SensorInput(55, 90, 15, true,
                true, true, false, false, false);
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "both"));

        assertTrue(response.enginesAgree());
        assertEquals("MUDAR_ESQUERDA", response.prolog().selectedDecision());
        assertFalse(response.prolog().conflicts().isEmpty());
        assertEquals(response.prolog().conflicts(), response.drools().conflicts());
    }

    @Test
    void producesWhyNotForInactiveRules() {
        var sensors = new SensorInput(40, 90, 100, false,
                false, false, false, false, false);
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "prolog"));

        assertNull(response.enginesAgree());
        assertEquals("MANTER_VELOCIDADE", response.prolog().selectedDecision());
        assertTrue(response.prolog().inactiveRules().stream()
                .allMatch(rule -> !rule.failedConditions().isEmpty()));
    }

    @Test
    void distinguishesLowAndCriticalTyrePressure() {
        var low = new SensorInput(40, 90, 100, false, false, false,
                3, 2, "BAIXA", false, false, true, false,
                "NENHUMA", true, false, false, false, 1000);
        var critical = new SensorInput(40, 90, 100, false, false, false,
                3, 2, "CRITICA", false, false, true, false,
                "NENHUMA", true, false, false, false, 1000);

        assertEquals("REDUZIR_VELOCIDADE",
                evaluations.evaluate(new EvaluationRequest(low, "both")).prolog().selectedDecision());
        assertEquals("PARAR_VERIFICAR_PNEUS",
                evaluations.evaluate(new EvaluationRequest(critical, "both")).prolog().selectedDecision());
    }

    @Test
    void lanePositionPreventsChangingBeyondRoadBoundary() {
        var sensors = new SensorInput(40, 90, 15, true, true, true,
                3, 1, "NORMAL", false, false, true, false,
                "NENHUMA", true, false, false, false, 1000);
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "both"));

        assertEquals("MUDAR_ESQUERDA", response.prolog().selectedDecision());
        assertTrue(response.prolog().activeRules().stream()
                .noneMatch(rule -> rule.decision().equals("MUDAR_DIREITA")));
    }
}
