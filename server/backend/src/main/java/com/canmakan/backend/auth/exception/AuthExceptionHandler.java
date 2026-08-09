package com.canmakan.backend.auth.exception;

import com.canmakan.backend.auth.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Exception translation for {@link AuthController} (login + register + refresh + logout). */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    static final String AUTHENTICATION_FAILURE_MESSAGE =
        "Invalid credentials or account unavailable.";
    static final String REFRESH_FAILURE_MESSAGE = "Authentication required.";
    private static final String INVALID_LOGIN_MESSAGE = "Invalid login request.";
    private static final String INVALID_REGISTRATION_MESSAGE = "Invalid registration request.";
    private static final String REGISTRATION_FAILED_MESSAGE = "Registration could not be completed.";
    private static final String AUTH_OPERATION_FAILURE_MESSAGE =
        "Authentication request could not be completed.";

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", "An account with this email already exists."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalidRequest() {
        return ResponseEntity.badRequest()
            .body(Map.of("message", validationMessageForCurrentRequest()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument() {
        return ResponseEntity.badRequest()
            .body(Map.of("message", validationMessageForCurrentRequest()));
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

    @ExceptionHandler(RegistrationFailedException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", REGISTRATION_FAILED_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedFailure() {
        String message = isRegistrationRequest()
            ? REGISTRATION_FAILED_MESSAGE
            : AUTH_OPERATION_FAILURE_MESSAGE;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", message));
    }

    private static String validationMessageForCurrentRequest() {
        return isRegistrationRequest() ? INVALID_REGISTRATION_MESSAGE : INVALID_LOGIN_MESSAGE;
    }

    private static boolean isRegistrationRequest() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        return uri.contains("/register");
    }
}
