package com.canmakan.backend.shared.exception;

import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InactiveProfileException;
import com.canmakan.backend.family.exception.LastPrimaryAdminException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cross-cutting exception translation shared by multiple controllers.
 *
 * <p>Includes auth identity failures and family/profile authorization outcomes
 * reused outside family controllers (for example {@code POST /api/scan/assess}).
 *
 * @author Amelia
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Missing users.id for the authenticated caller
    @ExceptionHandler(AuthenticatedUserNotFoundException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleAuthenticatedUserNotFound(
            AuthenticatedUserNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(FamilyForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleFamilyForbidden(FamilyForbiddenException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(InactiveProfileException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleInactiveProfile(InactiveProfileException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(FamilyNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleFamilyNotFound(FamilyNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(LastPrimaryAdminException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleLastPrimaryAdmin(LastPrimaryAdminException ex) {
        return Map.of("message", ex.getMessage());
    }
}
