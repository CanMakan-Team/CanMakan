package com.canmakan.backend.family.service;

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
import com.canmakan.backend.user.model.UserPreference;
import com.canmakan.backend.user.repository.UserPreferenceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves and persists each user's active scan profile, and clears stale
 * {@code user_preferences.active_profile_id} pointers when a profile is deactivated or removed.
 * Split out of {@link com.canmakan.backend.family.FamilyService} to keep that class's dependency
 * count within Sonar's class-coupling limit.
 */
@Service
@RequiredArgsConstructor
public class FamilyActiveProfileService {

    private final DietaryProfileRepository dietaryProfileRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyAuthorizationService familyAuthorization;
    private final UserPreferenceRepository userPreferenceRepository;

    /**
     * Returns the caller's active scan profile, using stored preference or documented default.
     */
    @Transactional
    public ActiveProfileResponse getActiveProfile(long userId) {
        Long effectiveProfileId = resolveEffectiveActiveProfileId(userId);
        DietaryProfile profile = dietaryProfileRepository.findById(effectiveProfileId)
            .orElseThrow(() -> new FamilyNotFoundException("Active profile was not found."));
        return toActiveProfileResponse(profile);
    }

    /**
     * Persists the caller's active scan profile after family/inactive validation.
     */
    @Transactional
    public ActiveProfileResponse setActiveProfile(long userId, long profileId) {
        DietaryProfile profile = familyAuthorization.assertProfileSelectable(userId, profileId);
        UserPreference preference = userPreferenceRepository.findById(userId)
            .orElseGet(() -> {
                UserPreference created = new UserPreference();
                created.setUserId(userId);
                return created;
            });
        preference.setActiveProfileId(profile.getId());
        userPreferenceRepository.saveAndFlush(preference);
        return toActiveProfileResponse(profile);
    }

    /**
     * Clears any stored preference pointing at a profile that was just deactivated or removed.
     */
    @Transactional
    public void clearPreferencePointingAt(long profileId) {
        for (UserPreference pref : userPreferenceRepository.findByActiveProfileId(profileId)) {
            pref.setActiveProfileId(null);
            userPreferenceRepository.saveAndFlush(pref);
        }
    }

    // Resolve the effective active profile id
    private Long resolveEffectiveActiveProfileId(long userId) {
        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findById(userId);
        if (preferenceOpt.isPresent() && preferenceOpt.get().getActiveProfileId() != null) {
            Long storedId = preferenceOpt.get().getActiveProfileId();
            try {
                familyAuthorization.assertProfileSelectable(userId, storedId);
                return storedId;
            } catch (FamilyForbiddenException | InactiveProfileException ex) {
                clearStoredActiveProfile(userId);
            }
        }
        return resolveDefaultActiveProfileId(userId);
    }

    // Resolve the default active profile id
    private Long resolveDefaultActiveProfileId(long userId) {
        Optional<FamilyMember> membershipOpt =
            familyMemberRepository.findMembershipByUserId(userId);
        DietaryProfile selfProfile = dietaryProfileRepository.findByLinkedUser_Id(userId)
            .orElseThrow(() -> new FamilyNotFoundException(
                "No dietary profile exists for the authenticated user."));
        if (membershipOpt.isPresent()) {
            Family family = familyRepository.findById(membershipOpt.get().getFamilyId())
                .orElseThrow(() -> new FamilyNotFoundException(FamilyAuthorizationService.NOT_IN_FAMILY_MESSAGE));
            if (selfProfile.getFamily() != null
                    && family.getId().equals(selfProfile.getFamily().getId())
                    && selfProfile.isActive()) {
                return selfProfile.getId();
            }
            List<DietaryProfile> activeProfiles =
                dietaryProfileRepository.findProfilesByFamilyId(family.getId());
            if (activeProfiles.isEmpty()) {
                throw new FamilyNotFoundException("No active profiles exist for this family.");
            }
            return activeProfiles.get(0).getId();
        }
        if (selfProfile.getFamily() != null) {
            throw new FamilyForbiddenException(
                "Profile is not available outside your family context.");
        }
        if (!selfProfile.isActive()) {
            throw new InactiveProfileException("Profile is inactive and cannot be selected.");
        }
        return selfProfile.getId();
    }

    // Clear the stored active profile
    private void clearStoredActiveProfile(long userId) {
        userPreferenceRepository.findById(userId).ifPresent(preference -> {
            preference.setActiveProfileId(null);
            userPreferenceRepository.saveAndFlush(preference);
        });
    }

    // Convert the active profile response
    private ActiveProfileResponse toActiveProfileResponse(DietaryProfile profile) {
        Long familyId = profile.getFamily() == null ? null : profile.getFamily().getId();
        return new ActiveProfileResponse(
            profile.getId(),
            profile.getProfileName(),
            profile.getRelationship(),
            familyId,
            isFamilyAdminLinkedProfile(profile)
        );
    }

    private boolean isFamilyAdminLinkedProfile(DietaryProfile profile) {
        if (profile.getLinkedUser() == null || profile.getLinkedUser().getId() == null) {
            return false;
        }
        return familyMemberRepository.findMembershipByUserId(profile.getLinkedUser().getId())
            .filter(membership -> FamilyMember.ROLE_PRIMARY_ADMIN.equals(membership.getMemberRole()))
            .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()))
            .isPresent();
    }
}
