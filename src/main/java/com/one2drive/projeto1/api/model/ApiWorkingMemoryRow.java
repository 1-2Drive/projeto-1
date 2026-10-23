package com.one2drive.projeto1.api.model;

public record ApiWorkingMemoryRow(
    String type,
    String value,
    String source,
    Long timestamp,
    String stream
) {
}
