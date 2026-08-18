package com.canmakan.backend.dietaryprofile.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@ExtendWith(MockitoExtension.class)
@DisplayName("DietaryProfileExceptionHandler")
class DietaryProfileExceptionHandlerTest {

    private static final String MESSAGE_KEY = "message";
    private static final String INVALID_REQUEST_MESSAGE = "Invalid dietary profile request.";

    private DietaryProfileExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DietaryProfileExceptionHandler();
    }

    @Test
    @DisplayName("handleValidation uses the field error's default message when present")
    void handleValidationUsesFieldErrorMessage() {
        FieldError fieldError = mock(FieldError.class);
        when(fieldError.getDefaultMessage()).thenReturn("profileName must not be blank");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        Map<String, String> response = handler.handleValidation(exception);

        assertEquals(Map.of(MESSAGE_KEY, "profileName must not be blank"), response);
    }

    @Test
    @DisplayName("handleValidation falls back to a generic message when there is no field error")
    void handleValidationFallsBackWhenNoFieldError() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldError()).thenReturn(null);
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        Map<String, String> response = handler.handleValidation(exception);

        assertEquals(Map.of(MESSAGE_KEY, INVALID_REQUEST_MESSAGE), response);
    }

    @Test
    @DisplayName("handleValidation falls back to a generic message when the field error has no default message")
    void handleValidationFallsBackWhenFieldErrorMessageIsNull() {
        FieldError fieldError = mock(FieldError.class);
        when(fieldError.getDefaultMessage()).thenReturn(null);
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        Map<String, String> response = handler.handleValidation(exception);

        assertEquals(Map.of(MESSAGE_KEY, INVALID_REQUEST_MESSAGE), response);
    }

    @Test
    @DisplayName("handleInvalidRequest uses the IllegalArgumentException's message when present")
    void handleInvalidRequestUsesIllegalArgumentMessage() {
        Map<String, String> response =
            handler.handleInvalidRequest(new IllegalArgumentException("severity must be valid"));

        assertEquals(Map.of(MESSAGE_KEY, "severity must be valid"), response);
    }

    @Test
    @DisplayName("handleInvalidRequest falls back to a generic message when the IllegalArgumentException has none")
    void handleInvalidRequestFallsBackWhenIllegalArgumentMessageIsNull() {
        Map<String, String> response = handler.handleInvalidRequest(new IllegalArgumentException());

        assertEquals(Map.of(MESSAGE_KEY, INVALID_REQUEST_MESSAGE), response);
    }

    @Test
    @DisplayName("handleInvalidRequest falls back to a generic message for a non-IllegalArgumentException")
    void handleInvalidRequestFallsBackForOtherExceptionTypes() {
        HttpMessageNotReadableException unreadable =
            mock(HttpMessageNotReadableException.class);

        Map<String, String> response = handler.handleInvalidRequest(unreadable);

        assertEquals(Map.of(MESSAGE_KEY, INVALID_REQUEST_MESSAGE), response);
    }

    @Test
    @DisplayName("handleSelfProfileConflict passes through the exception message")
    void handleSelfProfileConflictUsesExceptionMessage() {
        Map<String, String> response =
            handler.handleSelfProfileConflict(new SelfProfileAlreadyExistsException());

        assertEquals(
            Map.of(MESSAGE_KEY, "A SELF profile already exists for this account."), response);
    }

    @Test
    @DisplayName("handleSelfProfileNotFound passes through the exception message")
    void handleSelfProfileNotFoundUsesExceptionMessage() {
        Map<String, String> response =
            handler.handleSelfProfileNotFound(new SelfProfileNotFoundException());

        assertEquals(
            Map.of(MESSAGE_KEY, "No SELF profile exists for this account yet."), response);
    }
}
