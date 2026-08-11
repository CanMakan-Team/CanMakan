package com.canmakan.backend.family.exception;

/**
 * Caller is authenticated but lacks PRIMARY_ADMIN (or other) family privilege.
 * 
 * @author Amelia
 */
public class FamilyForbiddenException extends RuntimeException {

    public FamilyForbiddenException(String message) {
        super(message);
    }
}
