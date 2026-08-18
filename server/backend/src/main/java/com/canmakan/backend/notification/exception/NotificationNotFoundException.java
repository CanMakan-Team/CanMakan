package com.canmakan.backend.notification.exception;

/**
 * The caller tried to change a notification that does not exist for their account.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String message) {
        super(message);
    }
}
