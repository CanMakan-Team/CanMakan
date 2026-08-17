package com.canmakan.backend.family;

import com.canmakan.backend.family.exception.InvitationEmailMismatchException;
import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.model.InvitationStatus;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Blocks invite-flow registration unless the email matches the pending invite.
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class InvitationRegistrationGuard {

    public static final String MISMATCH_MESSAGE =
        "Use the email address this invitation was sent to.";

    private final FamilyInvitationRepository familyInvitationRepository;

    public void requireEmailMatchesPendingInvite(String invitationToken, String email) {
        if (invitationToken == null || invitationToken.isBlank()) {
            return;
        }
        FamilyInvitation invitation = familyInvitationRepository
            .findByInvitationToken(invitationToken.strip())
            .orElse(null);
        if (invitation == null
                || invitation.getStatus() != InvitationStatus.PENDING
                || isExpired(invitation)) {
            return;
        }
        String normalizedEmail = FamilyService.normalizeEmail(email);
        if (normalizedEmail == null
                || !invitation.getInvitedEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new InvitationEmailMismatchException(MISMATCH_MESSAGE);
        }
    }

    private static boolean isExpired(FamilyInvitation invitation) {
        return invitation.getExpiresAt() == null
            || !invitation.getExpiresAt().isAfter(Instant.now());
    }
}
