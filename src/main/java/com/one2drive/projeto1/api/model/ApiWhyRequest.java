package com.one2drive.projeto1.api.model;

import jakarta.validation.constraints.NotBlank;

public record ApiWhyRequest(@NotBlank String question, Long timestampMillis, String mode) { }
