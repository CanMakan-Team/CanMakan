package com.canmakan.backend.integration;

/**
 * Controlled failure returned when an external product lookup cannot be completed safely.
 *
 * @author YangMaowei
 */
public class ProductLookupException extends RuntimeException {

    private final Reason reason;

    public ProductLookupException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ProductLookupException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_RESPONSE,
        REMOTE_FAILURE,
        INTERRUPTED
    }
}
