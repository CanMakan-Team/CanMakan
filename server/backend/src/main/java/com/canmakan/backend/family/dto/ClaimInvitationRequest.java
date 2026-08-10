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
    String invitationToken
) {
    public ClaimInvitationRequest {
        invitationToken = invitationToken == null ? null : invitationToken.strip();
    }
}
