package com.canmakan.backend.family.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.model.InvitationStatus;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.notification.NotificationService;
import com.canmakan.backend.notification.model.NotificationType;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyInviteNotifier")
class FamilyInviteNotifierTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private FamilyInvitationRepository familyInvitationRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private UserAccountRepository userAccountRepository;

    private FamilyInviteNotifier familyInviteNotifier;

    @BeforeEach
    void setUp() {
        familyInviteNotifier = new FamilyInviteNotifier(
            notificationService,
            familyInvitationRepository,
            familyRepository,
            userAccountRepository
        );
    }

    @Test
    void friendlyNameUsesLocalPart() {
        assertEquals("Jamie", FamilyInviteNotifier.friendlyName("jamie@example.com"));
    }

    @Test
    void notifyInviteSentWritesAdminAndInviteeCards() {
        FamilyInvitation invitation = pendingInvite();
        stubFamily();
        UserAccount invitee = new UserAccount();
        invitee.setId(30L);
        invitee.setEmail("jamie@example.com");
        UserAccount admin = new UserAccount();
        admin.setId(10L);
        admin.setEmail("amelia@example.com");
        when(userAccountRepository.findById(10L)).thenReturn(Optional.of(admin));

        familyInviteNotifier.notifyInviteSent(invitation, invitee);

        verify(notificationService).upsert(
            eq(10L),
            eq(NotificationType.FAMILY_INVITE_UPDATE),
            eq(new NotificationService.NotificationReference("INVITATION", 5L)),
            eq("Invite sent to jamie@example.com."),
            eq("Wong Family"),
            isNull(),
            isNull()
        );
        verify(notificationService).upsert(
            30L,
            NotificationType.FAMILY_INVITE_REQUEST,
            new NotificationService.NotificationReference("INVITATION", 5L),
            "Join Wong Family?",
            "Invited by Amelia.",
            "tok",
            invitation.getExpiresAt()
        );
    }

    @Test
    void notifyInviteAcceptedUpdatesAdminAndRemovesInviteeCard() {
        FamilyInvitation invitation = pendingInvite();
        stubFamily();

        familyInviteNotifier.notifyInviteAccepted(invitation, "jamie@example.com");

        verify(notificationService).upsert(
            eq(10L),
            eq(NotificationType.FAMILY_INVITE_UPDATE),
            eq(new NotificationService.NotificationReference("INVITATION", 5L)),
            eq("Jamie joined your family."),
            eq("Wong Family"),
            isNull(),
            isNull()
        );
        verify(notificationService).deleteByReference(
            NotificationType.FAMILY_INVITE_REQUEST,
            "INVITATION",
            5L
        );
    }

    @Test
    void hydrateIncomingCreatesInviteeCard() {
        FamilyInvitation invitation = pendingInvite();
        stubFamily();
        UserAccount admin = new UserAccount();
        admin.setId(10L);
        admin.setEmail("amelia@example.com");
        when(familyInvitationRepository.findPendingByEmail("jamie@example.com"))
            .thenReturn(List.of(invitation));
        when(userAccountRepository.findById(10L)).thenReturn(Optional.of(admin));

        familyInviteNotifier.hydrateIncomingInvites(30L, "jamie@example.com");

        verify(notificationService).upsert(
            30L,
            NotificationType.FAMILY_INVITE_REQUEST,
            new NotificationService.NotificationReference("INVITATION", 5L),
            "Join Wong Family?",
            "Invited by Amelia.",
            "tok",
            invitation.getExpiresAt()
        );
    }

    private FamilyInvitation pendingInvite() {
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setId(5L);
        invitation.setFamilyId(1L);
        invitation.setInvitedByUserId(10L);
        invitation.setInvitedEmail("jamie@example.com");
        invitation.setInvitationToken("tok");
        invitation.setInviteCode("ABCD1234");
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return invitation;
    }

    private void stubFamily() {
        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Wong Family");
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
    }
}
