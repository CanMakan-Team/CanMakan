package com.canmakan.backend.auth.exception;

/** Controlled wrapper for unexpected registration configuration or persistence failures. */
public class RegistrationFailedException extends RuntimeException {

    public RegistrationFailedException() {
        super("Registration could not be completed.");
    }

    public RegistrationFailedException(Throwable cause) {
        super("Registration could not be completed.", cause);
    }
}
