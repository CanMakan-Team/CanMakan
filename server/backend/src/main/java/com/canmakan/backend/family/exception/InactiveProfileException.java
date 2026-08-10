package com.canmakan.backend.family.exception;

/**
 * Profile exists but is inactive and cannot be selected (HTTP 409).
 *
 * @author Amelia
 */
public class InactiveProfileException extends RuntimeException {

    public InactiveProfileException(String message) {
        super(message);
    }
}
