package com.canmakan.backend.shared.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cross-cutting exception translation shared by multiple controllers.
 *
 * @author Amelia
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Missing users.id for the authenticated caller
    @ExceptionHandler(AuthenticatedUserNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleAuthenticatedUserNotFound(
            AuthenticatedUserNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }
}
