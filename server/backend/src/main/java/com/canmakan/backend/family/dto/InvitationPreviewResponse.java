package com.canmakan.backend.family.dto;

/**
 * Public invite lookup used to lock registration to the invited email.
 *
 * @author Amelia
 */
public record InvitationPreviewResponse(
    String invitedEmail,
    String familyName,
    boolean expired
) {
}
