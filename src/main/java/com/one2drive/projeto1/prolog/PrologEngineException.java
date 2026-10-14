package com.one2drive.projeto1.prolog;

import com.one2drive.projeto1.inference.ExpertSystemException;

/** The Prolog backend could not produce a decision: a crash, a timeout or an error reply. */
public class PrologEngineException extends ExpertSystemException {
    public PrologEngineException(String message) {
        super(message);
    }

    public PrologEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
