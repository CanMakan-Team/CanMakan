package com.canmakan.backend.dietaryprofile.exception;

/** Raised when an account already has its one allowed linked SELF profile. */
public class SelfProfileAlreadyExistsException extends RuntimeException {

    public SelfProfileAlreadyExistsException() {
        super("A SELF profile already exists for this account.");
    }

    public SelfProfileAlreadyExistsException(Throwable cause) {
        super("A SELF profile already exists for this account.", cause);
    }
}
