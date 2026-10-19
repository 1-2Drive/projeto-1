package com.one2drive.projeto1.api.model;

/** STOP outcome includes the completed history record that the client can query. */
public record ApiStopResponse(boolean success, String message, String sessionId) { }
