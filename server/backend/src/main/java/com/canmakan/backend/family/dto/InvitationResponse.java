package com.canmakan.backend.family.dto;

import com.canmakan.backend.family.model.InvitationStatus;
import java.time.Instant;

/** Created invitation with shareable link and short code. */
public record InvitationResponse(
    Long invitationId,
    String invitedEmail,
    String invitationToken,
    String inviteCode,
    String inviteUrl,
    InvitationStatus status,
    Instant expiresAt,
    boolean inviteeRegistered,
    boolean emailSent
) {
}
