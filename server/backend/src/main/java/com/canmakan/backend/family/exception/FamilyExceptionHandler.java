package com.canmakan.backend.family.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.canmakan.backend.family.FamilyController;
import com.canmakan.backend.family.InvitationController;

/**
 * Error translation for family create / me / invite / dependant and invitation inbox endpoints.
 *
 * @author Amelia
 */
@RestControllerAdvice(assignableTypes = {FamilyController.class, InvitationController.class})
public class FamilyExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        String message = "Request validation failed.";
        if (ex.getBindingResult().getFieldError() != null
                && ex.getBindingResult().getFieldError().getDefaultMessage() != null) {
            message = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Map.of("message", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadable() {
        return Map.of("message", "Request body is required.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("message", ex.getMessage() == null ? "Invalid request." : ex.getMessage());
    }

    @ExceptionHandler(AlreadyInFamilyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleAlreadyInFamily(AlreadyInFamilyException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(InvitationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleInvitationConflict(InvitationConflictException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(InvitationExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public Map<String, String> handleInvitationExpired(InvitationExpiredException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleInvitationNotFound(InvitationNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(FamilyForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(FamilyForbiddenException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(FamilyNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(FamilyNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }
}
