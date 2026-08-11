package com.canmakan.backend.dietaryprofile;

import com.canmakan.backend.dietaryprofile.exception.SelfProfileAlreadyExistsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error translation for dietary profile and restriction requests. */
@RestControllerAdvice(assignableTypes = DietaryProfileController.class)
public class DietaryProfileExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException exception) {
        String message = "Invalid dietary profile request.";
        if (exception.getBindingResult().getFieldError() != null
                && exception.getBindingResult().getFieldError().getDefaultMessage() != null) {
            message = exception.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Map.of("message", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException
            && exception.getMessage() != null
            ? exception.getMessage()
            : "Invalid dietary profile request.";
        return Map.of("message", message);
    }

    @ExceptionHandler(SelfProfileAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleSelfProfileConflict(
            SelfProfileAlreadyExistsException exception) {
        return Map.of("message", exception.getMessage());
    }
}
