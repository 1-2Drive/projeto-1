package com.one2drive.projeto1.api.model;

public record ApiTimelineEntry(
    long timestamp,
    String type,
    String message
) {
}
