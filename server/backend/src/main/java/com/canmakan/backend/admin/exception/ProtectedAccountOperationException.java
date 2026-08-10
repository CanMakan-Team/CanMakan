package com.canmakan.backend.admin.exception;

/** Signals an account operation blocked by a System Admin safety invariant. */
public class ProtectedAccountOperationException extends RuntimeException {

    public ProtectedAccountOperationException(String message) {
        super(message);
    }
}
