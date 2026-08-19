package com.canmakan.backend.dietaryprofile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.dto.CreateSelfProfileRequest;
import com.canmakan.backend.dietaryprofile.dto.DietaryProfileSummaryDto;
import com.canmakan.backend.dietaryprofile.dto.SelfProfileResponse;
import com.canmakan.backend.dietaryprofile.exception.SelfProfileAlreadyExistsException;
import com.canmakan.backend.dietaryprofile.exception.SelfProfileNotFoundException;
import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestrictionId;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.repository.ProfileRestrictionRepository;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Backend testing for Use Case 1: Update App User Dietary Profile.
 */
@ExtendWith(MockitoExtension.class)
class DietaryProfileServiceTest {

    @Mock
    private DietaryProfileRepository dietaryProfileRepository;

    @Mock
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    @Mock
    private ProfileRestrictionRepository profileRestrictionRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @InjectMocks
    private DietaryProfileService dietaryProfileService;

    @Test
    @DisplayName("UC18 profile setup creates a linked SELF profile with restrictions")
    void createSelfProfilePersistsProfileAndRestrictionsAtomically() {
        UserAccount account = new UserAccount();
        account.setId(14L);
        account.setActive(true);
        DietaryRestriction restriction = createRestriction(2L);

        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(account));
        when(dietaryRestrictionRepository.findById(2L)).thenReturn(Optional.of(restriction));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> {
                DietaryProfile profile = invocation.getArgument(0);
                profile.setId(77L);
                return profile;
            });

        SelfProfileResponse response = dietaryProfileService.createSelfProfile(
            14L,
            new CreateSelfProfileRequest("Person Name", Map.of(2L, "strict_avoid"))
        );

        assertEquals(77L, response.profileId());
        assertEquals("Person Name", response.profileName());
        assertEquals("SELF", response.relationship());
        assertTrue(response.active());
        assertEquals(Map.of(2L, "STRICT_AVOID"), response.restrictions());

        org.mockito.ArgumentCaptor<DietaryProfile> profileCaptor =
            org.mockito.ArgumentCaptor.forClass(DietaryProfile.class);
        verify(dietaryProfileRepository, org.mockito.Mockito.times(2))
            .saveAndFlush(profileCaptor.capture());
        DietaryProfile saved = profileCaptor.getAllValues().get(1);
        assertSame(account, saved.getLinkedUser());
        assertEquals("SELF", saved.getRelationship());
        assertTrue(saved.isPrimary());
        assertEquals(1, saved.getProfileRestrictions().size());
        assertEquals(
            "STRICT_AVOID",
            saved.getProfileRestrictions().iterator().next().getSeverityLevel()
        );
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    @DisplayName("UC18 profile setup accepts INTOLERANCE severity")
    void createSelfProfileAcceptsIntoleranceSeverity() {
        UserAccount account = new UserAccount();
        account.setId(14L);
        DietaryRestriction restriction = createRestriction(2L);
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(account));
        when(dietaryRestrictionRepository.findById(2L)).thenReturn(Optional.of(restriction));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenAnswer(invocation -> {
                DietaryProfile profile = invocation.getArgument(0);
                profile.setId(77L);
                return profile;
            });

        SelfProfileResponse response = dietaryProfileService.createSelfProfile(
            14L,
            new CreateSelfProfileRequest("Person Name", Map.of(2L, "intolerance"))
        );

        assertEquals(Map.of(2L, "INTOLERANCE"), response.restrictions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONSENSE", "LOW", "PREFERENCE"})
    @DisplayName("UC18 profile setup rejects unsupported short severity values")
    void createSelfProfileRejectsUnsupportedSeverity(String severity) {
        UserAccount account = new UserAccount();
        account.setId(14L);
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(account));
        when(dietaryRestrictionRepository.findById(2L))
            .thenReturn(Optional.of(createRestriction(2L)));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.createSelfProfile(
                14L,
                new CreateSelfProfileRequest("Person Name", Map.of(2L, severity))
            )
        );

        assertEquals(
            "Restriction severity must be STRICT_AVOID or INTOLERANCE.",
            exception.getMessage()
        );
        verify(dietaryProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UC18 profile setup rejects blank severity")
    void createSelfProfileRejectsBlankSeverity() {
        UserAccount account = new UserAccount();
        account.setId(14L);
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(account));
        when(dietaryRestrictionRepository.findById(2L))
            .thenReturn(Optional.of(createRestriction(2L)));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.createSelfProfile(
                14L,
                new CreateSelfProfileRequest("Person Name", Map.of(2L, "  "))
            )
        );

        assertEquals("Restriction severity is required.", exception.getMessage());
        verify(dietaryProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UC18 linked-user unique race maps to duplicate SELF conflict")
    void linkedUserUniqueRaceMapsToSelfProfileConflict() {
        UserAccount account = new UserAccount();
        account.setId(14L);
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(account));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException(
                    "Duplicate entry '14' for key 'uq_dietary_profiles_linked_user'",
                    "23000",
                    1062
                )
            ));

        assertThrows(
            SelfProfileAlreadyExistsException.class,
            () -> dietaryProfileService.createSelfProfile(
                14L,
                new CreateSelfProfileRequest("Person Name", Map.of())
            )
        );
    }

    @Test
    @DisplayName("UC18 unrelated profile integrity failure is not mislabeled as duplicate")
    void unrelatedIntegrityFailureIsNotMappedToSelfProfileConflict() {
        UserAccount account = new UserAccount();
        account.setId(14L);
        DataIntegrityViolationException integrityFailure =
            new DataIntegrityViolationException("unrelated foreign-key failure");
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(account));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class)))
            .thenThrow(integrityFailure);

        DataIntegrityViolationException thrown = assertThrows(
            DataIntegrityViolationException.class,
            () -> dietaryProfileService.createSelfProfile(
                14L,
                new CreateSelfProfileRequest("Person Name", Map.of())
            )
        );

        assertSame(integrityFailure, thrown);
    }

    @Test
    @DisplayName("UC18 profile setup safely rejects a duplicate linked SELF profile")
    void createSelfProfileRejectsDuplicate() {
        DietaryProfile existing = new DietaryProfile();
        existing.setId(77L);
        when(dietaryProfileRepository.findByLinkedUser_Id(14L))
            .thenReturn(Optional.of(existing));

        assertThrows(
            SelfProfileAlreadyExistsException.class,
            () -> dietaryProfileService.createSelfProfile(
                14L,
                new CreateSelfProfileRequest("Person Name", Map.of())
            )
        );

        verify(userAccountRepository, never()).findById(any());
        verify(dietaryProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UC1 getSelfProfile returns the caller's existing profile and selections")
    void getSelfProfileReturnsExistingProfileWithRestrictions() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(77L);
        profile.setProfileName("Person Name");
        profile.setRelationship("SELF");
        profile.setActive(true);

        ProfileRestriction restriction = new ProfileRestriction();
        restriction.setId(new ProfileRestrictionId(77L, 2L));
        restriction.setDietaryProfile(profile);
        restriction.setDietaryRestriction(createRestriction(2L));
        restriction.setSeverityLevel("STRICT_AVOID");
        profile.setProfileRestrictions(new HashSet<>(Set.of(restriction)));

        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.of(profile));

        SelfProfileResponse response = dietaryProfileService.getSelfProfile(14L);

        assertEquals(77L, response.profileId());
        assertEquals("Person Name", response.profileName());
        assertEquals("SELF", response.relationship());
        assertTrue(response.active());
        assertEquals(Map.of(2L, "STRICT_AVOID"), response.restrictions());
    }

    @Test
    @DisplayName("UC1 getSelfProfile throws when the caller has no linked SELF profile yet")
    void getSelfProfileThrowsWhenNoneExists() {
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());

        assertThrows(
            SelfProfileNotFoundException.class,
            () -> dietaryProfileService.getSelfProfile(14L)
        );
    }

    @Test
    @DisplayName("UC1 updateSelfProfile renames the profile and replaces its restrictions")
    void updateSelfProfileUpdatesNameAndReplacesRestrictions() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(77L);
        profile.setProfileName("Old Name");
        profile.setRelationship("SELF");
        profile.setActive(true);

        ProfileRestriction existingRestriction = new ProfileRestriction();
        existingRestriction.setId(new ProfileRestrictionId(77L, 3L));
        existingRestriction.setDietaryProfile(profile);
        existingRestriction.setDietaryRestriction(createRestriction(3L));
        existingRestriction.setSeverityLevel("STRICT_AVOID");
        profile.setProfileRestrictions(new HashSet<>(Set.of(existingRestriction)));

        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.of(profile));
        when(dietaryRestrictionRepository.findById(2L)).thenReturn(Optional.of(createRestriction(2L)));
        when(dietaryProfileRepository.saveAndFlush(profile)).thenReturn(profile);

        SelfProfileResponse response = dietaryProfileService.updateSelfProfile(
            14L,
            new CreateSelfProfileRequest("New Name", Map.of(2L, "intolerance"))
        );

        assertEquals("New Name", profile.getProfileName());
        assertEquals(1, profile.getProfileRestrictions().size());
        assertEquals(
            2L,
            profile.getProfileRestrictions().iterator().next().getDietaryRestriction().getId()
        );
        assertEquals(77L, response.profileId());
        assertEquals("New Name", response.profileName());
        assertEquals(Map.of(2L, "INTOLERANCE"), response.restrictions());
        verify(dietaryProfileRepository).saveAndFlush(profile);
    }

    @Test
    @DisplayName("UC1 updateSelfProfile keeps PREFERENCE like the mobile restriction editor")
    void updateSelfProfileAcceptsPreferenceSeverity() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(77L);
        profile.setProfileName("Person Name");
        profile.setRelationship("SELF");
        profile.setActive(true);
        profile.setProfileRestrictions(new HashSet<>());

        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.of(profile));
        when(dietaryRestrictionRepository.findById(11L))
            .thenReturn(Optional.of(createRestriction(11L)));
        when(dietaryProfileRepository.saveAndFlush(profile)).thenReturn(profile);

        SelfProfileResponse response = dietaryProfileService.updateSelfProfile(
            14L,
            new CreateSelfProfileRequest("Person Name", Map.of(11L, "preference"))
        );

        assertEquals(Map.of(11L, "PREFERENCE"), response.restrictions());
        assertEquals(
            "PREFERENCE",
            profile.getProfileRestrictions().iterator().next().getSeverityLevel()
        );
    }

    @Test
    @DisplayName("UC1 updateSelfProfile throws when the caller has no linked SELF profile yet")
    void updateSelfProfileThrowsWhenNoneExists() {
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());

        assertThrows(
            SelfProfileNotFoundException.class,
            () -> dietaryProfileService.updateSelfProfile(
                14L,
                new CreateSelfProfileRequest("New Name", Map.of())
            )
        );

        verify(dietaryProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UC18 invalid restrictions leave both the profile and account untouched")
    void invalidRestrictionFailsBeforeProfilePersistenceAndLeavesAccountUntouched() {
        UserAccount account = new UserAccount();
        account.setId(14L);
        account.setActive(true);
        when(dietaryProfileRepository.findByLinkedUser_Id(14L)).thenReturn(Optional.empty());
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(account));
        when(dietaryRestrictionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.createSelfProfile(
                14L,
                new CreateSelfProfileRequest(
                    "Person Name",
                    Map.of(999L, "STRICT_AVOID")
                )
            )
        );

        assertTrue(account.isActive());
        verify(dietaryProfileRepository, never()).saveAndFlush(any());
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    @DisplayName("UC1 BE1: Saves a selected dietary restriction for a profile")
    void saveDietaryRestrictionSelectionsReplacesSelectionsInTheProfileCollection() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);

        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setId(2L);

        ProfileRestriction existingRestriction = new ProfileRestriction();
        existingRestriction.setId(new ProfileRestrictionId(1L, 3L));
        existingRestriction.setDietaryProfile(profile);
        existingRestriction.setDietaryRestriction(new DietaryRestriction());
        existingRestriction.getDietaryRestriction().setId(3L);
        existingRestriction.setSeverityLevel("LOW_RISK");
        Set<ProfileRestriction> initialRestrictions = new HashSet<>();
        initialRestrictions.add(existingRestriction);
        profile.setProfileRestrictions(initialRestrictions);

        when(dietaryProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(dietaryRestrictionRepository.findById(2L)).thenReturn(Optional.of(restriction));

        dietaryProfileService.saveDietaryRestrictionSelections(1L, Map.of(2L, "STRICT_AVOID"));

        assertEquals(1, profile.getProfileRestrictions().size());
        ProfileRestriction savedRestriction = profile.getProfileRestrictions().iterator().next();
        assertEquals(2L, savedRestriction.getDietaryRestriction().getId());
        assertEquals("STRICT_AVOID", savedRestriction.getSeverityLevel());
        verify(dietaryProfileRepository).save(profile);
    }

    @Test
    @DisplayName("UC1 BE2: Removes deselected dietary restrictions when saving")
    void saveDietaryRestrictionSelectionsRemovesDeselectedRestrictions() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);

        ProfileRestriction keptRestriction = new ProfileRestriction();
        keptRestriction.setId(new ProfileRestrictionId(1L, 2L));
        keptRestriction.setDietaryProfile(profile);
        keptRestriction.setDietaryRestriction(createRestriction(2L));
        keptRestriction.setSeverityLevel("LOW_RISK");

        ProfileRestriction removedRestriction = new ProfileRestriction();
        removedRestriction.setId(new ProfileRestrictionId(1L, 3L));
        removedRestriction.setDietaryProfile(profile);
        removedRestriction.setDietaryRestriction(createRestriction(3L));
        removedRestriction.setSeverityLevel("STRICT_AVOID");

        profile.setProfileRestrictions(new HashSet<>(Set.of(keptRestriction, removedRestriction)));

        when(dietaryProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(dietaryRestrictionRepository.findById(2L)).thenReturn(Optional.of(createRestriction(2L)));

        dietaryProfileService.saveDietaryRestrictionSelections(1L, Map.of(2L, "STRICT_AVOID"));

        assertEquals(1, profile.getProfileRestrictions().size());
        assertEquals(2L, profile.getProfileRestrictions().iterator().next().getDietaryRestriction().getId());
        verify(dietaryProfileRepository).save(profile);
    }

    @Test
    @DisplayName("UC1 BE3: Throws exception when the target profile does not exist")
    void saveDietaryRestrictionSelectionsThrowsWhenProfileDoesNotExist() {
        when(dietaryProfileRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.saveDietaryRestrictionSelections(99L, Map.of(2L, "STRICT_AVOID"))
        );

        assertEquals("Profile not found: 99", exception.getMessage());
        verify(dietaryProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("UC1 BE4: Throws exception when a requested restriction does not exist")
    void saveDietaryRestrictionSelectionsThrowsWhenRestrictionDoesNotExist() {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);
        profile.setProfileRestrictions(new HashSet<>());

        when(dietaryProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(dietaryRestrictionRepository.findById(42L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> dietaryProfileService.saveDietaryRestrictionSelections(1L, Map.of(42L, "STRICT_AVOID"))
        );

        assertEquals("Restriction not found: 42", exception.getMessage());
        verify(dietaryProfileRepository, never()).save(profile);
    }

    @Test
    @DisplayName("family profile list marks isPrimary only for PRIMARY_ADMIN linked profile")
    void getProfilesByFamilyIdIsPrimaryFromMembershipNotColumn() {
        Family family = new Family();
        family.setId(1L);

        UserAccount admin = new UserAccount();
        admin.setId(10L);
        DietaryProfile adminProfile = new DietaryProfile();
        adminProfile.setId(77L);
        adminProfile.setFamily(family);
        adminProfile.setLinkedUser(admin);
        adminProfile.setProfileName("Admin");
        adminProfile.setRelationship("SELF");
        adminProfile.setPrimary(true);
        adminProfile.setActive(true);

        UserAccount invitee = new UserAccount();
        invitee.setId(30L);
        DietaryProfile inviteeProfile = new DietaryProfile();
        inviteeProfile.setId(99L);
        inviteeProfile.setFamily(family);
        inviteeProfile.setLinkedUser(invitee);
        inviteeProfile.setProfileName("Invitee");
        inviteeProfile.setRelationship("SELF");
        inviteeProfile.setPrimary(true);
        inviteeProfile.setActive(true);

        when(familyMemberRepository.findActivePrimaryAdminUserIds(1L)).thenReturn(List.of(10L));
        when(dietaryProfileRepository.findProfilesByFamilyId(1L))
            .thenReturn(List.of(adminProfile, inviteeProfile));

        List<DietaryProfileSummaryDto> rows = dietaryProfileService.getProfilesByFamilyId(1L);

        assertEquals(2, rows.size());
        assertTrue(rows.get(0).isPrimary());
        assertFalse(rows.get(1).isPrimary());
    }

    private DietaryRestriction createRestriction(Long id) {
        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setId(id);
        return restriction;
    }
}
