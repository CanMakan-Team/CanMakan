package com.canmakan.backend.admin.exception;

/** Signals invalid account-list criteria or status-change input. */
public class InvalidAccountStatusRequestException extends RuntimeException {

    public InvalidAccountStatusRequestException(String message) {
        super(message);
    }
}
