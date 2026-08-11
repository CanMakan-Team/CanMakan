package com.canmakan.backend.admin.exception;

/** Signals that a requested account does not exist. */
public class AdminUserNotFoundException extends RuntimeException {

    public AdminUserNotFoundException(Long userId) {
        super("User account not found: " + userId);
    }
}
