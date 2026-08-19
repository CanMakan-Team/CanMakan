package com.canmakan.backend.shared.exception;

/** Raised when the authenticated caller does not match an existing users row. */
public class AuthenticatedUserNotFoundException extends RuntimeException {
    public AuthenticatedUserNotFoundException(String message) {
        super(message);
    }
}
