package com.canmakan.backend.family.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Request body to create a family invitation for an email address. 
 * 
 * @author Amelia
*/
public record CreateInvitationRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Pattern(
        regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", // example: "user@example.com"
        message = "Email must be valid.")
    @Size(max = 255, message = "Email must not exceed 255 characters.")
    String email
) {
    public CreateInvitationRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
