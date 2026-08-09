package com.canmakan.backend.analytics.exception;

/** Signals invalid client-supplied criteria for a UC7 consumer-trends request. */
public class ConsumerTrendsValidationException extends RuntimeException {

    public ConsumerTrendsValidationException(String message) {
        super(message);
    }
}
