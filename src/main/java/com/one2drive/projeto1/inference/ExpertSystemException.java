package com.one2drive.projeto1.inference;

/**
 * The active backend could not produce a decision. The simulation must stop deciding rather
 * than carry on with a default decision nobody inferred.
 */
public class ExpertSystemException extends RuntimeException {
    public ExpertSystemException(String message) {
        super(message);
    }

    public ExpertSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
