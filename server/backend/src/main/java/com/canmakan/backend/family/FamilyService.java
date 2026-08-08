package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.DietaryProfile;
import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.model.CreateFamilyRequest;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyMeResponse;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC8 create-circle and family context for the caller.
 * Caller id is supplied by the controller (temporary {@code X-User-Id}; JWT later).
 * Request field validation is handled by {@code @Valid} on the controller.
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class FamilyService {

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

        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found."));

        try {
            Family family = new Family();
            family.setFamilyName(request.familyName().trim());
            family.setCreatedByUserId(userId);
            Family savedFamily = familyRepository.saveAndFlush(family);

            FamilyMember membership = new FamilyMember();
            membership.setId(new FamilyMember.FamilyMemberId(savedFamily.getId(), userId));
            membership.setMemberRole(FamilyMember.ROLE_PRIMARY_ADMIN);
            familyMemberRepository.saveAndFlush(membership);

            // Registration already creates a family-less profile for this user
            // (see RegistrationService); reuse it here rather than creating a
            // second row, since linked_user_id is unique per user.
            DietaryProfile selfProfile = dietaryProfileRepository.findByLinkedUser_Id(userId)
                    .orElseGet(DietaryProfile::new);
            selfProfile.setFamily(savedFamily);
            selfProfile.setLinkedUser(user);
            if (selfProfile.getProfileName() == null || selfProfile.getProfileName().isBlank()) {
                selfProfile.setProfileName(profileNameFromUser(user));
            }
            selfProfile.setRelationship("SELF");
            selfProfile.setPrimary(true);
            DietaryProfile savedProfile = dietaryProfileRepository.saveAndFlush(selfProfile);

            return new FamilyMeResponse(
                    savedFamily.getId(),
                    savedFamily.getFamilyName(),
                    FamilyMember.ROLE_PRIMARY_ADMIN,
                    savedProfile.getId(),
                    savedFamily.getCreatedByUserId()
            );
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyInFamilyException("You already belong to a family circle.");
        }
    }

    @Transactional(readOnly = true)
    public FamilyMeResponse getMyFamily(long userId) {
        FamilyMember membership = familyMemberRepository.findMembershipByUserId(userId)
                .orElseThrow(() -> new FamilyNotFoundException("You are not a member of a family circle."));

        Family family = familyRepository.findById(membership.getFamilyId())
                .orElseThrow(() -> new FamilyNotFoundException("You are not a member of a family circle."));

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
