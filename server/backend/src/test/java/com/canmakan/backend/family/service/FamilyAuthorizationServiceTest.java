package com.canmakan.backend.family.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InactiveProfileException;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.user.model.UserAccount;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Characterization tests for family/profile authorization gates (F14 / F15 lock-in).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyAuthorizationService")
class FamilyAuthorizationServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private DietaryProfileRepository dietaryProfileRepository;

    @InjectMocks
    private FamilyAuthorizationService familyAuthorization;

    @Test
    @DisplayName("assertProfileSelectable allows family member for another member profile")
    void familyMemberMaySelectSiblingProfile() {
        DietaryProfile profile = profileInFamily(20L, 1L, linkedUser(11L), true);
        when(dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(20L)).thenReturn(Optional.of(profile));
        when(familyMemberRepository.findMembershipByUserId(10L))
            .thenReturn(Optional.of(member(1L, 10L, FamilyMember.ROLE_MEMBER)));

        DietaryProfile result = familyAuthorization.assertProfileSelectable(10L, 20L);
        assertEquals(20L, result.getId());
    }

    @Test
    @DisplayName("assertProfileSelectable rejects outsider")
    void outsiderCannotSelectFamilyProfile() {
        DietaryProfile profile = profileInFamily(20L, 1L, linkedUser(11L), true);
        when(dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(20L)).thenReturn(Optional.of(profile));
        when(familyMemberRepository.findMembershipByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(
            FamilyForbiddenException.class,
            () -> familyAuthorization.assertProfileSelectable(99L, 20L));
    }

    @Test
    @DisplayName("assertProfileSelectable rejects inactive profile")
    void inactiveProfileRejected() {
        DietaryProfile profile = profileInFamily(20L, 1L, linkedUser(11L), false);
        when(dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(20L)).thenReturn(Optional.of(profile));

        assertThrows(
            InactiveProfileException.class,
            () -> familyAuthorization.assertProfileSelectable(10L, 20L));
    }

    @Test
    @DisplayName("assertMayEditRestrictions allows self-linked profile")
    void selfMayEditOwnRestrictions() {
        DietaryProfile profile = profileInFamily(20L, 1L, linkedUser(10L), true);
        when(dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(20L)).thenReturn(Optional.of(profile));

        assertDoesNotThrow(() -> familyAuthorization.assertMayEditRestrictions(10L, 20L));
    }

    @Test
    @DisplayName("assertMayEditRestrictions allows primary admin for family profile")
    void primaryAdminMayEditFamilyRestrictions() {
        DietaryProfile profile = profileInFamily(20L, 1L, linkedUser(11L), true);
        when(dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(20L)).thenReturn(Optional.of(profile));
        when(familyMemberRepository.findMembershipByUserId(10L))
            .thenReturn(Optional.of(member(1L, 10L, FamilyMember.ROLE_PRIMARY_ADMIN)));

        assertDoesNotThrow(() -> familyAuthorization.assertMayEditRestrictions(10L, 20L));
    }

    @Test
    @DisplayName("assertMayEditRestrictions rejects non-admin editing another profile")
    void nonAdminCannotEditSiblingRestrictions() {
        DietaryProfile profile = profileInFamily(20L, 1L, linkedUser(11L), true);
        when(dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(20L)).thenReturn(Optional.of(profile));
        when(familyMemberRepository.findMembershipByUserId(10L))
            .thenReturn(Optional.of(member(1L, 10L, FamilyMember.ROLE_MEMBER)));

        assertThrows(
            FamilyForbiddenException.class,
            () -> familyAuthorization.assertMayEditRestrictions(10L, 20L));
    }

    @Test
    @DisplayName("requireMembership throws when user has no family")
    void requireMembershipNotFound() {
        when(familyMemberRepository.findMembershipByUserId(5L)).thenReturn(Optional.empty());
        assertThrows(FamilyNotFoundException.class, () -> familyAuthorization.requireMembership(5L));
    }

    private static FamilyMember member(long familyId, long userId, String role) {
        FamilyMember member = new FamilyMember();
        member.setId(new FamilyMember.FamilyMemberId(familyId, userId));
        member.setMemberRole(role);
        member.setIsActive(true);
        return member;
    }

    private static UserAccount linkedUser(long userId) {
        UserAccount user = new UserAccount();
        user.setId(userId);
        return user;
    }

    private static DietaryProfile profileInFamily(
            long profileId, long familyId, UserAccount linkedUser, boolean active) {
        Family family = new Family();
        family.setId(familyId);
        DietaryProfile profile = new DietaryProfile();
        profile.setId(profileId);
        profile.setFamily(family);
        profile.setLinkedUser(linkedUser);
        profile.setActive(active);
        profile.setProfileName("Test");
        return profile;
    }
}
