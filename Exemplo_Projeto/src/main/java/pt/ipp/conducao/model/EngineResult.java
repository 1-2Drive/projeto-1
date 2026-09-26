package pt.ipp.conducao.model;

import java.util.List;

public record EngineResult(String engine, String selectedDecision, String explanation,
                           List<RuleResult> activeRules, List<RuleResult> inactiveRules,
                           List<String> conflicts, long executionTimeMs, String error) {
    public boolean successful() { return error == null; }
}
