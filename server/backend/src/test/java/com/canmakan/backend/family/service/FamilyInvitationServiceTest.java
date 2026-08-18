package com.canmakan.backend.family.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.config.InviteProperties;
import com.canmakan.backend.family.dto.ClaimInvitationRequest;
import com.canmakan.backend.family.dto.CreateInvitationRequest;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.InvitationPreviewResponse;
import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.dto.PendingInvitationResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InvitationConflictException;
import com.canmakan.backend.family.exception.InvitationExpiredException;
import com.canmakan.backend.family.exception.InvitationNotFoundException;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.model.InvitationStatus;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyInvitationService")
class FamilyInvitationServiceTest {

    private static final long ADMIN_ID = 10L;
    private static final long USER_ID = 30L;
    private static final long FAMILY_ID = 1L;

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private FamilyInvitationRepository familyInvitationRepository;
    @Mock
    private DietaryProfileRepository dietaryProfileRepository;
    @Mock
    private FamilyAuthorizationService familyAuthorization;
    @Mock
    private InvitationEmailService invitationEmailService;
    @Mock
    private FamilyInviteNotifier familyInviteNotifier;

    private FamilyInvitationService service;

    @BeforeEach
    void setUp() {
        lenient().when(familyInvitationRepository.existsByInvitationToken(anyString())).thenReturn(false);
        lenient().when(familyInvitationRepository.existsByInviteCode(anyString())).thenReturn(false);
        service = new FamilyInvitationService(
                userAccountRepository,
                familyRepository,
                familyMemberRepository,
                familyInvitationRepository,
                dietaryProfileRepository,
                familyAuthorization,
                new InviteProperties(),
                invitationEmailService,
                familyInviteNotifier);
    }

    // ---- createInvitation ----

    @Test
    @DisplayName("createInvitation saves, emails and notifies for a not-yet-registered invitee")
    void createInvitationHappyPathForUnregisteredInvitee() {
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(invitationEmailService.sendInvitationEmail(eq("Wong Family"), any(InvitationResponse.class)))
                .thenReturn(true);

        InvitationResponse response = service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE"));

        assertEquals("jamie@example.com", response.invitedEmail());
        assertTrue(response.emailSent());
        assertFalse(response.inviteeRegistered());
        assertTrue(response.inviteUrl().endsWith("/invite/" + response.invitationToken()));
        verify(familyInviteNotifier).notifyInviteSent(any(FamilyInvitation.class), eq(null));
        verify(familyInvitationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("createInvitation marks the invitee as registered and passes their account to the notifier")
    void createInvitationHappyPathForRegisteredInvitee() {
        UserAccount invitee = userAccount(40L, "jamie@example.com");
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.of(invitee));
        when(familyMemberRepository.existsByIdUserId(40L)).thenReturn(false);
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(invitationEmailService.sendInvitationEmail(anyString(), any(InvitationResponse.class)))
                .thenReturn(true);

        InvitationResponse response = service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE"));

        assertTrue(response.inviteeRegistered());
        verify(familyInviteNotifier).notifyInviteSent(any(FamilyInvitation.class), eq(invitee));
    }

    @Test
    @DisplayName("createInvitation rejects an invitee who already belongs to a family")
    void createInvitationRejectsInviteeAlreadyInFamily() {
        UserAccount invitee = userAccount(40L, "jamie@example.com");
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.of(invitee));
        when(familyMemberRepository.existsByIdUserId(40L)).thenReturn(true);

        assertThrows(InvitationConflictException.class, () -> service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE")));
    }

    @Test
    @DisplayName("createInvitation rejects a duplicate pending invitation for the same email")
    void createInvitationRejectsDuplicatePendingInvitation() {
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.of(pendingInvitation("jamie@example.com")));

        assertThrows(InvitationConflictException.class, () -> service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE")));
    }

    @Test
    @DisplayName("createInvitation rolls back the saved row when the email fails to send")
    void createInvitationRollsBackWhenEmailFails() {
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.empty());
        when(invitationEmailService.sendInvitationEmail(eq("a family circle"), any(InvitationResponse.class)))
                .thenReturn(false);

        InvitationResponse response = service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE"));

        assertFalse(response.emailSent());
        verify(familyInvitationRepository).delete(any(FamilyInvitation.class));
        verify(familyInvitationRepository).flush();
        verify(familyInviteNotifier, never()).notifyInviteSent(any(), any());
    }

