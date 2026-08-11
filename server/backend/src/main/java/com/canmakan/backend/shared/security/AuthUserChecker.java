package com.canmakan.backend.shared.security;

import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;

/**
 * Helpers for validating the authenticated caller and reading their user id.
 * Unwraps the JWT principal to get the user id.
 * Ensures the user id is present and valid in edge cases
 *
 * @author Amelia
 */
public final class AuthUserChecker {

    private static final String USER_NOT_FOUND_MESSAGE = "Authenticated user was not found.";

    private AuthUserChecker() {
    }

    /**
     * Returns the caller's user id from the JWT principal, or throws when missing.
     */
    public static long requireUserId(AuthUserDetails userDetails) {
        if (userDetails == null || userDetails.getUserId() == null) {
            throw new AuthenticatedUserNotFoundException(USER_NOT_FOUND_MESSAGE);
        }
        return userDetails.getUserId();
    }
}
