package pt.ipp.conducao.model;

import java.util.List;

public record KnowledgeBase(String version, String description, List<RuleDefinition> rules) { }
