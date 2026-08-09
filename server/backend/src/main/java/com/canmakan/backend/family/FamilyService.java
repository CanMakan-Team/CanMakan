package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.DietaryProfile;
import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.dto.CreateFamilyRequest;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.FamilyMeRestrictionDetail;
import com.canmakan.backend.family.dto.FamilyMeRestrictionSum;
import com.canmakan.backend.family.dto.FamilyRestrictionSumRes;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC8 create-circle and family context for the caller.
 * Caller id is supplied by the controller from the JWT principal.
 * Request field validation is handled by {@code @Valid} on the controller.
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class FamilyService {

    private static final String NOT_IN_FAMILY_MESSAGE =
        "You are not a member of a family circle.";

    private final UserAccountRepository userAccountRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final DietaryProfileRepository dietaryProfileRepository;

    // UC8 create circle by user id and request body
    // Transactional to ensure atomicity of the operation
    @Transactional
    public FamilyMeResponse createFamily(long userId, CreateFamilyRequest request) {
        // Check if the user is already in a family
        if (familyMemberRepository.existsByIdUserId(userId)) {
            throw new AlreadyInFamilyException("You already belong to a family circle.");
        }

        // Find the user by ID
        // If the user is not found, throw an authenticated user not found exception
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));

        try {
            // Create new family with the request body and save it
            Family family = new Family();
            family.setFamilyName(request.familyName().trim());
            family.setCreatedByUserId(userId);
            Family savedFamily = familyRepository.saveAndFlush(family);

            // Create new family member with the saved family ID and user ID
            FamilyMember membership = new FamilyMember();
            membership.setId(new FamilyMember.FamilyMemberId(savedFamily.getId(), userId));
            membership.setMemberRole(FamilyMember.ROLE_PRIMARY_ADMIN);
            familyMemberRepository.saveAndFlush(membership);

            // Registration already creates a family-less profile for this user
            // (see AuthService.register); reuse it here rather than creating a
            // second row, since linked_user_id is unique per user.
            DietaryProfile selfProfile = dietaryProfileRepository.findByLinkedUser_Id(userId)
                .orElseGet(DietaryProfile::new);

            // If the profile name is null or blank, set it to the profile name from the user
            // Set the family, linked user, profile name, relationship, and primary
            selfProfile.setFamily(savedFamily);
            selfProfile.setLinkedUser(user);
            if (selfProfile.getProfileName() == null || selfProfile.getProfileName().isBlank()) {
                selfProfile.setProfileName(profileNameFromUser(user));
            }
            selfProfile.setRelationship("SELF");
            selfProfile.setPrimary(true);
            DietaryProfile savedProfile = dietaryProfileRepository.saveAndFlush(selfProfile);

            // Return the family me response
            return new FamilyMeResponse(
                savedFamily.getId(),
                savedFamily.getFamilyName(),
                FamilyMember.ROLE_PRIMARY_ADMIN,
                savedProfile.getId(),
                savedFamily.getCreatedByUserId()
            );
        } catch (DataIntegrityViolationException ex) {
            // If the exception is a membership unique violation, throw an already in family exception
            if (isMembershipUniqueViolation(ex)) {
                throw new AlreadyInFamilyException("You already belong to a family circle.");
            }
            throw ex;
        }
    }

    /**
     * UC6
     * Retrieves a summary of dietary restrictions for all active members in the authenticated user's family circle.
     */
    @Transactional(readOnly = true)
    public FamilyRestrictionSumRes getFamilyRestrictionSummary(Long currentUserId) {
        // 1. Validate Family's Circle Membership and get Family ID
        FamilyMember membership = requireMembership(currentUserId);

        // 2. Fetch Active Family Members
        List<FamilyMember> activeMembers = familyMemberRepository.findActiveMembersByFamilyId(membership.getFamilyId());

        // 3. For each active member, fetch their dietary restrictions and map to DTO
        List<FamilyMeRestrictionSum> familyMembersSummary = activeMembers.stream().map(member -> {

            // 3.1 Fetch Dietary Profile associated with the Family Member User ID
            Optional<DietaryProfile> dietaryProfileOpt = dietaryProfileRepository.findByLinkedUser_Id(member.getId().getUserId());

            // 3.2 Extract the Profile Name
            String name = dietaryProfileOpt.map(profile -> profile.getProfileName())
                .orElse("Unknown Member");

            // 3.3 Extract and Map Dietary Restrictions to DTO
            List<FamilyMeRestrictionDetail> restrictionDetails = dietaryProfileOpt.map(profile ->
                // Assuming the collection in DietaryProfile is named profileRestrictions
                profile.getProfileRestrictions().stream()
                    .map(restriction -> new FamilyMeRestrictionDetail(
                        restriction.getDietaryRestriction().getCode(),
                        restriction.getDietaryRestriction().getDisplayName(),
                        restriction.getSeverityLevel()
                    )).toList()
            ).orElse(List.of());

            return new FamilyMeRestrictionSum(
                member.getUserId(),
                name,
                member.getIsActive(),
                restrictionDetails
            );
        }).toList();

        return new FamilyRestrictionSumRes(familyMembersSummary);
    }

    @Transactional(readOnly = true)
    public FamilyMeResponse getMyFamily(long userId) {
        FamilyMember membership = requireMembership(userId);

        Family family = familyRepository.findById(membership.getFamilyId())
            .orElseThrow(() -> new FamilyNotFoundException(NOT_IN_FAMILY_MESSAGE));

        Long selfProfileId = dietaryProfileRepository.findByLinkedUser_Id(userId)
            .map(DietaryProfile::getId)
            .orElse(null);

        return new FamilyMeResponse(
            family.getId(),
            family.getFamilyName(),
            membership.getMemberRole(),
            selfProfileId,
            family.getCreatedByUserId()
        );
    }

    private FamilyMember requireMembership(long userId) {
        return familyMemberRepository.findMembershipByUserId(userId)
            .orElseThrow(() -> new FamilyNotFoundException(NOT_IN_FAMILY_MESSAGE));
    }

    /**
     * True when the failure is the D2 one-membership-per-user unique key
     * (MySQL 1062 or constraint name), not an unrelated integrity error.
     */
    static boolean isMembershipUniqueViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                && sqlException.getErrorCode() == 1062) {
                return true;
            }
            String detail = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (detail.contains("uq_family_members_user_id")
                || detail.contains("family_members.user_id")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String profileNameFromUser(UserAccount user) {
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return "My Profile";
        }
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return local.isBlank() ? "My Profile" : local;
    }
}
