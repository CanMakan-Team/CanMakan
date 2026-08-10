package com.canmakan.backend.family.exception;

/**
 * Invitation token does not exist (HTTP 404).
 *
 * @author Amelia
 */
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException(String message) {
        super(message);
    }
}
