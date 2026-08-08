package com.canmakan.backend.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** UC19-scoped safe error translation for authentication HTTP contracts. */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    static final String AUTHENTICATION_FAILURE_MESSAGE =
        "Invalid credentials or account unavailable.";
    static final String REFRESH_FAILURE_MESSAGE = "Authentication required.";
    private static final String INVALID_REQUEST_MESSAGE = "Invalid login request.";
    private static final String OPERATION_FAILURE_MESSAGE =
        "Authentication request could not be completed.";

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalidRequest() {
        return ResponseEntity.badRequest()
            .body(Map.of("message", INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationFailure() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("message", AUTHENTICATION_FAILURE_MESSAGE));
    }

    @ExceptionHandler(RefreshAuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleRefreshFailure() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("message", REFRESH_FAILURE_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", OPERATION_FAILURE_MESSAGE));
    }
}
