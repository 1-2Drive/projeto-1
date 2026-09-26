package pt.ipp.conducao.engine;

import pt.ipp.conducao.model.EngineResult;
import pt.ipp.conducao.model.RuleResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ResultPolicy {
    private static final Set<Set<String>> INCOMPATIBLE = Set.of(
            Set.of("MUDAR_ESQUERDA", "MUDAR_DIREITA"),
            Set.of("TRAVAR_EMERGENCIA", "MANTER_VELOCIDADE"),
            Set.of("PARAR_VERIFICAR_PNEUS", "MANTER_VELOCIDADE"),
            Set.of("REDUZIR_VELOCIDADE", "MANTER_VELOCIDADE")
    );

    private ResultPolicy() { }

    public static EngineResult build(String engine, List<RuleResult> results, long startNanos) {
        List<RuleResult> active = results.stream().filter(RuleResult::active)
                .sorted(Comparator.comparingInt(RuleResult::priority).reversed()
                        .thenComparing(RuleResult::ruleId)).toList();
        List<RuleResult> inactive = results.stream().filter(rule -> !rule.active())
                .sorted(Comparator.comparing(RuleResult::ruleId)).toList();
        String decision = active.isEmpty() ? "SEM_DECISAO" : active.get(0).decision();
        String explanation = active.isEmpty()
                ? "Nenhuma regra foi ativada; a base de conhecimento deve ser revista."
                : active.get(0).reason() + " Regra vencedora: " + active.get(0).ruleId()
                    + " (prioridade " + active.get(0).priority() + ").";
        return new EngineResult(engine, decision, explanation, active, inactive,
                conflicts(active), (System.nanoTime() - startNanos) / 1_000_000, null);
    }

    public static EngineResult error(String engine, long startNanos, Exception error) {
        return new EngineResult(engine, "ERRO", "O motor nao produziu uma decisao.",
                List.of(), List.of(), List.of(),
                (System.nanoTime() - startNanos) / 1_000_000, error.getMessage());
    }

    private static List<String> conflicts(List<RuleResult> active) {
        List<String> found = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                String first = active.get(i).decision();
                String second = active.get(j).decision();
                if (!first.equals(second) && INCOMPATIBLE.contains(Set.of(first, second))) {
                    found.add(active.get(i).ruleId() + " (" + first + ") contradiz "
                            + active.get(j).ruleId() + " (" + second + "). Vence a maior prioridade.");
                }
            }
        }
        return found;
    }
}
