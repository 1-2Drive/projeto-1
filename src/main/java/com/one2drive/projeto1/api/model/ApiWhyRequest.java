package com.one2drive.projeto1.api.model;

/** The recorded decision to explain; without a timestamp, the last one of the session. */
public record ApiWhyRequest(Long timestampMillis) { }
