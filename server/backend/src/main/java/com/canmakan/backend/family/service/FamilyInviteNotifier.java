package com.canmakan.backend.family.service;

import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.notification.NotificationService;
import com.canmakan.backend.notification.model.NotificationType;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes family-invite cards into the general notifications inbox.
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class FamilyInviteNotifier {

    static final String REFERENCE_INVITATION = "INVITATION";

    private final NotificationService notificationService;
    private final FamilyInvitationRepository familyInvitationRepository;
    private final FamilyRepository familyRepository;
    private final UserAccountRepository userAccountRepository;

    @Transactional
    public void notifyInviteSent(FamilyInvitation invitation, UserAccount inviteeOrNull) {
        String familyName = familyName(invitation);
        notificationService.upsert(
            invitation.getInvitedByUserId(),
            NotificationType.FAMILY_INVITE_UPDATE,
            REFERENCE_INVITATION,
            invitation.getId(),
            "Invite sent to " + invitation.getInvitedEmail() + ".",
            familyName,
            null,
            null
        );
        if (inviteeOrNull != null) {
            upsertIncoming(inviteeOrNull.getId(), invitation, familyName);
        }
    }

    @Transactional
    public void notifyInviteAccepted(FamilyInvitation invitation, String inviteeEmail) {
        notificationService.upsert(
            invitation.getInvitedByUserId(),
            NotificationType.FAMILY_INVITE_UPDATE,
            REFERENCE_INVITATION,
            invitation.getId(),
            friendlyName(inviteeEmail) + " joined your family.",
            familyName(invitation),
            null,
            null
        );
        notificationService.deleteByReference(
            NotificationType.FAMILY_INVITE_REQUEST,
            REFERENCE_INVITATION,
            invitation.getId()
        );
    }

    @Transactional
    public void notifyInviteDeclined(FamilyInvitation invitation, String inviteeEmail) {
        notificationService.upsert(
            invitation.getInvitedByUserId(),
            NotificationType.FAMILY_INVITE_UPDATE,
            REFERENCE_INVITATION,
            invitation.getId(),
            friendlyName(inviteeEmail) + " declined this time.",
            familyName(invitation),
            null,
            null
        );
        notificationService.deleteByReference(
            NotificationType.FAMILY_INVITE_REQUEST,
            REFERENCE_INVITATION,
            invitation.getId()
        );
    }

    @Transactional
    public void hydrateIncomingInvites(long userId, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        List<FamilyInvitation> pending = familyInvitationRepository.findPendingByEmail(normalized);
        for (FamilyInvitation invitation : pending) {
            upsertIncoming(userId, invitation, familyName(invitation));
        }
    }

    private void upsertIncoming(long userId, FamilyInvitation invitation, String familyName) {
        String invitedBy = userAccountRepository.findById(invitation.getInvitedByUserId())
            .map(account -> friendlyName(account.getEmail()))
            .orElse("your family admin");
        notificationService.upsert(
            userId,
            NotificationType.FAMILY_INVITE_REQUEST,
            REFERENCE_INVITATION,
            invitation.getId(),
            "Join " + familyName + "?",
            "Invited by " + invitedBy + ".",
            invitation.getInvitationToken(),
            invitation.getExpiresAt()
        );
    }

    private String familyName(FamilyInvitation invitation) {
        return familyRepository.findById(invitation.getFamilyId())
            .map(Family::getFamilyName)
            .orElse("a family circle");
    }

    static String friendlyName(String email) {
        if (email == null || email.isBlank()) {
            return "Someone";
        }
        String local = email.strip();
        int at = local.indexOf('@');
        if (at > 0) {
            local = local.substring(0, at);
        }
        local = local.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (local.isEmpty()) {
            return email.strip();
        }
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
    }
}
