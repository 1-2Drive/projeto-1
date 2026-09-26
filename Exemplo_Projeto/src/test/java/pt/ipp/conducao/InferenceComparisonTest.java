package pt.ipp.conducao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pt.ipp.conducao.knowledge.KnowledgeBaseService;
import pt.ipp.conducao.model.EvaluationRequest;
import pt.ipp.conducao.model.SensorInput;
import pt.ipp.conducao.service.EvaluationService;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

@SpringBootTest
class InferenceComparisonTest {
    @Autowired EvaluationService evaluations;
    @Autowired KnowledgeBaseService knowledge;

    @Test
    void knowledgeBaseContainsExpandedRules() {
        assertEquals(49, knowledge.get().rules().size());
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
                "NENHUMA", true, false, false, false, 1000, Map.of());
        var critical = new SensorInput(40, 90, 100, false, false, false,
                3, 2, "CRITICA", false, false, true, false,
                "NENHUMA", true, false, false, false, 1000, Map.of());

        assertEquals("REDUZIR_VELOCIDADE",
                evaluations.evaluate(new EvaluationRequest(low, "both")).prolog().selectedDecision());
        assertEquals("PARAR_VERIFICAR_PNEUS",
                evaluations.evaluate(new EvaluationRequest(critical, "both")).prolog().selectedDecision());
    }

    @Test
    void lanePositionPreventsChangingBeyondRoadBoundary() {
        var sensors = new SensorInput(40, 90, 15, true, true, true,
                3, 1, "NORMAL", false, false, true, false,
                "NENHUMA", true, false, false, false, 1000, Map.of());
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "both"));

        assertEquals("MUDAR_ESQUERDA", response.prolog().selectedDecision());
        assertTrue(response.prolog().activeRules().stream()
                .noneMatch(rule -> rule.decision().equals("MUDAR_DIREITA")));
    }

    @Test
    void criticalSensorFailureStopsAutonomousDrivingInBothEngines() {
        var sensors = scenario(40, 90, false, 1000,
                "NORMAL", "NENHUMA", false, true, false, 1000);
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "both"));

        assertTrue(response.enginesAgree());
        assertEquals("IMPEDIR_CONDUCAO_AUTONOMA", response.prolog().selectedDecision());
        assertEquals("IMPEDIR_CONDUCAO_AUTONOMA", response.drools().selectedDecision());
    }

    @Test
    void pedestrianDistanceChangesTheDecision() {
        var critical = scenario(40, 90, false, 1000,
                "NORMAL", "NENHUMA", true, true, true, 5);
        var nearby = scenario(40, 90, false, 1000,
                "NORMAL", "NENHUMA", true, true, true, 20);

        assertEquals("TRAVAR_EMERGENCIA",
                evaluations.evaluate(new EvaluationRequest(critical, "both")).prolog().selectedDecision());
        assertEquals("PREPARAR_TRAVAGEM",
                evaluations.evaluate(new EvaluationRequest(nearby, "both")).prolog().selectedDecision());
    }

    @Test
    void elevatedFatigueOutranksAdverseWeather() {
        var sensors = new SensorInput(40, 90, 1000, false, false, false,
                2, 1, "NORMAL", false, false, true, false,
                "ELEVADA", true, true, true, false, 1000, Map.of());
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "both"));

        assertTrue(response.enginesAgree());
        assertEquals("PARAR_DESCANSAR", response.prolog().selectedDecision());
        assertTrue(response.prolog().activeRules().stream()
                .anyMatch(rule -> rule.ruleId().equals("R13_CHUVA_INTENSA")));
    }

    @Test
    void vehicleOffEndsInStandby() {
        assertDecision("SISTEMA_EM_ESPERA", Map.of("veiculoLigado", false));
    }

    @Test
    void alcoholWithoutKnownRatePreventsDriving() {
        assertDecision("NAO_CONDUZIR", Map.of(
                "consumiuAlcool", true, "taxaAlcoolConhecida", false));
    }

    @Test
    void unsafeOvertakingIsCancelled() {
        assertDecision("CANCELAR_ULTRAPASSAGEM", Map.of(
                "emUltrapassagem", true, "ultrapassagemPermitida", false));
    }

    @Test
    void redSignalStopsBeforeSafetyLine() {
        assertDecision("PARAR_ANTES_LINHA", Map.of(
                "aproximaCruzamento", true, "temPrioridade", true,
                "sinalPermiteAvancar", false));
    }

    @Test
    void obstacleOutsideTrajectoryIsMonitored() {
        assertDecision("MONITORIZAR_OBSTACULO", Map.of(
                "obstaculoDetetado", true, "obstaculoNaTrajetoria", false));
    }

    @Test
    void obstacleWithoutSafeDeviationTriggersEmergencyBraking() {
        assertDecision("TRAVAR_EMERGENCIA", Map.of(
                "obstaculoDetetado", true, "obstaculoNaTrajetoria", true,
                "espacoSeguroDesvio", false));
    }

    @Test
    void safeObstacleDeviationIsSelected() {
        assertDecision("DESVIAR_CONTROLADO", Map.of(
                "obstaculoDetetado", true, "obstaculoNaTrajetoria", true,
                "espacoSeguroDesvio", true));
    }

    @Test
    void pedestrianOutsideTrajectoryRemainsUnderSurveillance() {
        assertDecision("VIGIAR_PEAO", Map.of(
                "peaoDetetado", true, "peaoPodeEntrarTrajetoria", false));
    }

    private void assertDecision(String expected, Map<String, Object> context) {
        var sensors = new SensorInput(40, 90, 1000, false, false, false,
                2, 1, "NORMAL", false, false, true, false,
                "NENHUMA", true, false, false, false, 1000, context);
        var response = evaluations.evaluate(new EvaluationRequest(sensors, "both"));
        assertTrue(response.enginesAgree());
        assertEquals(expected, response.prolog().selectedDecision());
        assertEquals(expected, response.drools().selectedDecision());
    }

    private SensorInput scenario(double speed, double limit, boolean obstacle, double obstacleDistance,
                                 String tyres, String fatigue, boolean sensorsOperational,
                                 boolean criticalSensorFailure, boolean pedestrian, double pedestrianDistance) {
        return new SensorInput(speed, limit, obstacleDistance, obstacle, false, false,
                2, 1, tyres, false, false, sensorsOperational, criticalSensorFailure,
                fatigue, true, false, false, pedestrian, pedestrianDistance, Map.of());
    }
}
