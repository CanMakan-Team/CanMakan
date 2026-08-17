package com.canmakan.backend.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestrictionId;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.family.FamilyAuthorizationService;
import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.family.dto.ActiveProfileResponse;
import com.canmakan.backend.family.dto.ClaimInvitationRequest;
import com.canmakan.backend.family.dto.CreateDependantProfileRequest;
import com.canmakan.backend.family.dto.CreateFamilyRequest;
import com.canmakan.backend.family.dto.CreateInvitationRequest;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.FamilyRestrictionSumRes;
import com.canmakan.backend.family.dto.FamilyScanHistoryDto;
import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.dto.UserSearchResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InactiveProfileException;
import com.canmakan.backend.family.exception.InvitationConflictException;
import com.canmakan.backend.family.exception.InvitationExpiredException;
import com.canmakan.backend.family.exception.LastPrimaryAdminException;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.model.InvitationStatus;
import com.canmakan.backend.family.model.UserPreference;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.family.repository.UserPreferenceRepository;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** UC6: FamilyService tests 
 * 
 * @author Amelia
*/
@ExtendWith(MockitoExtension.class)
@DisplayName("UC8 - 11 test cases: FamilyService")
class FamilyServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private FamilyInvitationRepository familyInvitationRepository;
    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private DietaryProfileRepository dietaryProfileRepository;
    @Mock
    private DietaryRestrictionRepository dietaryRestrictionRepository;
    @Mock
    private DietaryProfileService dietaryProfileService;
    @Mock
    private ScanRepository scanRepository;

    private InvitationEmailService invitationEmailService;
    private FamilyInviteNotifier familyInviteNotifier;
    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setPublicBaseUrl("http://localhost:5173");
        inviteProperties.setExpiryDays(7);
        invitationEmailService = org.mockito.Mockito.mock(InvitationEmailService.class);
        familyInviteNotifier = org.mockito.Mockito.mock(FamilyInviteNotifier.class);
        FamilyAuthorizationService familyAuthorization = new FamilyAuthorizationService(
            familyMemberRepository,
            dietaryProfileRepository
        );
        familyService = new FamilyService(
            userAccountRepository,
            familyRepository,
            familyMemberRepository,
            familyInvitationRepository,
            userPreferenceRepository,
            dietaryProfileRepository,
            dietaryRestrictionRepository,
            dietaryProfileService,
            familyAuthorization,
            scanRepository,
            inviteProperties,
            invitationEmailService,
            familyInviteNotifier
        );
    }

    @Test
    @DisplayName("creates family, PRIMARY_ADMIN membership, and SELF profile")
    void createFamilyHappyPath() {
        when(familyMemberRepository.existsByIdUserId(14L)).thenReturn(false);

        UserAccount user = new UserAccount();
        user.setId(14L);
        user.setEmail("person@example.com");
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(user));

        when(familyRepository.saveAndFlush(any(Family.class))).thenAnswer(invocation -> {
            Family family = invocation.getArgument(0);
            family.setId(50L);
            return family;
        });
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class))).thenAnswer(invocation -> {
            DietaryProfile profile = invocation.getArgument(0);
            profile.setId(77L);
            return profile;
        });

        FamilyMeResponse response = familyService.createFamily(14L, new CreateFamilyRequest("  Wong Family  "));

        assertEquals(50L, response.familyId());
        assertEquals("Wong Family", response.familyName());
        assertEquals(FamilyMember.ROLE_PRIMARY_ADMIN, response.memberRole());
        assertEquals(77L, response.selfProfileId());
        assertEquals(14L, response.createdByUserId());

        ArgumentCaptor<FamilyMember> memberCaptor = ArgumentCaptor.forClass(FamilyMember.class);
        verify(familyMemberRepository).saveAndFlush(memberCaptor.capture());
        assertEquals(FamilyMember.ROLE_PRIMARY_ADMIN, memberCaptor.getValue().getMemberRole());
        assertEquals(50L, memberCaptor.getValue().getFamilyId());
        assertEquals(14L, memberCaptor.getValue().getUserId());
    }

    @Test
    @DisplayName("reuses an existing standalone SELF profile rather than duplicating it")
    void createFamilyReusesExistingStandaloneProfile() {
        when(familyMemberRepository.existsByIdUserId(14L)).thenReturn(false);

        UserAccount user = new UserAccount();
        user.setId(14L);
        user.setEmail("person@example.com");
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(user));

        DietaryProfile existingProfile = new DietaryProfile();
        existingProfile.setId(77L);
        existingProfile.setProfileName("Sarah Abdullah");
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.of(existingProfile));

        when(familyRepository.saveAndFlush(any(Family.class))).thenAnswer(invocation -> {
            Family family = invocation.getArgument(0);
            family.setId(50L);
            return family;
        });
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        familyService.createFamily(14L, new CreateFamilyRequest("Abdullah Family"));

        ArgumentCaptor<DietaryProfile> profileCaptor = ArgumentCaptor.forClass(DietaryProfile.class);
        verify(dietaryProfileRepository).saveAndFlush(profileCaptor.capture());
        DietaryProfile savedProfile = profileCaptor.getValue();

        assertEquals(77L, savedProfile.getId());
        assertEquals("Sarah Abdullah", savedProfile.getProfileName());
        assertEquals(50L, savedProfile.getFamily().getId());
        assertEquals("SELF", savedProfile.getRelationship());
        assertTrue(savedProfile.isPrimary());
    }

    @Test
    @DisplayName("rejects second create when user already has membership")
    void createFamilyConflictWhenAlreadyMember() {
        when(familyMemberRepository.existsByIdUserId(4L)).thenReturn(true);

        assertThrows(
            AlreadyInFamilyException.class,
            () -> familyService.createFamily(4L, new CreateFamilyRequest("Another"))
        );
        verify(familyRepository, never()).saveAndFlush(any(Family.class));
    }

    @Test
    @DisplayName("maps membership unique constraint violation to already-in-family")
    void createFamilyMapsUniqueViolation() {
        when(familyMemberRepository.existsByIdUserId(14L)).thenReturn(false);
        UserAccount user = new UserAccount();
        user.setId(14L);
        user.setEmail("person@example.com");
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(user));
        SQLException duplicateKey = new SQLException("Duplicate entry", "23000", 1062);
        when(familyRepository.saveAndFlush(any(Family.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate", duplicateKey));

        assertThrows(
            AlreadyInFamilyException.class,
            () -> familyService.createFamily(14L, new CreateFamilyRequest("Race"))
        );
    }

    @Test
    @DisplayName("rethrows unrelated data integrity violations")
    void createFamilyRethrowsUnrelatedIntegrityViolation() {
        when(familyMemberRepository.existsByIdUserId(14L)).thenReturn(false);
        UserAccount user = new UserAccount();
        user.setId(14L);
        user.setEmail("person@example.com");
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(user));
        when(familyRepository.saveAndFlush(any(Family.class)))
            .thenThrow(new DataIntegrityViolationException("fk_other_table"));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> familyService.createFamily(14L, new CreateFamilyRequest("Other"))
        );
    }

    @Test
    @DisplayName("rejects create when authenticated user id is unknown")
    void createFamilyMissingUser() {
        when(familyMemberRepository.existsByIdUserId(999L)).thenReturn(false);
        when(userAccountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
            AuthenticatedUserNotFoundException.class,
            () -> familyService.createFamily(999L, new CreateFamilyRequest("Orphan"))
        );
        verify(familyRepository, never()).saveAndFlush(any(Family.class));
    }

    @Test
    @DisplayName("getMyFamily returns context for membership")
    void getMyFamilyHappyPath() {
        FamilyMember membership = new FamilyMember(
            new FamilyMember.FamilyMemberId(1L, 4L),
            FamilyMember.ROLE_PRIMARY_ADMIN,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(4L)).thenReturn(Optional.of(membership));

        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Tan Family");
        family.setCreatedByUserId(4L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));

        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);
        when(dietaryProfileRepository.findByLinkedUser_Id(4L)).thenReturn(Optional.of(profile));

        FamilyMeResponse response = familyService.getMyFamily(4L);
        assertEquals(1L, response.familyId());
        assertEquals("Tan Family", response.familyName());
        assertEquals(1L, response.selfProfileId());
    }

    @Test
    @DisplayName("getMyFamily 404 when no membership")
    void getMyFamilyNotFound() {
        when(familyMemberRepository.findMembershipByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(FamilyNotFoundException.class, () -> familyService.getMyFamily(99L));
    }

    @Test
    @DisplayName("profileNameFromUser uses email local-part")
    void profileNameFromEmail() {
        UserAccount user = new UserAccount();
        user.setEmail("sarah.tan@example.com");
        assertEquals("sarah.tan", FamilyService.profileNameFromUser(user));
    }

    @Test
    @DisplayName("user-search returns NOT_REGISTERED for unknown email")
    void searchUnknownEmail() {
        stubPrimaryAdmin(10L, 1L);
        when(userAccountRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(1L, "new@example.com"))
            .thenReturn(Optional.empty());

        UserSearchResponse result = familyService.searchUserByEmail(10L, "new@example.com");
        assertEquals(UserSearchResponse.ACCOUNT_NOT_REGISTERED, result.accountStatus());
        assertEquals(UserSearchResponse.LINK_NOT_LINKED, result.familyLinkStatus());
        assertTrue(result.maskedEmail().contains("@"));
    }

    @Test
    @DisplayName("invite unknown email returns url and code")
    void inviteUnknownEmail() {
        stubPrimaryAdmin(10L, 1L);
        when(userAccountRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(1L, "new@example.com"))
            .thenReturn(Optional.empty());
        when(familyInvitationRepository.existsByInvitationToken(any())).thenReturn(false);
        when(familyInvitationRepository.existsByInviteCode(any())).thenReturn(false);
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class))).thenAnswer(invocation -> {
            FamilyInvitation invitation = invocation.getArgument(0);
            invitation.setId(88L);
            return invitation;
        });
        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(invitationEmailService.sendInvitationEmail(eq("Host Family"), any(InvitationResponse.class)))
            .thenReturn(true);

        InvitationResponse response = familyService.createInvitation(
            10L, new CreateInvitationRequest("new@example.com", "SPOUSE"));

        assertEquals(88L, response.invitationId());
        assertFalse(response.inviteeRegistered());
        assertTrue(response.emailSent());
        assertTrue(response.inviteUrl().startsWith("http://localhost:5173/invite/"));
        assertEquals(8, response.inviteCode().length());
        ArgumentCaptor<FamilyInvitation> invitationCaptor =
            ArgumentCaptor.forClass(FamilyInvitation.class);
        verify(familyInvitationRepository).saveAndFlush(invitationCaptor.capture());
        assertEquals("SPOUSE", invitationCaptor.getValue().getRelationship());
        assertEquals(InvitationStatus.PENDING, response.status());
        verify(familyInviteNotifier).notifyInviteSent(any(FamilyInvitation.class), isNull());
    }

    @Test
    @DisplayName("invite registered user also notifies the invitee inbox")
    void inviteRegisteredUserNotifiesInvitee() {
        stubPrimaryAdmin(10L, 1L);
        UserAccount invitee = new UserAccount();
        invitee.setId(30L);
        invitee.setEmail("jamie@example.com");
        when(userAccountRepository.findByEmail("jamie@example.com")).thenReturn(Optional.of(invitee));
        when(familyMemberRepository.existsByIdUserId(30L)).thenReturn(false);
        when(familyInvitationRepository.findPendingByFamilyAndEmail(1L, "jamie@example.com"))
            .thenReturn(Optional.empty());
        when(familyInvitationRepository.existsByInvitationToken(any())).thenReturn(false);
        when(familyInvitationRepository.existsByInviteCode(any())).thenReturn(false);
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class))).thenAnswer(invocation -> {
            FamilyInvitation invitation = invocation.getArgument(0);
            invitation.setId(88L);
            return invitation;
        });
        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(invitationEmailService.sendInvitationEmail(eq("Host Family"), any(InvitationResponse.class)))
            .thenReturn(true);

        InvitationResponse response = familyService.createInvitation(
            10L, new CreateInvitationRequest("jamie@example.com", "CHILD"));

        assertTrue(response.inviteeRegistered());
        verify(familyInviteNotifier).notifyInviteSent(any(FamilyInvitation.class), eq(invitee));
    }

    @Test
    @DisplayName("invite rejects MEMBER")
    void inviteForbiddenForMember() {
        FamilyMember membership = new FamilyMember(
            new FamilyMember.FamilyMemberId(1L, 20L),
            FamilyMember.ROLE_MEMBER,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(20L)).thenReturn(Optional.of(membership));

        assertThrows(
            FamilyForbiddenException.class,
            () -> familyService.createInvitation(20L, new CreateInvitationRequest("a@b.com", "OTHER"))
        );
    }

    @Test
    @DisplayName("invite rejects when a pending invitation was already emailed")
    void inviteRejectsExistingPendingAfterSuccessfulSend() {
        stubPrimaryAdmin(10L, 1L);
        FamilyInvitation existing = new FamilyInvitation();
        existing.setId(5L);
        existing.setFamilyId(1L);
        existing.setInvitedEmail("dup@example.com");
        existing.setInvitationToken("existing-token");
        existing.setInviteCode("ABCD1234");
        existing.setStatus(InvitationStatus.PENDING);
        existing.setExpiresAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(userAccountRepository.findByEmail("dup@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(1L, "dup@example.com"))
            .thenReturn(Optional.of(existing));

        InvitationConflictException exception = assertThrows(
            InvitationConflictException.class,
            () -> familyService.createInvitation(10L, new CreateInvitationRequest("dup@example.com", "PARENT"))
        );

        assertEquals(
            "An invitation email was already sent to this address.",
            exception.getMessage()
        );
        verify(invitationEmailService, never()).sendInvitationEmail(any(), any());
        verify(familyInvitationRepository, never()).saveAndFlush(any());
        verify(familyInvitationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("invite does not keep PENDING when email send fails")
    void inviteDoesNotKeepPendingWhenEmailFails() {
        stubPrimaryAdmin(10L, 1L);
        when(userAccountRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(familyInvitationRepository.findPendingByFamilyAndEmail(1L, "new@example.com"))
            .thenReturn(Optional.empty());
        when(familyInvitationRepository.existsByInvitationToken(any())).thenReturn(false);
        when(familyInvitationRepository.existsByInviteCode(any())).thenReturn(false);
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class))).thenAnswer(invocation -> {
            FamilyInvitation invitation = invocation.getArgument(0);
            invitation.setId(88L);
            return invitation;
        });
        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(invitationEmailService.sendInvitationEmail(eq("Host Family"), any(InvitationResponse.class)))
            .thenReturn(false);

        InvitationResponse response = familyService.createInvitation(
            10L, new CreateInvitationRequest("new@example.com", "SPOUSE"));

        assertFalse(response.emailSent());
        verify(familyInvitationRepository).delete(any(FamilyInvitation.class));
        verify(familyInvitationRepository).flush();
        verify(familyInviteNotifier, never()).notifyInviteSent(any(), any());
    }

    @Test
    @DisplayName("dependant create applies restrictions and has no membership")
    void createDependant() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class))).thenAnswer(invocation -> {
            DietaryProfile profile = invocation.getArgument(0);
            profile.setId(55L);
            return profile;
        });
        DietaryRestriction peanut = new DietaryRestriction();
        peanut.setId(3L);
        peanut.setCode("PEANUT");
        when(dietaryRestrictionRepository.findByCodeIgnoreCase("PEANUT")).thenReturn(Optional.of(peanut));

        var response = familyService.createDependantProfile(
            10L,
            new CreateDependantProfileRequest("Child", "CHILD", List.of("PEANUT"), List.of())
        );

        assertEquals(55L, response.profileId());
        assertEquals(1L, response.familyId());
        verify(dietaryProfileService).saveDietaryRestrictionSelections(eq(55L), any());
        verify(familyMemberRepository, never()).saveAndFlush(any(FamilyMember.class));
    }

    @Test
    @DisplayName("restriction summary includes dependants")
    void summaryIncludesDependants() {
        FamilyMember membership = new FamilyMember(
            new FamilyMember.FamilyMemberId(1L, 10L),
            FamilyMember.ROLE_PRIMARY_ADMIN,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(10L)).thenReturn(Optional.of(membership));
        when(familyMemberRepository.findActiveMembersByFamilyId(1L)).thenReturn(List.of(membership));

        DietaryProfile self = new DietaryProfile();
        self.setId(1L);
        self.setProfileName("Admin");
        when(dietaryProfileRepository.findByLinkedUser_Id(10L)).thenReturn(Optional.of(self));

        DietaryProfile dependant = new DietaryProfile();
        dependant.setId(2L);
        dependant.setProfileName("Toddler");
        when(dietaryProfileRepository.findDependantProfilesByFamilyId(1L))
            .thenReturn(List.of(dependant));

        FamilyRestrictionSumRes summary = familyService.getFamilyRestrictionSummary(10L);
        assertEquals(2, summary.getFamilyMembers().size());
        assertEquals(0L, summary.getFamilyMembers().get(1).getUserId());
        assertEquals(2L, summary.getFamilyMembers().get(1).getProfileId());
        assertEquals("Toddler", summary.getFamilyMembers().get(1).getName());
    }

    @Test
    @DisplayName("listFamilyMembers returns linked user and dependant roster rows")
    void listFamilyMembersIncludesLinkedAndDependant() {
        FamilyMember membership = new FamilyMember(
            new FamilyMember.FamilyMemberId(1L, 10L),
            FamilyMember.ROLE_PRIMARY_ADMIN,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(10L)).thenReturn(Optional.of(membership));
        when(familyMemberRepository.findActiveMembersByFamilyId(1L)).thenReturn(List.of(membership));

        DietaryProfile self = new DietaryProfile();
        self.setId(1L);
        self.setProfileName("Admin");
        self.setRelationship("SELF");
        self.setActive(true);
        when(dietaryProfileRepository.findByLinkedUser_Id(10L)).thenReturn(Optional.of(self));

        UserAccount admin = new UserAccount();
        admin.setId(10L);
        admin.setEmail("admin@example.com");
        when(userAccountRepository.findById(10L)).thenReturn(Optional.of(admin));

        DietaryProfile dependant = new DietaryProfile();
        dependant.setId(2L);
        dependant.setProfileName("Toddler");
        dependant.setRelationship("CHILD");
        dependant.setActive(true);
        when(dietaryProfileRepository.findAllDependantProfilesByFamilyId(1L))
            .thenReturn(List.of(dependant));

        List<com.canmakan.backend.family.dto.FamilyMemberRosterDto> rows =
            familyService.listFamilyMembers(10L);

        assertEquals(2, rows.size());
        assertEquals(10L, rows.get(0).memberId());
        assertEquals(1L, rows.get(0).profileId());
        assertEquals("PRIMARY_ADMIN", rows.get(0).memberRole());
        assertTrue(rows.get(0).profileActive());
        assertEquals("REGISTERED_USER", rows.get(0).source());
        assertEquals("a***n@example.com", rows.get(0).maskedEmail());
        assertEquals(2L, rows.get(1).memberId());
        assertEquals("DEPENDANT_PROFILE", rows.get(1).source());
        assertEquals("Toddler", rows.get(1).profileName());
        assertTrue(rows.get(1).profileActive());
    }

    @Test
    @DisplayName("listFamilyMembers without membership throws 404-style FamilyNotFoundException")
    void listFamilyMembersNotInFamily() {
        when(familyMemberRepository.findMembershipByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(FamilyNotFoundException.class, () -> familyService.listFamilyMembers(99L));
    }

    @Test
    @DisplayName("accept invitation joins as MEMBER")
    void acceptInvitationHappyPath() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(30L)).thenReturn(false);

        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        family.setCreatedByUserId(10L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(dietaryProfileRepository.findByLinkedUser_Id(30L)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class))).thenAnswer(invocation -> {
            DietaryProfile profile = invocation.getArgument(0);
            profile.setId(99L);
            return profile;
        });

        FamilyMeResponse response = familyService.acceptInvitation(30L, "tok");

        ArgumentCaptor<DietaryProfile> profileCaptor = ArgumentCaptor.forClass(DietaryProfile.class);
        verify(dietaryProfileRepository).saveAndFlush(profileCaptor.capture());
        assertFalse(profileCaptor.getValue().isPrimary());
        assertEquals("SPOUSE", profileCaptor.getValue().getRelationship());

        assertEquals(1L, response.familyId());
        assertEquals(FamilyMember.ROLE_MEMBER, response.memberRole());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        verify(familyInviteNotifier).notifyInviteAccepted(invitation, "invitee@example.com");
    }

    @Test
    @DisplayName("UC9 acceptance reuses standalone SELF profile and preserves its data")
    void acceptInvitationReusesExistingStandaloneProfile() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        when(familyInvitationRepository.findByInvitationToken("tok"))
            .thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(30L)).thenReturn(false);

        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        family.setCreatedByUserId(10L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        DietaryProfile existingProfile = new DietaryProfile();
        existingProfile.setId(88L);
        existingProfile.setLinkedUser(user);
        existingProfile.setProfileName("Chosen Profile Name");
        existingProfile.setRelationship("SELF");
        existingProfile.setPrimary(true);
        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setId(2L);
        ProfileRestriction selection = new ProfileRestriction();
        selection.setId(new ProfileRestrictionId(88L, 2L));
        selection.setDietaryProfile(existingProfile);
        selection.setDietaryRestriction(restriction);
        selection.setSeverityLevel("INTOLERANCE");
        existingProfile.getProfileRestrictions().add(selection);

        when(dietaryProfileRepository.findByLinkedUser_Id(30L))
            .thenReturn(Optional.of(existingProfile));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        FamilyMeResponse response = familyService.acceptInvitation(30L, "tok");

        ArgumentCaptor<DietaryProfile> profileCaptor =
            ArgumentCaptor.forClass(DietaryProfile.class);
        verify(dietaryProfileRepository).saveAndFlush(profileCaptor.capture());
        DietaryProfile savedProfile = profileCaptor.getValue();
        assertSame(existingProfile, savedProfile);
        assertSame(family, savedProfile.getFamily());
        assertEquals("Chosen Profile Name", savedProfile.getProfileName());
        assertTrue(savedProfile.getProfileRestrictions().contains(selection));
        assertEquals("INTOLERANCE", selection.getSeverityLevel());
        assertEquals(88L, response.selfProfileId());
        assertFalse(savedProfile.isPrimary());
        assertEquals("SPOUSE", savedProfile.getRelationship());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
    }

    @Test
    @DisplayName("UC9 claim with a typed profile name uses it instead of the email-derived placeholder")
    void claimInvitationWithProfileNameUsesTypedName() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(30L)).thenReturn(false);

        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        family.setCreatedByUserId(10L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(dietaryProfileRepository.findByLinkedUser_Id(30L)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        FamilyMeResponse response = familyService.claimInvitation(
            30L, new ClaimInvitationRequest("tok", "Person Name"));

        ArgumentCaptor<DietaryProfile> profileCaptor = ArgumentCaptor.forClass(DietaryProfile.class);
        verify(dietaryProfileRepository).saveAndFlush(profileCaptor.capture());
        assertEquals("Person Name", profileCaptor.getValue().getProfileName());
        assertEquals(1L, response.familyId());
    }

    @Test
    @DisplayName("UC9 claim with a typed profile name overrides an auto-provisioned placeholder")
    void claimInvitationWithProfileNameOverridesExistingPlaceholder() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(30L)).thenReturn(false);

        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        family.setCreatedByUserId(10L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // Simulates a placeholder already inserted from an earlier attempt that
        // was not given the typed name (e.g. a plain login-triggered claim).
        DietaryProfile placeholder = new DietaryProfile();
        placeholder.setId(88L);
        placeholder.setLinkedUser(user);
        placeholder.setProfileName("invitee");
        when(dietaryProfileRepository.findByLinkedUser_Id(30L))
            .thenReturn(Optional.of(placeholder));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        familyService.claimInvitation(30L, new ClaimInvitationRequest("tok", "Person Name"));

        ArgumentCaptor<DietaryProfile> profileCaptor = ArgumentCaptor.forClass(DietaryProfile.class);
        verify(dietaryProfileRepository).saveAndFlush(profileCaptor.capture());
        assertEquals("Person Name", profileCaptor.getValue().getProfileName());
    }

    @Test
    @DisplayName("accept expired invitation throws InvitationExpiredException")
    void acceptExpired() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        invitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(InvitationExpiredException.class,
            () -> familyService.acceptInvitation(30L, "tok"));
        assertEquals(InvitationStatus.EXPIRED, invitation.getStatus());
    }

    @Test
    @DisplayName("accept with email mismatch throws FamilyForbiddenException")
    void acceptEmailMismatch() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("other@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));

        assertThrows(FamilyForbiddenException.class,
            () -> familyService.acceptInvitation(30L, "tok"));
    }

    @Test
    @DisplayName("accept while already in family throws AlreadyInFamilyException")
    void acceptAlreadyInFamily() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyMemberRepository.existsByIdUserId(30L)).thenReturn(true);

        assertThrows(AlreadyInFamilyException.class,
            () -> familyService.acceptInvitation(30L, "tok"));
    }

    @Test
    @DisplayName("accept already-final invitation throws InvitationConflictException")
    void acceptAlreadyFinal() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        invitation.setStatus(InvitationStatus.ACCEPTED);
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));

        assertThrows(InvitationConflictException.class,
            () -> familyService.acceptInvitation(30L, "tok"));
    }

    @Test
    @DisplayName("decline marks invitation DECLINED")
    void declineInvitation() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        when(familyInvitationRepository.findByInvitationToken("tok")).thenReturn(Optional.of(invitation));
        when(familyInvitationRepository.saveAndFlush(any(FamilyInvitation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        familyService.declineInvitation(30L, "tok");

        assertEquals(InvitationStatus.DECLINED, invitation.getStatus());
        verify(familyMemberRepository, never()).saveAndFlush(any(FamilyMember.class));
        verify(familyInviteNotifier).notifyInviteDeclined(invitation, "invitee@example.com");
    }

    @Test
    @DisplayName("list pending invitations includes family display fields")
    void listPendingInvitations() {
        UserAccount user = new UserAccount();
        user.setId(30L);
        user.setEmail("invitee@example.com");
        when(userAccountRepository.findById(30L)).thenReturn(Optional.of(user));

        FamilyInvitation invitation = pendingInvitation("tok", "invitee@example.com");
        invitation.setId(5L);
        invitation.setInvitedByUserId(10L);
        when(familyInvitationRepository.findPendingByEmail("invitee@example.com"))
            .thenReturn(List.of(invitation));

        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Host Family");
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));

        UserAccount admin = new UserAccount();
        admin.setId(10L);
        admin.setEmail("admin@example.com");
        when(userAccountRepository.findById(10L)).thenReturn(Optional.of(admin));

        List<com.canmakan.backend.family.dto.PendingInvitationResponse> rows =
            familyService.listMyPendingInvitations(30L);

        assertEquals(1, rows.size());
        assertEquals("Host Family", rows.get(0).familyName());
        assertEquals("admin@example.com", rows.get(0).invitedByDisplayName());
        assertFalse(rows.get(0).expired());
    }

    @Test
    @DisplayName("getActiveProfile defaults to self profile for family member")
    void getActiveProfileDefaultsToSelf() {
        stubMembership(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));
        when(userPreferenceRepository.findById(10L)).thenReturn(Optional.empty());

        DietaryProfile self = activeProfile(77L, "Admin", family, true);
        UserAccount user = new UserAccount();
        user.setId(10L);
        self.setLinkedUser(user);
        when(dietaryProfileRepository.findByLinkedUser_Id(10L)).thenReturn(Optional.of(self));
        when(dietaryProfileRepository.findById(77L)).thenReturn(Optional.of(self));

        ActiveProfileResponse response = familyService.getActiveProfile(10L);

        assertEquals(77L, response.profileId());
        assertEquals("Admin", response.profileName());
    }

    @Test
    @DisplayName("setActiveProfile persists preference for in-family profile")
    void setActiveProfilePersists() {
        stubMembership(10L, 1L);
        Family family = new Family();
        family.setId(1L);

        DietaryProfile dependant = activeProfile(88L, "Child", family, true);
        when(dietaryProfileRepository.findById(88L)).thenReturn(Optional.of(dependant));
        when(userPreferenceRepository.findById(10L)).thenReturn(Optional.empty());
        when(userPreferenceRepository.saveAndFlush(any(UserPreference.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ActiveProfileResponse response = familyService.setActiveProfile(10L, 88L);

        assertEquals(88L, response.profileId());
        verify(userPreferenceRepository).saveAndFlush(any(UserPreference.class));
    }

    @Test
    @DisplayName("setActiveProfile rejects profile outside family")
    void setActiveProfileForbiddenOutsideFamily() {
        stubMembership(10L, 1L);
        Family otherFamily = new Family();
        otherFamily.setId(99L);
        DietaryProfile outsider = activeProfile(55L, "Other", otherFamily, true);
        when(dietaryProfileRepository.findById(55L)).thenReturn(Optional.of(outsider));

        assertThrows(
            FamilyForbiddenException.class,
            () -> familyService.setActiveProfile(10L, 55L)
        );
    }

    @Test
    @DisplayName("setActiveProfile rejects inactive profile")
    void setActiveProfileInactive() {
        Family family = new Family();
        family.setId(1L);
        DietaryProfile inactive = activeProfile(88L, "Child", family, false);
        when(dietaryProfileRepository.findById(88L)).thenReturn(Optional.of(inactive));

        assertThrows(
            InactiveProfileException.class,
            () -> familyService.setActiveProfile(10L, 88L)
        );
    }

    @Test
    @DisplayName("getProfilesForFamilyMember returns profiles for caller family")
    void getProfilesForFamilyMemberOk() {
        stubMembership(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile profile = activeProfile(77L, "Admin", family, true);
        when(dietaryProfileService.getProfilesByFamilyId(1L)).thenReturn(List.of(
            new com.canmakan.backend.dietaryprofile.dto.DietaryProfileSummaryDto(
                profile.getId(),
                profile.getProfileName(),
                1L,
                profile.getRelationship(),
                "AD",
                profile.isPrimary(),
                profile.isActive())
        ));

        List<com.canmakan.backend.dietaryprofile.dto.DietaryProfileSummaryDto> rows =
            familyService.getProfilesForFamilyMember(10L, 1L);

        assertEquals(1, rows.size());
        assertEquals(77L, rows.get(0).id());
    }

    @Test
    @DisplayName("getProfilesForFamilyMember rejects another family id")
    void getProfilesForFamilyMemberForbidden() {
        stubMembership(10L, 1L);

        assertThrows(
            FamilyForbiddenException.class,
            () -> familyService.getProfilesForFamilyMember(10L, 99L)
        );
    }

    @Test
    @DisplayName("assertProfileAuthorizedForScan rejects profile outside family")
    void assertProfileAuthorizedForScanForbidden() {
        stubMembership(10L, 1L);
        Family otherFamily = new Family();
        otherFamily.setId(99L);
        DietaryProfile outsider = activeProfile(55L, "Other", otherFamily, true);
        when(dietaryProfileRepository.findById(55L)).thenReturn(Optional.of(outsider));

        assertThrows(
            FamilyForbiddenException.class,
            () -> familyService.assertProfileAuthorizedForScan(10L, 55L)
        );
    }

    @Test
    @DisplayName("updateProfileMetadata updates name for primary admin")
    void updateProfileMetadataOk() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile dependant = activeProfile(88L, "Child", family, true);
        dependant.setLinkedUser(null);
        when(dietaryProfileRepository.findById(88L)).thenReturn(Optional.of(dependant));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = familyService.updateProfileMetadata(
            10L,
            88L,
            new com.canmakan.backend.family.dto.UpdateProfileRequest(
                "Toddler", "CHILD", null, null));

        assertEquals("Toddler", response.profileName());
        assertEquals("CHILD", response.relationship());
        assertEquals(88L, response.profileId());
    }

    @Test
    @DisplayName("setProfileActive deactivates profile")
    void setProfileActiveDeactivates() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile profile = activeProfile(88L, "Child", family, true);
        when(dietaryProfileRepository.findById(88L)).thenReturn(Optional.of(profile));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(userPreferenceRepository.findByActiveProfileId(88L)).thenReturn(List.of());

        var response = familyService.setProfileActive(10L, 88L, false);

        assertFalse(response.active());
        assertFalse(profile.isActive());
    }

    @Test
    @DisplayName("setProfileActive rejects deactivating the caller's own admin profile")
    void setProfileActiveRejectsOwnAdminProfile() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile profile = activeProfile(77L, "Admin", family, true);
        UserAccount linked = new UserAccount();
        linked.setId(10L);
        profile.setLinkedUser(linked);
        when(dietaryProfileRepository.findById(77L)).thenReturn(Optional.of(profile));

        FamilyForbiddenException ex = assertThrows(
            FamilyForbiddenException.class,
            () -> familyService.setProfileActive(10L, 77L, false)
        );
        assertEquals("Cannot deactivate your own family admin profile.", ex.getMessage());
        verify(dietaryProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("setProfileActive rejects deactivating the last primary admin's profile")
    void setProfileActiveRejectsLastPrimaryAdmin() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile profile = activeProfile(99L, "Other Admin", family, true);
        UserAccount linked = new UserAccount();
        linked.setId(20L);
        profile.setLinkedUser(linked);
        when(dietaryProfileRepository.findById(99L)).thenReturn(Optional.of(profile));
        FamilyMember otherAdminMembership = new FamilyMember(
            new FamilyMember.FamilyMemberId(1L, 20L),
            FamilyMember.ROLE_PRIMARY_ADMIN,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(20L))
            .thenReturn(Optional.of(otherAdminMembership));
        when(familyMemberRepository.countActivePrimaryAdmins(1L)).thenReturn(1L);

        LastPrimaryAdminException ex = assertThrows(
            LastPrimaryAdminException.class,
            () -> familyService.setProfileActive(10L, 99L, false)
        );
        assertEquals("Cannot deactivate the family admin profile.", ex.getMessage());
        verify(dietaryProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("removeFamilyMember rejects last primary admin")
    void removeFamilyMemberLastAdminConflict() {
        stubPrimaryAdmin(10L, 1L);
        FamilyMember target = new FamilyMember(
            new FamilyMember.FamilyMemberId(1L, 10L),
            FamilyMember.ROLE_PRIMARY_ADMIN,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(10L))
            .thenReturn(Optional.of(target));
        when(familyMemberRepository.countActivePrimaryAdmins(1L)).thenReturn(1L);

        assertThrows(
            com.canmakan.backend.family.exception.LastPrimaryAdminException.class,
            () -> familyService.removeFamilyMember(10L, 10L)
        );
        verify(familyMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("removeDependantProfile soft-deactivates and detaches family")
    void removeDependantProfileSoft() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile dependant = activeProfile(88L, "Child", family, true);
        dependant.setLinkedUser(null);
        when(dietaryProfileRepository.findById(88L)).thenReturn(Optional.of(dependant));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(userPreferenceRepository.findByActiveProfileId(88L)).thenReturn(List.of());

        familyService.removeDependantProfile(10L, 88L);

        assertFalse(dependant.isActive());
        org.junit.jupiter.api.Assertions.assertNull(dependant.getFamily());
    }

    @Test
    @DisplayName("assertMayEditRestrictions allows self linked profile")
    void assertMayEditRestrictionsSelfOk() {
        Family family = new Family();
        family.setId(1L);
        DietaryProfile self = activeProfile(77L, "Admin", family, true);
        UserAccount user = new UserAccount();
        user.setId(10L);
        self.setLinkedUser(user);
        when(dietaryProfileRepository.findById(77L)).thenReturn(Optional.of(self));

        familyService.assertMayEditRestrictions(10L, 77L);
    }

    @Test
    @DisplayName("assertMayEditRestrictions allows PRIMARY_ADMIN to edit another adult")
    void assertMayEditRestrictionsAdminOtherAdultOk() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile other = activeProfile(99L, "Member", family, true);
        UserAccount linked = new UserAccount();
        linked.setId(20L);
        other.setLinkedUser(linked);
        when(dietaryProfileRepository.findById(99L)).thenReturn(Optional.of(other));

        familyService.assertMayEditRestrictions(10L, 99L);
    }

    @Test
    @DisplayName("assertMayEditRestrictions rejects non-admin editing another adult")
    void assertMayEditRestrictionsOtherAdultForbiddenForMember() {
        FamilyMember membership = new FamilyMember(
            new FamilyMember.FamilyMemberId(1L, 10L),
            FamilyMember.ROLE_MEMBER,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(10L)).thenReturn(Optional.of(membership));

        Family family = new Family();
        family.setId(1L);
        DietaryProfile other = activeProfile(99L, "Member", family, true);
        UserAccount linked = new UserAccount();
        linked.setId(20L);
        other.setLinkedUser(linked);
        when(dietaryProfileRepository.findById(99L)).thenReturn(Optional.of(other));

        assertThrows(
            FamilyForbiddenException.class,
            () -> familyService.assertMayEditRestrictions(10L, 99L)
        );
    }

    @Test
    @DisplayName("UC4: listFamilyScans returns UNSAFE wire verdict for PRIMARY_ADMIN")
    void listFamilyScansForPrimaryAdmin() {
        stubPrimaryAdmin(10L, 1L);
        Family family = new Family();
        family.setId(1L);
        DietaryProfile profile = activeProfile(5L, "Admin", family, true);
        when(dietaryProfileRepository.findAllProfilesByFamilyId(1L)).thenReturn(List.of(profile));

        ScanProduct product = new ScanProduct();
        product.setProductName("Crunchy Peanut Bar");
        product.setBrand("Good Day");
        Scan scan = new Scan();
        scan.setId(501L);
        scan.setProfileId(5L);
        scan.setVerdict("UNSAFE");
        scan.setProduct(product);
        scan.setAiExplanation("Peanut matched");
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(any()))
            .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = familyService.listFamilyScans(10L);

        assertEquals(1, rows.size());
        assertEquals("UNSAFE", rows.get(0).verdict());
        assertEquals("Crunchy Peanut Bar", rows.get(0).product());
        assertEquals("Admin", rows.get(0).evaluatedProfile());
    }

    @Test
    @DisplayName("UC4: listFamilyScans rejects non-PRIMARY_ADMIN with 403 semantics")
    void listFamilyScansRejectsNonAdmin() {
        stubMembership(11L, 1L);

        FamilyForbiddenException ex = assertThrows(
            FamilyForbiddenException.class,
            () -> familyService.listFamilyScans(11L)
        );
        assertEquals(FamilyAuthorizationService.PRIMARY_ADMIN_REQUIRED, ex.getMessage());
        verify(scanRepository, never()).findByProfileIdInWithProductOrderByScannedAtDesc(any());
    }

    private static DietaryProfile activeProfile(
            Long id, String name, Family family, boolean active) {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(id);
        profile.setProfileName(name);
        profile.setFamily(family);
        profile.setRelationship("SELF");
        profile.setPrimary(true);
        profile.setActive(active);
        return profile;
    }

    private void stubMembership(long userId, long familyId) {
        FamilyMember membership = new FamilyMember(
            new FamilyMember.FamilyMemberId(familyId, userId),
            FamilyMember.ROLE_MEMBER,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(userId))
            .thenReturn(Optional.of(membership));
    }

    private static FamilyInvitation pendingInvitation(String token, String email) {
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setId(5L);
        invitation.setFamilyId(1L);
        invitation.setInvitedByUserId(10L);
        invitation.setInvitedEmail(email);
        invitation.setRelationship("SPOUSE");
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
        invitation.setInvitationToken(token);
        invitation.setInviteCode("ABCD1234");
        return invitation;
    }

    private void stubPrimaryAdmin(long userId, long familyId) {
        FamilyMember membership = new FamilyMember(
            new FamilyMember.FamilyMemberId(familyId, userId),
            FamilyMember.ROLE_PRIMARY_ADMIN,
            true,
            null
        );
        when(familyMemberRepository.findMembershipByUserId(userId)).thenReturn(Optional.of(membership));
    }
}
