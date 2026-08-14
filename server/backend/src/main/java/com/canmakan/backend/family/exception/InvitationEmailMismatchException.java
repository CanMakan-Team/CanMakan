package com.canmakan.backend.family.exception;

/**
 * Registration email does not match the pending invitation.
 */
public class InvitationEmailMismatchException extends RuntimeException {

    public InvitationEmailMismatchException(String message) {
        super(message);
    }
}
