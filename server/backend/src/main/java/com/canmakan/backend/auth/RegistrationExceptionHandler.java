package com.canmakan.backend.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Auth-scoped error translation for register and login. 
 * 
 * @author Amelia
 * @author YangMaowei
 */
@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {

    private static final String INVALID_REGISTRATION_MESSAGE = "Invalid registration request.";
    private static final String INVALID_LOGIN_MESSAGE = "Invalid login request.";
    private static final String REGISTRATION_FAILED_MESSAGE = "Registration could not be completed.";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";

    // Handle duplicate email exception
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", "An account with this email already exists."));
    }

    // Handle invalid credentials exception
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("message", INVALID_CREDENTIALS_MESSAGE));
    }

    // Handle invalid request exception
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalidRequest(Exception exception) {
        if (hasLoginFieldRejection(exception)) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", INVALID_LOGIN_MESSAGE));
        }
        return ResponseEntity.badRequest()
            .body(Map.of("message", INVALID_REGISTRATION_MESSAGE));
    }

    // Handle illegal argument exception
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception) {
        String message = hasLoginFieldRejection(exception)
            ? INVALID_LOGIN_MESSAGE
            : INVALID_REGISTRATION_MESSAGE;
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    // Handle registration failure exception
    @ExceptionHandler(RegistrationFailedException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationFailure() {
        return internalServerError();
    }

    // Handle unexpected failure exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedFailure() {
        return internalServerError();
    }
    
    // --- Helper methods ---

    // Check if the exception is a login field rejection
    private static boolean hasLoginFieldRejection(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String detail = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (detail.contains("login")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // Return an internal server error response
    private ResponseEntity<Map<String, String>> internalServerError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", REGISTRATION_FAILED_MESSAGE));
    }
}
