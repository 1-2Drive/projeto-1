package com.one2drive.projeto1.api.model;

public record ApiSnapshotPayload(
    String event,
    ApiSimulationSnapshot payload
) {
}
