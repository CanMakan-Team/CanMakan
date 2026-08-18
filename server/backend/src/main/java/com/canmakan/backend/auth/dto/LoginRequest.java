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

/**
 * Request body for email/password login (UC19).
 *
 * @author Amelia
 * @author YangMaowei
 */
public record LoginRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Pattern(
        // example@abc.com; domain labels exclude '.' so there is only one way to split
        // them, avoiding the super-linear backtracking of overlapping [^\s@]+ groups. The
        // repeated label group is bounded so matching cannot recurse arbitrarily deep.
        regexp = "^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+){1,10}$",
        message = "Email must be valid.")
    @Size(max = 255, message = "Email must not exceed 255 characters.")
    String email,

    @NotBlank(message = "Password is required.")
    @Size(min = 1, message = "Password is required.")
    String password
) {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null
            || password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_PASSWORD_BYTES;
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported login field: " + propertyName);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=<redacted>]";
    }

}
