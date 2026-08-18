package com.canmakan.backend.family.dto;

import com.canmakan.backend.family.model.FamilyRelationshipToAdmin;
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
        // example: "user@example.com"; domain labels exclude '.' so there is only one way
        // to split them, avoiding the super-linear backtracking of overlapping [^\s@]+ groups.
        regexp = "^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+){1,10}$",
        message = "Email must be valid.")
    @Size(max = 255, message = "Email must not exceed 255 characters.")
    String email,

    @NotBlank(message = "Relationship is required.")
    @Pattern(
        regexp = FamilyRelationshipToAdmin.PATTERN,
        message = "Relationship must be SPOUSE, CHILD, PARENT, DEPENDANT, or OTHER.")
    @Size(max = 30, message = "Relationship must be at most 30 characters.")
    String relationship
) {
    public CreateInvitationRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        relationship = FamilyRelationshipToAdmin.normalize(relationship);
    }
}
