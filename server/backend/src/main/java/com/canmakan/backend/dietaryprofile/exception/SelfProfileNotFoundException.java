package com.canmakan.backend.dietaryprofile.exception;

/** Raised when the caller has no linked SELF profile yet (nothing to fetch or update). */
public class SelfProfileNotFoundException extends RuntimeException {

    public SelfProfileNotFoundException() {
        super("No SELF profile exists for this account yet.");
    }
}
