package com.canmakan.backend.auth;

/** Raised when email/password login fails or the account is inactive. 
 * 
 * @author Amelia
 * @author YangMaowei
*/
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
