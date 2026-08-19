package com.canmakan.backend.family.exception;

/**
 * Invitation is still PENDING but past {@code expires_at} (HTTP 410).
 *
 * @author Amelia
 */
public class InvitationExpiredException extends RuntimeException {

    public InvitationExpiredException(String message) {
        super(message);
    }
}
