package com.canmakan.backend.family.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyExceptionHandler")
class FamilyExceptionHandlerTest {

    private static final String MESSAGE_KEY = "message";

    private FamilyExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FamilyExceptionHandler();
    }

    @Test
    @DisplayName("handleValidation uses the field error's default message when present")
    void handleValidationUsesFieldErrorMessage() {
        FieldError fieldError = mock(FieldError.class);
        when(fieldError.getDefaultMessage()).thenReturn("email must not be blank");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        Map<String, String> response = handler.handleValidation(ex);

        assertEquals(Map.of(MESSAGE_KEY, "email must not be blank"), response);
    }

    @Test
    @DisplayName("handleValidation falls back to a generic message when there is no field error")
    void handleValidationFallsBackWhenNoFieldError() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldError()).thenReturn(null);
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        Map<String, String> response = handler.handleValidation(ex);

        assertEquals(Map.of(MESSAGE_KEY, "Request validation failed."), response);
    }

    @Test
    @DisplayName("handleValidation falls back to a generic message when the field error has no default message")
    void handleValidationFallsBackWhenFieldErrorMessageIsNull() {
        FieldError fieldError = mock(FieldError.class);
        when(fieldError.getDefaultMessage()).thenReturn(null);
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldError()).thenReturn(fieldError);
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        Map<String, String> response = handler.handleValidation(ex);

        assertEquals(Map.of(MESSAGE_KEY, "Request validation failed."), response);
    }

    @Test
    @DisplayName("handleUnreadable returns a fixed message for an unreadable request body")
    void handleUnreadableReturnsFixedMessage() {
        Map<String, String> response = handler.handleUnreadable();

        assertEquals(Map.of(MESSAGE_KEY, "Request body is required."), response);
    }

    @Test
    @DisplayName("handleIllegalArgument passes through the exception message when present")
    void handleIllegalArgumentUsesExceptionMessage() {
        Map<String, String> response = handler.handleIllegalArgument(new IllegalArgumentException("bad family id"));

        assertEquals(Map.of(MESSAGE_KEY, "bad family id"), response);
    }

    @Test
    @DisplayName("handleIllegalArgument falls back to a generic message when the exception has none")
    void handleIllegalArgumentFallsBackWhenMessageIsNull() {
        Map<String, String> response = handler.handleIllegalArgument(new IllegalArgumentException());

        assertEquals(Map.of(MESSAGE_KEY, "Invalid request."), response);
    }

    @Test
    @DisplayName("handleAlreadyInFamily passes through the exception message")
    void handleAlreadyInFamilyUsesExceptionMessage() {
        Map<String, String> response =
                handler.handleAlreadyInFamily(new AlreadyInFamilyException("already in a family"));

        assertEquals(Map.of(MESSAGE_KEY, "already in a family"), response);
    }

    @Test
    @DisplayName("handleInvitationConflict passes through the exception message")
    void handleInvitationConflictUsesExceptionMessage() {
        Map<String, String> response =
                handler.handleInvitationConflict(new InvitationConflictException("invitation already claimed"));

        assertEquals(Map.of(MESSAGE_KEY, "invitation already claimed"), response);
    }

    @Test
    @DisplayName("handleInvitationExpired passes through the exception message")
    void handleInvitationExpiredUsesExceptionMessage() {
        Map<String, String> response =
                handler.handleInvitationExpired(new InvitationExpiredException("invitation expired"));

        assertEquals(Map.of(MESSAGE_KEY, "invitation expired"), response);
    }

    @Test
    @DisplayName("handleInvitationNotFound passes through the exception message")
    void handleInvitationNotFoundUsesExceptionMessage() {
        Map<String, String> response =
                handler.handleInvitationNotFound(new InvitationNotFoundException("invitation not found"));

        assertEquals(Map.of(MESSAGE_KEY, "invitation not found"), response);
    }
}
