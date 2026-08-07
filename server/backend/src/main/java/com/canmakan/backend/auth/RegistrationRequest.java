package com.canmakan.backend.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Request body for public user registration. */
public record RegistrationRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(max = 255, message = "Email must not exceed 255 characters.")
    String email,

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    String password
) {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    public RegistrationRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
            || password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_PASSWORD_BYTES;
    }

    @Override
    public String toString() {
        return "RegistrationRequest[email=" + email + ", password=<redacted>]";
    }

    /** Reject fields outside the frozen public registration contract. */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported registration field: " + propertyName);
    }
}
