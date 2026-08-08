package com.canmakan.backend.shared.exception;

/** Raised when a caller id (e.g. X-User-Id) does not match an existing users row. */
public class AuthenticatedUserNotFoundException extends RuntimeException {
    public AuthenticatedUserNotFoundException(String message) {
        super(message);
    }
}
