package com.canmakan.backend.dietaryprofile.exception;

import com.canmakan.backend.dietaryprofile.DietaryProfileController;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error translation for dietary profile and restriction requests. */
@RestControllerAdvice(assignableTypes = DietaryProfileController.class)
public class DietaryProfileExceptionHandler {

    private static final String MESSAGE_KEY = "message";
    private static final String INVALID_REQUEST_MESSAGE = "Invalid dietary profile request.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError != null && fieldError.getDefaultMessage() != null
            ? fieldError.getDefaultMessage()
            : INVALID_REQUEST_MESSAGE;
        return Map.of(MESSAGE_KEY, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException
            && exception.getMessage() != null
            ? exception.getMessage()
            : INVALID_REQUEST_MESSAGE;
        return Map.of(MESSAGE_KEY, message);
    }

    @ExceptionHandler(SelfProfileAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSelfProfileConflict(
            SelfProfileAlreadyExistsException exception) {
        return Map.of(MESSAGE_KEY, exception.getMessage());
    }

    @ExceptionHandler(SelfProfileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleSelfProfileNotFound(
            SelfProfileNotFoundException exception) {
        return Map.of(MESSAGE_KEY, exception.getMessage());
    }
}
