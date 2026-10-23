package com.one2drive.projeto1.drools;

public record WorkingMemoryFactView(
    String type,
    String value,
    String source,
    Long timestamp,
    String stream,
    String raw
) {
}
