package com.canmakan.backend.family.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.canmakan.backend.family.FamilyController;
import com.canmakan.backend.family.InvitationController;

/**
 * Error translation for family- and invitation-specific failures
 * ({@link FamilyController}, {@link InvitationController}).
 *
 * <p>Shared family authz exceptions ({@code FamilyForbiddenException},
 * {@code InactiveProfileException}, {@code FamilyNotFoundException}) are handled
 * by {@link com.canmakan.backend.shared.exception.GlobalExceptionHandler}
 * so scan and other callers get the same HTTP mapping.
 *
 * @author Amelia
 */
@RestControllerAdvice(assignableTypes = {FamilyController.class, InvitationController.class})
public class FamilyExceptionHandler {

    private static final String MESSAGE_KEY = "message";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        String message = "Request validation failed.";
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            message = fieldError.getDefaultMessage();
        }
        return Map.of(MESSAGE_KEY, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadable() {
        return Map.of(MESSAGE_KEY, "Request body is required.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of(MESSAGE_KEY, ex.getMessage() == null ? "Invalid request." : ex.getMessage());
    }

    @ExceptionHandler(AlreadyInFamilyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleAlreadyInFamily(AlreadyInFamilyException ex) {
        return Map.of(MESSAGE_KEY, ex.getMessage());
    }

    @ExceptionHandler(InvitationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleInvitationConflict(InvitationConflictException ex) {
        return Map.of(MESSAGE_KEY, ex.getMessage());
    }

    @ExceptionHandler(InvitationExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public Map<String, String> handleInvitationExpired(InvitationExpiredException ex) {
        return Map.of(MESSAGE_KEY, ex.getMessage());
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleInvitationNotFound(InvitationNotFoundException ex) {
        return Map.of(MESSAGE_KEY, ex.getMessage());
    }
}
