package com.canmakan.backend.family.exception;

/**
 * Sole/last PRIMARY_ADMIN cannot be removed (HTTP 409).
 */
public class LastPrimaryAdminException extends RuntimeException {

    public LastPrimaryAdminException(String message) {
        super(message);
    }
}
