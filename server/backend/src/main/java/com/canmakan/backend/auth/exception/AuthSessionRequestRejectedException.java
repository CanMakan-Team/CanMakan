package com.canmakan.backend.auth.exception;

/** Rejects an auth cookie mutation whose client intent or browser origin is untrusted. */
public class AuthSessionRequestRejectedException extends RuntimeException {

    public AuthSessionRequestRejectedException() {
        super("Authentication request origin could not be verified.");
    }
}
