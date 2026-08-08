package com.canmakan.backend.family.exception;

/** Thrown when GET /families/me finds no membership (HTTP 404). 
 * 
 * @author Amelia
*/
public class FamilyNotFoundException extends RuntimeException {

    public FamilyNotFoundException(String message) {
        super(message);
    }
}
