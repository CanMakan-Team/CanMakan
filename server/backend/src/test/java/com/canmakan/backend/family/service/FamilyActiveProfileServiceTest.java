package com.canmakan.backend.family.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.dto.ActiveProfileResponse;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InactiveProfileException;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.model.UserPreference;
import com.canmakan.backend.user.repository.UserPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyActiveProfileService")
class FamilyActiveProfileServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private DietaryProfileRepository dietaryProfileRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private FamilyAuthorizationService familyAuthorization;
    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    private FamilyActiveProfileService service;

    @BeforeEach
    void setUp() {
        service = new FamilyActiveProfileService(
                dietaryProfileRepository,
                familyRepository,
                familyMemberRepository,
                familyAuthorization,
                userPreferenceRepository);
    }

    @Test
    @DisplayName("getActiveProfile returns the stored preference when it is still selectable")
    void getActiveProfileUsesStoredPreferenceWhenSelectable() {
        DietaryProfile profile = profile(10L, "Dad", null, true, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(preference(10L)));
        when(familyAuthorization.assertProfileSelectable(USER_ID, 10L)).thenReturn(profile);
        when(dietaryProfileRepository.findById(10L)).thenReturn(Optional.of(profile));

        ActiveProfileResponse response = service.getActiveProfile(USER_ID);

        assertEquals(10L, response.profileId());
    }

    @Test
    @DisplayName("getActiveProfile clears the stored preference and falls back to the default when it is no longer selectable")
    void getActiveProfileClearsStalePreferenceAndFallsBackToDefault() {
        UserPreference storedPreference = preference(10L);
        when(userPreferenceRepository.findById(USER_ID))
                .thenReturn(Optional.of(storedPreference))
                .thenReturn(Optional.of(storedPreference));
        when(familyAuthorization.assertProfileSelectable(USER_ID, 10L))
                .thenThrow(new FamilyForbiddenException("not yours"));
        DietaryProfile selfProfile = profile(20L, "Self", null, true, null);
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(selfProfile));
        when(familyMemberRepository.findMembershipByUserId(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findById(20L)).thenReturn(Optional.of(selfProfile));

        ActiveProfileResponse response = service.getActiveProfile(USER_ID);

        assertEquals(20L, response.profileId());
        verify(userPreferenceRepository).saveAndFlush(storedPreference);
    }

    @Test
    @DisplayName("getActiveProfile falls back to the default when no preference is stored")
    void getActiveProfileUsesDefaultWhenNoPreferenceStored() {
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        DietaryProfile selfProfile = profile(20L, "Self", null, true, null);
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(selfProfile));
        when(familyMemberRepository.findMembershipByUserId(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findById(20L)).thenReturn(Optional.of(selfProfile));

        ActiveProfileResponse response = service.getActiveProfile(USER_ID);

        assertEquals(20L, response.profileId());
    }

    @Test
    @DisplayName("getActiveProfile prefers the caller's own profile when it belongs to the caller's family and is active")
    void getActiveProfileUsesOwnActiveProfileWithinFamily() {
        Family family = family(100L);
        DietaryProfile selfProfile = profile(20L, "Self", family, true, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(selfProfile));
        when(familyMemberRepository.findMembershipByUserId(USER_ID))
                .thenReturn(Optional.of(membership(100L, USER_ID)));
        when(familyRepository.findById(100L)).thenReturn(Optional.of(family));
        when(dietaryProfileRepository.findById(20L)).thenReturn(Optional.of(selfProfile));

        ActiveProfileResponse response = service.getActiveProfile(USER_ID);

        assertEquals(20L, response.profileId());
    }

    @Test
    @DisplayName("getActiveProfile falls back to the family's first active profile when the caller has none of their own in-family")
    void getActiveProfileFallsBackToFirstFamilyProfile() {
        Family family = family(100L);
        DietaryProfile selfProfile = profile(20L, "Self", null, true, null);
        DietaryProfile familyProfile = profile(30L, "Kid", family, true, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(selfProfile));
        when(familyMemberRepository.findMembershipByUserId(USER_ID))
                .thenReturn(Optional.of(membership(100L, USER_ID)));
        when(familyRepository.findById(100L)).thenReturn(Optional.of(family));
        when(dietaryProfileRepository.findProfilesByFamilyId(100L)).thenReturn(List.of(familyProfile));
        when(dietaryProfileRepository.findById(30L)).thenReturn(Optional.of(familyProfile));

        ActiveProfileResponse response = service.getActiveProfile(USER_ID);

        assertEquals(30L, response.profileId());
    }

    @Test
    @DisplayName("getActiveProfile throws when the caller's family has no active profiles")
    void getActiveProfileThrowsWhenFamilyHasNoActiveProfiles() {
        Family family = family(100L);
        DietaryProfile selfProfile = profile(20L, "Self", null, true, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(selfProfile));
        when(familyMemberRepository.findMembershipByUserId(USER_ID))
                .thenReturn(Optional.of(membership(100L, USER_ID)));
        when(familyRepository.findById(100L)).thenReturn(Optional.of(family));
        when(dietaryProfileRepository.findProfilesByFamilyId(100L)).thenReturn(List.of());

        assertThrows(FamilyNotFoundException.class, () -> service.getActiveProfile(USER_ID));
    }

    @Test
    @DisplayName("getActiveProfile throws when the caller is not in a family but their own profile belongs to one")
    void getActiveProfileThrowsForbiddenWhenNotInFamilyButProfileBelongsToOne() {
        Family family = family(100L);
        DietaryProfile selfProfile = profile(20L, "Self", family, true, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(selfProfile));
        when(familyMemberRepository.findMembershipByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(FamilyForbiddenException.class, () -> service.getActiveProfile(USER_ID));
    }

    @Test
    @DisplayName("getActiveProfile throws when the caller is not in a family and their own profile is inactive")
    void getActiveProfileThrowsInactiveWhenNotInFamilyAndProfileInactive() {
        DietaryProfile selfProfile = profile(20L, "Self", null, false, null);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.of(selfProfile));
        when(familyMemberRepository.findMembershipByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(InactiveProfileException.class, () -> service.getActiveProfile(USER_ID));
    }

    @Test
    @DisplayName("getActiveProfile throws when the caller has no dietary profile at all")
    void getActiveProfileThrowsWhenSelfProfileMissing() {
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(dietaryProfileRepository.findByLinkedUser_Id(USER_ID)).thenReturn(Optional.empty());

        assertThrows(FamilyNotFoundException.class, () -> service.getActiveProfile(USER_ID));
    }

    @Test
    @DisplayName("getActiveProfile throws when the resolved profile id no longer exists")
    void getActiveProfileThrowsWhenResolvedProfileMissing() {
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(preference(10L)));
        when(familyAuthorization.assertProfileSelectable(USER_ID, 10L)).thenReturn(profile(10L, "Dad", null, true, null));
        when(dietaryProfileRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(FamilyNotFoundException.class, () -> service.getActiveProfile(USER_ID));
    }

    @Test
    @DisplayName("setActiveProfile updates an existing stored preference")
    void setActiveProfileUpdatesExistingPreference() {
        DietaryProfile profile = profile(10L, "Dad", null, true, null);
        UserPreference existing = preference(null);
        when(familyAuthorization.assertProfileSelectable(USER_ID, 10L)).thenReturn(profile);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        ActiveProfileResponse response = service.setActiveProfile(USER_ID, 10L);

        assertEquals(10L, response.profileId());
        assertEquals(10L, existing.getActiveProfileId());
        verify(userPreferenceRepository).saveAndFlush(existing);
    }

    @Test
    @DisplayName("setActiveProfile creates a preference row when the caller has none yet")
    void setActiveProfileCreatesPreferenceWhenMissing() {
        DietaryProfile profile = profile(10L, "Dad", null, true, null);
        when(familyAuthorization.assertProfileSelectable(USER_ID, 10L)).thenReturn(profile);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());

        ActiveProfileResponse response = service.setActiveProfile(USER_ID, 10L);

        assertEquals(10L, response.profileId());
        verify(userPreferenceRepository, times(1)).saveAndFlush(any(UserPreference.class));
    }

    @Test
    @DisplayName("clearPreferencePointingAt clears every preference row that references the profile")
    void clearPreferencePointingAtClearsMatchingPreferences() {
        UserPreference first = preference(5L);
        UserPreference second = preference(5L);
        when(userPreferenceRepository.findByActiveProfileId(5L)).thenReturn(List.of(first, second));

        service.clearPreferencePointingAt(5L);

        assertEquals(null, first.getActiveProfileId());
        assertEquals(null, second.getActiveProfileId());
        verify(userPreferenceRepository, times(2)).saveAndFlush(any(UserPreference.class));
    }

    @Test
    @DisplayName("getActiveProfile marks the response as a primary-admin-linked profile when applicable")
    void getActiveProfileMarksPrimaryAdminLinkedProfile() {
        UserAccount linkedUser = new UserAccount();
        linkedUser.setId(USER_ID);
        DietaryProfile profile = profile(10L, "Dad", null, true, linkedUser);
        when(userPreferenceRepository.findById(USER_ID)).thenReturn(Optional.of(preference(10L)));
        when(familyAuthorization.assertProfileSelectable(USER_ID, 10L)).thenReturn(profile);
        when(dietaryProfileRepository.findById(10L)).thenReturn(Optional.of(profile));
        FamilyMember adminMembership = membership(100L, USER_ID);
        adminMembership.setMemberRole(FamilyMember.ROLE_PRIMARY_ADMIN);
        adminMembership.setIsActive(true);
        when(familyMemberRepository.findMembershipByUserId(USER_ID)).thenReturn(Optional.of(adminMembership));

        ActiveProfileResponse response = service.getActiveProfile(USER_ID);

        assertEquals(Boolean.TRUE, response.isPrimary());
    }

    private static DietaryProfile profile(
            Long id, String name, Family family, boolean active, UserAccount linkedUser) {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(id);
        profile.setProfileName(name);
        profile.setFamily(family);
        profile.setActive(active);
        profile.setLinkedUser(linkedUser);
        return profile;
    }

    private static Family family(Long id) {
        Family family = new Family();
        family.setId(id);
        return family;
    }

    private static FamilyMember membership(Long familyId, Long userId) {
        FamilyMember member = new FamilyMember();
        member.setId(new FamilyMember.FamilyMemberId(familyId, userId));
        member.setMemberRole(FamilyMember.ROLE_MEMBER);
        member.setIsActive(true);
        return member;
    }

    private static UserPreference preference(Long activeProfileId) {
        UserPreference preference = new UserPreference();
        preference.setUserId(USER_ID);
        preference.setActiveProfileId(activeProfileId);
        return preference;
    }
}
