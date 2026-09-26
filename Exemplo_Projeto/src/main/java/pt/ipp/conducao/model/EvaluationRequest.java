package pt.ipp.conducao.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record EvaluationRequest(@NotNull @Valid SensorInput sensors, String engine) { }
