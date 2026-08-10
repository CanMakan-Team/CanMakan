package com.canmakan.backend.family.exception;

/**
 * Invitation cannot be created or claimed due to a business conflict.
 * 
 * @author Amelia
 */
public class InvitationConflictException extends RuntimeException {

    public InvitationConflictException(String message) {
        super(message);
    }
}