    // ---- claim / accept overloads ----

    @Test
    @DisplayName("claimInvitation resolves by token, applies the claim and returns the family context")
    void claimInvitationHappyPath() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FamilyMeResponse response = service.claimInvitation(
                USER_ID, new ClaimInvitationRequest("tok", "Jamie Wong"));

        assertEquals(FAMILY_ID, response.familyId());
        assertEquals(FamilyMember.ROLE_MEMBER, response.memberRole());
        verify(familyInviteNotifier).notifyInviteAccepted(invitation, "jamie@example.com");
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
    }

    @Test
    @DisplayName("claiming with a blank profile name derives the profile name from the user's email instead")
    void claimInvitationDerivesProfileNameWhenSuppliedNameIsBlank() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", "   "));

        verify(dietaryProfileRepository).saveAndFlush(argThat(
                profile -> "jamie".equals(profile.getProfileName())));
    }

    @Test
    @DisplayName("acceptInvitation(userId, token) reaches the same claim flow as claimInvitation")
    void acceptInvitationTwoArgOverloadDelegatesToSharedFlow() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FamilyMeResponse response = service.acceptInvitation(USER_ID, "tok");

        assertEquals(FAMILY_ID, response.familyId());
    }

    @Test
    @DisplayName("acceptInvitation(userId, token, profileName) reaches the same claim flow as claimInvitation")
    void acceptInvitationThreeArgOverloadDelegatesToSharedFlow() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FamilyMeResponse response = service.acceptInvitation(USER_ID, "tok", "Jamie Wong");

        assertEquals(FAMILY_ID, response.familyId());
    }

    @Test
    @DisplayName("claimInvitation throws when the authenticated user cannot be found")
    void claimInvitationThrowsWhenUserMissing() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(AuthenticatedUserNotFoundException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
    }

    @Test
    @DisplayName("claimInvitation throws when the invitation token is blank")
    void claimInvitationThrowsWhenTokenBlank() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));

        assertThrows(IllegalArgumentException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("   ", null)));
    }

    @Test
    @DisplayName("claimInvitation throws when the invitation token is missing entirely")
    void claimInvitationThrowsWhenTokenNull() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));

        assertThrows(IllegalArgumentException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest(null, null)));
    }

    @Test
    @DisplayName("claimInvitation throws when the token does not resolve to any invitation")
    void claimInvitationThrowsWhenTokenUnresolved() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.empty());

        assertThrows(InvitationNotFoundException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
    }

    @Test
    @DisplayName("claimInvitation by token throws when the invitation is no longer pending")
    void claimInvitationByTokenThrowsWhenNotPending() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));

        assertThrows(InvitationConflictException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
    }

    @Test
    @DisplayName("claimInvitation by token expires a stale invitation and throws")
    void claimInvitationByTokenExpiresStaleInvitation() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));

        assertThrows(InvitationExpiredException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));

        assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
        verify(familyInvitationRepository).saveAndFlush(invitation);
    }

    @Test
    @DisplayName("claimInvitation by token throws when the invitation email does not match the caller")
    void claimInvitationByTokenThrowsOnEmailMismatch() {
        UserAccount user = userAccount(USER_ID, "someoneelse@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));

        assertThrows(FamilyForbiddenException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
    }

    @Test
    @DisplayName("claiming throws when the caller already belongs to a family")
    void claimInvitationThrowsWhenAlreadyInFamily() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(true);

        assertThrows(AlreadyInFamilyException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
    }

    @Test
    @DisplayName("claiming throws when the invitation's family no longer exists")
    void claimInvitationThrowsWhenFamilyMissing() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.empty());

        assertThrows(FamilyNotFoundException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
    }

    @Test
    @DisplayName("claiming without a profile name reuses an existing non-blank self profile name")
    void claimInvitationKeepsExistingProfileNameWhenNoneSupplied() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        DietaryProfile existingProfile = new DietaryProfile();
        existingProfile.setProfileName("Existing Name");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(existingProfile));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null));

        assertEquals("Existing Name", existingProfile.getProfileName());
    }

    @Test
    @DisplayName("claiming without a profile name derives one when the existing self profile name is a blank string")
    void claimInvitationDerivesProfileNameWhenExistingNameIsBlankString() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        DietaryProfile existingProfile = new DietaryProfile();
        existingProfile.setProfileName("   ");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(existingProfile));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null));

        assertEquals("jamie", existingProfile.getProfileName());
    }

    @Test
    @DisplayName("claiming translates a membership unique-constraint violation into AlreadyInFamilyException")
    void claimInvitationTranslatesUniqueConstraintViolation() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "uq_family_members_user_id violated");
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class))).thenThrow(violation);

        assertThrows(AlreadyInFamilyException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
    }

    @Test
    @DisplayName("claiming rethrows an unrelated data integrity violation unchanged")
    void claimInvitationRethrowsUnrelatedDataIntegrityViolation() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(USER_ID)).thenReturn(false);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        DataIntegrityViolationException violation = new DataIntegrityViolationException("unrelated failure");
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class))).thenThrow(violation);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.claimInvitation(USER_ID, new ClaimInvitationRequest("tok", null)));
        assertEquals(violation, thrown);
    }

    // ---- declineInvitation ----

    @Test
    @DisplayName("declineInvitation marks the invitation declined and notifies the admin")
    void declineInvitationHappyPath() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));

        service.declineInvitation(USER_ID, "tok");

        assertEquals(InvitationStatus.DECLINED, invitation.getStatus());
        verify(familyInvitationRepository).saveAndFlush(invitation);
        verify(familyInviteNotifier).notifyInviteDeclined(invitation, "jamie@example.com");
    }

    @Test
    @DisplayName("declineInvitation throws when the authenticated user cannot be found")
    void declineInvitationThrowsWhenUserMissing() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(AuthenticatedUserNotFoundException.class,
                () -> service.declineInvitation(USER_ID, "tok"));
    }

    @Test
    @DisplayName("declineInvitation throws when the token is blank")
    void declineInvitationThrowsWhenTokenBlank() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));

        assertThrows(IllegalArgumentException.class, () -> service.declineInvitation(USER_ID, "   "));
    }

    @Test
    @DisplayName("declineInvitation throws when the token does not resolve to an invitation")
    void declineInvitationThrowsWhenTokenUnresolved() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.empty());

        assertThrows(InvitationNotFoundException.class, () -> service.declineInvitation(USER_ID, "tok"));
    }

    @Test
    @DisplayName("declineInvitation throws when the invitation email does not match the caller")
    void declineInvitationThrowsOnEmailMismatch() {
        UserAccount user = userAccount(USER_ID, "someoneelse@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok"))
                .thenReturn(Optional.of(pendingInvitation("jamie@example.com")));

        assertThrows(FamilyForbiddenException.class, () -> service.declineInvitation(USER_ID, "tok"));
    }

    @Test
    @DisplayName("declineInvitation throws when the invitation is no longer pending")
    void declineInvitationThrowsWhenNotPending() {
        UserAccount user = userAccount(USER_ID, "jamie@example.com");
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));

        assertThrows(InvitationConflictException.class, () -> service.declineInvitation(USER_ID, "tok"));
    }

    // ---- listMyPendingInvitations ----

    @Test
    @DisplayName("listMyPendingInvitations throws when the authenticated user cannot be found")
    void listMyPendingInvitationsThrowsWhenUserMissing() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(AuthenticatedUserNotFoundException.class,
                () -> service.listMyPendingInvitations(USER_ID));
    }

    @Test
    @DisplayName("listMyPendingInvitations returns an empty list when there are no pending invitations")
    void listMyPendingInvitationsReturnsEmptyList() {
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));
        when(familyInvitationRepository.findPendingByEmail("jamie@example.com")).thenReturn(List.of());

        assertTrue(service.listMyPendingInvitations(USER_ID).isEmpty());
    }

    @Test
    @DisplayName("listMyPendingInvitations falls back to generic family/admin names when either lookup misses")
    void listMyPendingInvitationsFallsBackForMissingFamilyAndAdmin() {
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));
        when(familyInvitationRepository.findPendingByEmail("jamie@example.com")).thenReturn(List.of(invitation));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

        List<PendingInvitationResponse> results = service.listMyPendingInvitations(USER_ID);

        assertEquals(1, results.size());
        assertEquals("Family", results.get(0).familyName());
        assertEquals("Family admin", results.get(0).invitedByDisplayName());
        assertFalse(results.get(0).expired());
    }

    @Test
    @DisplayName("listMyPendingInvitations resolves the family name, admin email and expiry flag when found")
    void listMyPendingInvitationsResolvesRealNamesAndExpiry() {
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userAccountRepository.findById(USER_ID)).thenReturn(Optional.of(userAccount(USER_ID, "jamie@example.com")));
        when(familyInvitationRepository.findPendingByEmail("jamie@example.com")).thenReturn(List.of(invitation));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(userAccountRepository.findById(ADMIN_ID)).thenReturn(Optional.of(userAccount(ADMIN_ID, "amelia@example.com")));

        List<PendingInvitationResponse> results = service.listMyPendingInvitations(USER_ID);

        assertEquals("Wong Family", results.get(0).familyName());
        assertEquals("amelia@example.com", results.get(0).invitedByDisplayName());
        assertTrue(results.get(0).expired());
    }

    // ---- previewInvitation ----

    @Test
    @DisplayName("previewInvitation throws when the token is blank")
    void previewInvitationThrowsWhenTokenBlank() {
        assertThrows(InvitationNotFoundException.class, () -> service.previewInvitation("  "));
    }

    @Test
    @DisplayName("previewInvitation throws when the token is missing entirely")
    void previewInvitationThrowsWhenTokenNull() {
        assertThrows(InvitationNotFoundException.class, () -> service.previewInvitation(null));
    }

    @Test
    @DisplayName("previewInvitation throws when the token does not resolve to an invitation")
    void previewInvitationThrowsWhenTokenUnresolved() {
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.empty());

        assertThrows(InvitationNotFoundException.class, () -> service.previewInvitation("tok"));
    }

    @Test
    @DisplayName("previewInvitation falls back to a generic family name when the family is missing")
    void previewInvitationFallsBackWhenFamilyMissing() {
        when(familyInvitationRepository.findByInvitationToken("tok"))
                .thenReturn(Optional.of(pendingInvitation("jamie@example.com")));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.empty());

        InvitationPreviewResponse preview = service.previewInvitation("tok");

        assertEquals("a family circle", preview.familyName());
        assertEquals("jamie@example.com", preview.invitedEmail());
        assertFalse(preview.expired());
    }

    @Test
    @DisplayName("previewInvitation resolves the real family name and expiry flag")
    void previewInvitationResolvesFamilyNameAndExpiry() {
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));

        InvitationPreviewResponse preview = service.previewInvitation("tok");

        assertEquals("Wong Family", preview.familyName());
        assertTrue(preview.expired());
    }

    @Test
    @DisplayName("previewInvitation treats a missing expiry timestamp as already expired")
    void previewInvitationTreatsNullExpiryAsExpired() {
        FamilyInvitation invitation = pendingInvitation("jamie@example.com");
        invitation.setExpiresAt(null);
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));

        InvitationPreviewResponse preview = service.previewInvitation("tok");

        assertTrue(preview.expired());
    }

    // ---- token / code generation retry loop ----

    @Test
    @DisplayName("createInvitation retries the invitation token when the first candidate collides")
    void createInvitationRetriesInvitationTokenOnCollision() {
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.existsByInvitationToken(anyString()))
                .thenReturn(true, false);
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(invitationEmailService.sendInvitationEmail(anyString(), any(InvitationResponse.class)))
                .thenReturn(true);

        InvitationResponse response = service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE"));

        assertTrue(response.emailSent());
        verify(familyInvitationRepository, times(2)).existsByInvitationToken(anyString());
    }

    @Test
    @DisplayName("createInvitation gives up generating a unique invitation token after repeated collisions")
    void createInvitationGivesUpOnPersistentTokenCollision() {
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.existsByInvitationToken(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE")));
    }

    @Test
    @DisplayName("createInvitation retries the invite code when the first candidate collides")
    void createInvitationRetriesInviteCodeOnCollision() {
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.existsByInviteCode(anyString()))
                .thenReturn(true, false);
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(invitationEmailService.sendInvitationEmail(anyString(), any(InvitationResponse.class)))
                .thenReturn(true);

        InvitationResponse response = service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE"));

        assertTrue(response.emailSent());
        verify(familyInvitationRepository, times(2)).existsByInviteCode(anyString());
    }

    @Test
    @DisplayName("createInvitation gives up generating a unique invite code after repeated collisions")
    void createInvitationGivesUpOnPersistentInviteCodeCollision() {
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.existsByInviteCode(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE")));
    }

    @Test
    @DisplayName("createInvitation trims a trailing slash from the configured public base URL")
    void createInvitationTrimsTrailingSlashFromPublicBaseUrl() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setPublicBaseUrl("https://canmakan-project.web.app/");
        FamilyInvitationService serviceWithTrailingSlashBase = new FamilyInvitationService(
                userAccountRepository,
                familyRepository,
                familyMemberRepository,
                familyInvitationRepository,
                dietaryProfileRepository,
                familyAuthorization,
                inviteProperties,
                invitationEmailService,
                familyInviteNotifier);
        when(familyAuthorization.requirePrimaryAdmin(ADMIN_ID)).thenReturn(adminMembership());
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(FAMILY_ID, "jamie@example.com"))
                .thenReturn(Optional.empty());
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyRepository.findById(FAMILY_ID)).thenReturn(Optional.of(family()));
        when(invitationEmailService.sendInvitationEmail(anyString(), any(InvitationResponse.class)))
                .thenReturn(true);

        InvitationResponse response = serviceWithTrailingSlashBase.createInvitation(
                ADMIN_ID, new CreateInvitationRequest("jamie@example.com", "SPOUSE"));

        assertEquals(
                "https://canmakan-project.web.app/invite/" + response.invitationToken(),
                response.inviteUrl());
    }

    // ---- helpers ----

    private static UserAccount userAccount(long id, String email) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static FamilyMember adminMembership() {
        FamilyMember member = new FamilyMember();
        member.setId(new FamilyMember.FamilyMemberId(FAMILY_ID, ADMIN_ID));
        member.setMemberRole(FamilyMember.ROLE_PRIMARY_ADMIN);
        member.setIsActive(true);
        return member;
    }

    private static Family family() {
        Family family = new Family();
        family.setId(FAMILY_ID);
        family.setFamilyName("Wong Family");
        family.setCreatedByUserId(ADMIN_ID);
        return family;
    }

    private static FamilyInvitation pendingInvitation(String email) {
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setId(5L);
        invitation.setFamilyId(FAMILY_ID);
        invitation.setInvitedByUserId(ADMIN_ID);
        invitation.setInvitedEmail(email);
        invitation.setRelationship("SPOUSE");
        invitation.setInvitationToken("tok");
        invitation.setInviteCode("ABCD1234");
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return invitation;
    }
}
