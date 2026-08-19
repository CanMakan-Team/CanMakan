package com.canmakan.backend.shared.security;

import java.io.Serializable;
import java.util.Objects;

/** Authentication-safe account identity placed behind Spring Security's user details. */
public record AuthenticatedPrincipal(
    Long userId,
    String email,
    boolean active,
    SystemRole systemRole
) implements Serializable {

    public AuthenticatedPrincipal {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(systemRole, "systemRole is required");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
    }
}
