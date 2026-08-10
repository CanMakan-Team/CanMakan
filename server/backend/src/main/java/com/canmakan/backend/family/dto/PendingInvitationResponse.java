package com.canmakan.backend.family.dto;

import com.canmakan.backend.family.model.InvitationStatus;
import java.time.Instant;

/**
 * Invitee inbox row for a PENDING invitation (UC10).
 *
 * @author Amelia
 */
public record PendingInvitationResponse(
    Long invitationId,
    Long familyId,
    String familyName,
    String invitedByDisplayName,
    String invitationToken,
    String inviteCode,
    InvitationStatus status,
    Instant expiresAt,
    boolean expired
) {
}
