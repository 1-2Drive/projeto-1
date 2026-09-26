package pt.ipp.conducao.model;

import java.util.List;

public record RuleResult(String ruleId, String ruleName, int priority, String decision,
                         String reason, boolean active, List<String> failedConditions) { }
