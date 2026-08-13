package com.canmakan.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Request body for public user registration. */
public record RegistrationRequest(
    /** Deprecated compatibility field. Profile names belong to authenticated profile setup. */
    String name,

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Pattern(
        regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", // example@abc.com
        message = "Email must be valid.")
    @Size(max = 255, message = "Email must not exceed 255 characters.")
    String email,

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
        message = "Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character.")
    String password,

    /**
     * Transitional deep-link token. When it matches a pending invitation,
     * registration email must be the invited address.
     */
    @Size(max = 100, message = "Invitation token must not exceed 100 characters.")
    String invitationToken
) {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    public RegistrationRequest {
        // Retain the legacy JSON field for compatibility, but never carry it into
        // account registration. Profile names belong to authenticated profile setup.
        name = null;
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        invitationToken = invitationToken == null || invitationToken.isBlank()
            ? null
            : invitationToken.strip();
    }

    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
            || password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_PASSWORD_BYTES;
    }

    @Override
    public String toString() {
        return "RegistrationRequest[name=" + name + ", email=" + email
            + ", password=<redacted>, invitationToken="
            + (invitationToken == null ? "null" : "<present>") + "]";
    }

    /** Reject fields outside the public registration contract. */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported registration field: " + propertyName);
    }
}
