package com.canmakan.backend.admin.exception;

import com.canmakan.backend.admin.AdminController;
import com.canmakan.backend.analytics.exception.ConsumerTrendsValidationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(AdminUserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleAdminUserNotFound(AdminUserNotFoundException exception) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(AdminScanFeedbackNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleAdminScanFeedbackNotFound(
            AdminScanFeedbackNotFoundException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(InvalidAccountStatusRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidAccountStatusRequest(
            InvalidAccountStatusRequestException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(ProtectedAccountOperationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleProtectedAccountOperation(
            ProtectedAccountOperationException exception
    ) {
        return Map.of("message", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Request validation failed.");
        return Map.of("message", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return Map.of("message", "Request body is required.");
    }
}
