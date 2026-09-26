package pt.ipp.conducao.knowledge;

import org.springframework.stereotype.Component;
import pt.ipp.conducao.model.ConditionDefinition;
import pt.ipp.conducao.model.RuleDefinition;
import pt.ipp.conducao.model.RuleResult;
import pt.ipp.conducao.model.SensorInput;

import java.util.ArrayList;
import java.util.List;

/** Interpreta apenas operadores atomicos; a ativacao continua a ser efetuada pelo motor. */
@Component
public class ConditionEvaluator {
    public boolean matches(RuleDefinition rule, SensorInput input) {
        return rule.conditions().stream().allMatch(condition -> holds(condition, input));
    }

    public RuleResult result(RuleDefinition rule, SensorInput input, boolean active) {
        List<String> failed = active ? List.of() : rule.conditions().stream()
                .filter(condition -> !holds(condition, input))
                .map(condition -> describeFailure(condition, input))
                .toList();
        return new RuleResult(rule.id(), rule.name(), rule.priority(), rule.decision(),
                rule.reason(), active, failed);
    }

    private boolean holds(ConditionDefinition condition, SensorInput input) {
        Object actual = sensorValue(condition.sensor(), input);
        Object expected = condition.operator().endsWith("_FIELD")
                ? sensorValue(String.valueOf(condition.value()), input)
                : condition.value();
        return switch (condition.operator()) {
            case "EQ" -> actual.equals(expected);
            case "LT" -> number(actual) < number(expected);
            case "LTE" -> number(actual) <= number(expected);
            case "GT" -> number(actual) > number(expected);
            case "GTE" -> number(actual) >= number(expected);
            case "LT_FIELD" -> number(actual) < number(expected);
            case "GT_FIELD" -> number(actual) > number(expected);
            case "LTE_FIELD" -> number(actual) <= number(expected);
            default -> throw new IllegalArgumentException("Operador desconhecido: " + condition.operator());
        };
    }

    private String describeFailure(ConditionDefinition condition, SensorInput input) {
        Object actual = sensorValue(condition.sensor(), input);
        Object expected = condition.operator().endsWith("_FIELD")
                ? sensorValue(String.valueOf(condition.value()), input)
                : condition.value();
        return condition.sensor() + "=" + actual + " nao satisfaz "
                + condition.operator() + " " + expected;
    }

    private double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private Object sensorValue(String sensor, SensorInput input) {
        return switch (sensor) {
            case "velocidade" -> input.velocidade();
            case "limiteVelocidade" -> input.limiteVelocidade();
            case "distanciaObstaculo" -> input.distanciaObstaculo();
            case "obstaculoNaFaixa" -> input.obstaculoNaFaixa();
            case "faixaEsquerdaLivre" -> input.faixaEsquerdaLivre();
            case "faixaDireitaLivre" -> input.faixaDireitaLivre();
            case "numeroFaixas" -> input.numeroFaixas();
            case "faixaAtual" -> input.faixaAtual();
            case "estadoPressaoPneus" -> input.estadoPressaoPneus();
            case "pisoInstavel" -> input.pisoInstavel();
            case "condutorDistraido" -> input.condutorDistraido();
            case "sensoresOperacionais" -> input.sensoresOperacionais();
            case "falhaSensorCritico" -> input.falhaSensorCritico();
            case "nivelFadiga" -> input.nivelFadiga();
            case "cintosColocados" -> input.cintosColocados();
            case "chuvaIntensa" -> input.chuvaIntensa();
            case "visibilidadeReduzida" -> input.visibilidadeReduzida();
            case "peaoNaTrajetoria" -> input.peaoNaTrajetoria();
            case "distanciaPeao" -> input.distanciaPeao();
            default -> throw new IllegalArgumentException("Sensor desconhecido: " + sensor);
        };
    }
}
