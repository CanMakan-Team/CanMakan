package com.canmakan.backend.family.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.canmakan.backend.family.FamilyController;

/**
 * UC8-scoped error translation for family create / me endpoints.
 *
 * @author Amelia
 */
@RestControllerAdvice(assignableTypes = FamilyController.class)
public class FamilyExceptionHandler {

    // UC8 handle validation exception for family name
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        String message = "Family name is required.";
        if (ex.getBindingResult().getFieldError() != null
                && ex.getBindingResult().getFieldError().getDefaultMessage() != null) {
            message = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Map.of("message", message);
    }

    // UC8 handle unreadable exception for family name
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadable() {
        return Map.of("message", "Family name is required.");
    }

    // UC8 handle already in family exception
    @ExceptionHandler(AlreadyInFamilyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleAlreadyInFamily(AlreadyInFamilyException ex) {
        return Map.of("message", ex.getMessage());
    }

    // UC8 handle family not found exception
    @ExceptionHandler(FamilyNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(FamilyNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }
}
