package com.canmakan.backend.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** UC18-scoped error translation that leaves other API error behavior unchanged. */
@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {

    private static final String INVALID_REQUEST_MESSAGE = "Invalid registration request.";
    private static final String REGISTRATION_FAILED_MESSAGE = "Registration could not be completed.";

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", "An account with this email already exists."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalidRequest() {
        return ResponseEntity.badRequest()
            .body(Map.of("message", INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(RegistrationFailedException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationFailure() {
        return internalServerError();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedFailure() {
        return internalServerError();
    }

    private ResponseEntity<Map<String, String>> internalServerError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", REGISTRATION_FAILED_MESSAGE));
    }
}
