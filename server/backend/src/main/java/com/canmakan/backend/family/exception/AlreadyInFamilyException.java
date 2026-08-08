package com.canmakan.backend.family.exception;

/** Thrown when the caller already belongs to a family (HTTP 409, D2). 
 * 
 * @author Amelia
*/
public class AlreadyInFamilyException extends RuntimeException {

    public AlreadyInFamilyException(String message) {
        super(message);
    }
}
