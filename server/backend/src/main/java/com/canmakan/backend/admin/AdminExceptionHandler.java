package com.canmakan.backend.admin;

import com.canmakan.backend.analytics.exception.ConsumerTrendsValidationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** HTTP exception translation scoped to System Admin endpoints. */
@RestControllerAdvice(assignableTypes = AdminController.class)
public class AdminExceptionHandler {

    @ExceptionHandler(ConsumerTrendsValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleConsumerTrendsValidation(
            ConsumerTrendsValidationException exception
    ) {
        return Map.of("message", exception.getMessage());
    }
}
