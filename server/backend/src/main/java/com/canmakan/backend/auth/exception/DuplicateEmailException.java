package com.canmakan.backend.auth.exception;

/** Raised when a normalized registration email already belongs to an account. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account with this email already exists.");
    }
}
