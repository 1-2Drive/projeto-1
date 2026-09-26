package pt.ipp.conducao.model;

import java.util.List;

public record RuleDefinition(String id, String name, int priority, String decision,
                             String reason, boolean terminal,
                             List<ConditionDefinition> conditions) { }
