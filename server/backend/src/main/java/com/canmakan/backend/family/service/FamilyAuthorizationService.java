package com.canmakan.backend.family.service;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InactiveProfileException;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared family membership and profile authorization checks (UC1 / UC2 / UC11 / UC12).
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class FamilyAuthorizationService {

    public static final String NOT_IN_FAMILY_MESSAGE =
        "You are not a member of a family circle.";
    public static final String PRIMARY_ADMIN_REQUIRED =
        "Only the family primary admin can perform this action.";
    public static final String PROFILE_NOT_FOUND_MESSAGE = "Profile was not found.";

    private final FamilyMemberRepository familyMemberRepository;
    private final DietaryProfileRepository dietaryProfileRepository;

    @Transactional(readOnly = true)
    public FamilyMember requireMembership(long userId) {
        return findMembership(userId);
    }

    @Transactional(readOnly = true)
    public FamilyMember requirePrimaryAdmin(long userId) {
        FamilyMember membership = findMembership(userId);
        if (!FamilyMember.ROLE_PRIMARY_ADMIN.equals(membership.getMemberRole())) {
            throw new FamilyForbiddenException(PRIMARY_ADMIN_REQUIRED);
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public DietaryProfile requireProfileInCallerFamily(long userId, long profileId) {
        FamilyMember membership = findMembership(userId);
        DietaryProfile profile = dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(profileId)
            .orElseThrow(() -> new FamilyNotFoundException(PROFILE_NOT_FOUND_MESSAGE));
        if (profile.getFamily() == null
                || !membership.getFamilyId().equals(profile.getFamily().getId())) {
            throw new FamilyForbiddenException("Profile does not belong to your family circle.");
        }
        return profile;
    }

    // Plain (non-@Transactional) so other methods in this class share it directly instead of
    // calling the annotated requireMembership(...) via 'this' — a self-invocation bypasses
    // Spring's proxy and silently skips the annotation.
    private FamilyMember findMembership(long userId) {
        return familyMemberRepository.findMembershipByUserId(userId)
            .orElseThrow(() -> new FamilyNotFoundException(NOT_IN_FAMILY_MESSAGE));
    }

    /**
     * D3: actor may edit restrictions for their own linked profile, or for any
     * profile in their family circle when they are PRIMARY_ADMIN.
     * Non-admins may only edit their own profile.
     */
    @Transactional(readOnly = true)
    public void assertMayEditRestrictions(long actorUserId, long profileId) {
        DietaryProfile profile = dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(profileId)
            .orElseThrow(() -> new FamilyNotFoundException(PROFILE_NOT_FOUND_MESSAGE));

        if (profile.getLinkedUser() != null
                && profile.getLinkedUser().getId() != null
                && profile.getLinkedUser().getId() == actorUserId) {
            return;
        }

        if (profile.getFamily() != null) {
            Optional<FamilyMember> membershipOpt =
                familyMemberRepository.findMembershipByUserId(actorUserId);
            if (membershipOpt.isPresent()) {
                FamilyMember membership = membershipOpt.get();
                if (FamilyMember.ROLE_PRIMARY_ADMIN.equals(membership.getMemberRole())
                        && membership.getFamilyId().equals(profile.getFamily().getId())) {
                    return;
                }
            }
        }

        throw new FamilyForbiddenException(
            "You can only edit dietary restrictions for your own profile.");
    }

    /**
     * Ensures the caller may assess or switch to the given profile.
     */
    @Transactional(readOnly = true)
    public void assertProfileAuthorizedForScan(long userId, long profileId) {
        selectProfile(userId, profileId);
    }

    @Transactional(readOnly = true)
    public DietaryProfile assertProfileSelectable(long userId, long profileId) {
        return selectProfile(userId, profileId);
    }

    // Plain (non-@Transactional) so assertProfileAuthorizedForScan shares it directly instead of
    // calling the annotated assertProfileSelectable(...) via 'this' — a self-invocation bypasses
    // Spring's proxy and silently skips the annotation.
    private DietaryProfile selectProfile(long userId, long profileId) {
        DietaryProfile profile = dietaryProfileRepository.findByIdWithFamilyAndLinkedUser(profileId)
            .orElseThrow(() -> new FamilyNotFoundException(PROFILE_NOT_FOUND_MESSAGE));
        if (!profile.isActive()) {
            throw new InactiveProfileException("Profile is inactive and cannot be selected.");
        }

        Optional<FamilyMember> membershipOpt =
            familyMemberRepository.findMembershipByUserId(userId);
        if (membershipOpt.isPresent()) {
            Long familyId = membershipOpt.get().getFamilyId();
            if (profile.getFamily() == null || !familyId.equals(profile.getFamily().getId())) {
                throw new FamilyForbiddenException(
                    "Profile does not belong to your family circle.");
            }
            return profile;
        }

        if (profile.getLinkedUser() == null
                || profile.getLinkedUser().getId() == null
                || profile.getLinkedUser().getId() != userId
                || profile.getFamily() != null) {
            throw new FamilyForbiddenException(
                "Profile does not belong to the authenticated user.");
        }
        return profile;
    }
}
