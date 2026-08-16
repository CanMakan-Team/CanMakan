package com.canmakan.backend.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body to claim a pending invitation while authenticated.
 *
 * @author Amelia
*/
public record ClaimInvitationRequest(
    @NotBlank(message = "Invitation token is required.")
    @Size(max = 100, message = "Invitation token must not exceed 100 characters.")
    String invitationToken,

    // Optional: the profile name the caller typed during registration, so the
    // SELF profile auto-provisioned by claiming this invitation is created
    // with that name instead of a placeholder derived from the email address.
    @Size(max = 100, message = "Profile name must not exceed 100 characters.")
    String profileName
) {
    public ClaimInvitationRequest {
        invitationToken = invitationToken == null ? null : invitationToken.strip();
        profileName = profileName == null ? null : profileName.strip();
    }
}
