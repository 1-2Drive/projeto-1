package pt.ipp.conducao.model;

import java.util.List;

public record EvaluationResponse(SensorInput input, String executionMode, EngineResult prolog,
                                 EngineResult drools, Boolean enginesAgree,
                                 List<String> comparisonNotes) { }
