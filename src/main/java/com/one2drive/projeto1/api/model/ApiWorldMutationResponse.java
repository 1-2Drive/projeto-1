package com.one2drive.projeto1.api.model;

public record ApiWorldMutationResponse(
    boolean success,
    String objectId,
    String message
) {
}
