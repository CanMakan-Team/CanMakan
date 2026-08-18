package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.dto.DietaryProfileSummaryDto;
import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.family.dto.ActiveProfileResponse;
import com.canmakan.backend.family.dto.ClaimInvitationRequest;
import com.canmakan.backend.family.dto.CreateDependantProfileRequest;
import com.canmakan.backend.family.dto.CreateFamilyRequest;
import com.canmakan.backend.family.dto.CreateInvitationRequest;
import com.canmakan.backend.family.dto.DependantProfileResponse;
import com.canmakan.backend.family.dto.FamilyMemberRosterDto;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.FamilyRestrictionSumRes;
import com.canmakan.backend.family.dto.FamilyScanHistoryDto;
import com.canmakan.backend.family.dto.InvitationPreviewResponse;
import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.dto.PendingInvitationResponse;
import com.canmakan.backend.family.dto.UpdateProfileRequest;
import com.canmakan.backend.family.dto.UserSearchResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.LastPrimaryAdminException;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.family.service.FamilyActiveProfileService;
import com.canmakan.backend.family.service.FamilyAuthorizationService;
import com.canmakan.backend.family.service.FamilyInvitationService;
import com.canmakan.backend.family.service.FamilyRosterService;
import com.canmakan.backend.family.service.FamilyScanHistoryService;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Family circle create/me, UC9 invite/dependant, UC10 accept/decline inbox, and UC6 summary.
 * Caller id is supplied by the controller from the JWT principal.
 *
 * @author Amelia
 * @author Khai
 */
@Service
@RequiredArgsConstructor
public class FamilyService {

    private static final String DEFAULT_SEVERITY = "STRICT_AVOID";

    private final UserAccountRepository userAccountRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyInvitationRepository familyInvitationRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final DietaryProfileService dietaryProfileService;
    private final FamilyAuthorizationService familyAuthorization;
    private final FamilyRosterService familyRosterService;
    private final FamilyInvitationService familyInvitationService;
    private final FamilyActiveProfileService familyActiveProfileService;
    private final FamilyScanHistoryService familyScanHistoryService;

    // Create a family circle
    @Transactional
    public FamilyMeResponse createFamily(long userId, CreateFamilyRequest request) {
        if (familyMemberRepository.existsByIdUserId(userId)) {
            throw new AlreadyInFamilyException("You already belong to a family circle.");
        }

        // Find the user by id
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));

        try {
            // Create a new family
            Family family = new Family();
            family.setFamilyName(request.familyName().trim());
            family.setCreatedByUserId(userId);
            Family savedFamily = familyRepository.saveAndFlush(family);

            // Create a new family member
            FamilyMember membership = new FamilyMember();
            membership.setId(new FamilyMember.FamilyMemberId(savedFamily.getId(), userId));
            membership.setMemberRole(FamilyMember.ROLE_PRIMARY_ADMIN);
            familyMemberRepository.saveAndFlush(membership);

            // Create a new dietary profile
            DietaryProfile selfProfile = dietaryProfileRepository.findByLinkedUser_Id(userId)
                .orElseGet(DietaryProfile::new);

            // Set the family and linked user
            selfProfile.setFamily(savedFamily);
            selfProfile.setLinkedUser(user);

            // Set the profile name
            if (selfProfile.getProfileName() == null || selfProfile.getProfileName().isBlank()) {
                selfProfile.setProfileName(profileNameFromUser(user));
            }

            // Set the relationship and primary
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
            if (isMembershipUniqueViolation(ex)) {
                throw new AlreadyInFamilyException("You already belong to a family circle.");
            }
            throw ex;
        }
    }

    // Get the family restriction summary
    public FamilyRestrictionSumRes getFamilyRestrictionSummary(Long currentUserId) {
        return familyRosterService.getFamilyRestrictionSummary(currentUserId);
    }

    /**
     * Lists linked members and dependant profiles for the caller's family.
     * Registered rows use {@code memberId = userId}; dependants use {@code memberId = profileId}.
     * Prefer {@code profileId} for UC12 manage APIs.
     */
    public List<FamilyMemberRosterDto> listFamilyMembers(long currentUserId) {
        return familyRosterService.listFamilyMembers(currentUserId);
    }

    /**
     * Lists all family profiles including inactive (UC12 manage).
     */
    @Transactional(readOnly = true)
    public List<DietaryProfileSummaryDto> listMyFamilyProfiles(long userId) {
        FamilyMember membership = familyAuthorization.requireMembership(userId);
        return dietaryProfileService.getAllProfilesByFamilyId(membership.getFamilyId());
    }

    /**
     * Updates profile name/relationship for a profile in the caller's family (PRIMARY_ADMIN).
     */
    @Transactional
    public FamilyMemberRosterDto updateProfileMetadata(
            long adminUserId, long profileId, UpdateProfileRequest request) {
        familyAuthorization.requirePrimaryAdmin(adminUserId);
        DietaryProfile profile = familyAuthorization.requireProfileInCallerFamily(adminUserId, profileId);
        profile.setProfileName(request.profileName().trim());
        profile.setRelationship(request.relationship().trim().toUpperCase(Locale.ROOT));
        dietaryProfileRepository.saveAndFlush(profile);

        // Restriction lists are optional; when present, D3 ownership applies
        // (self, or any family profile for PRIMARY_ADMIN).
        if (request.commonRequirements() != null || request.restrictions() != null) {
            familyAuthorization.assertMayEditRestrictions(adminUserId, profileId);
            Map<Long, String> selections = resolveRestrictionSelections(
                request.commonRequirements(), request.restrictions());
            dietaryProfileService.saveDietaryRestrictionSelections(profileId, selections);
            profile = dietaryProfileRepository.findById(profileId)
                .orElseThrow(() -> new FamilyNotFoundException("Profile was not found."));
        }
        return toRosterRow(profile);
    }

    /**
     * Toggles dietary_profiles.is_active (never users.is_active).
     */
    @Transactional
    public DietaryProfileSummaryDto setProfileActive(
            long adminUserId, long profileId, boolean active) {
        familyAuthorization.requirePrimaryAdmin(adminUserId);
        DietaryProfile profile = familyAuthorization.requireProfileInCallerFamily(adminUserId, profileId);
        if (!active) {
            assertProfileCanBeDeactivated(adminUserId, profile);
        }
        profile.setActive(active);
        dietaryProfileRepository.saveAndFlush(profile);
        if (!active) {
            familyActiveProfileService.clearPreferencePointingAt(profileId);
        }
        return toDietaryProfileSummary(profile);
    }

    /**
     * Guards against deactivating a profile that would leave the caller, or the family,
     * without an active primary admin.
     */
    private void assertProfileCanBeDeactivated(long adminUserId, DietaryProfile profile) {
        Long linkedUserId = profile.getLinkedUser() == null ? null : profile.getLinkedUser().getId();
        if (linkedUserId != null && linkedUserId == adminUserId) {
            throw new FamilyForbiddenException("Cannot deactivate your own family admin profile.");
        }
        if (!isFamilyAdminLinkedProfile(profile)) {
            return;
        }
        Long familyId = profile.getFamily() == null ? null : profile.getFamily().getId();
        if (familyId != null && familyMemberRepository.countActivePrimaryAdmins(familyId) <= 1) {
            throw new LastPrimaryAdminException("Cannot deactivate the family admin profile.");
        }
    }

    private DietaryProfileSummaryDto toDietaryProfileSummary(DietaryProfile profile) {
        return new DietaryProfileSummaryDto(
            profile.getId(),
            profile.getProfileName(),
            profile.getFamily() == null ? null : profile.getFamily().getId(),
            profile.getRelationship(),
            initialsOf(profile.getProfileName()),
            isFamilyAdminLinkedProfile(profile),
            profile.isActive()
        );
    }

    /**
     * Soft-removes a linked member: deactivates membership + profile (AC10–14).
     */
    @Transactional
    public void removeFamilyMember(long adminUserId, long targetUserId) {
        FamilyMember adminMembership = familyAuthorization.requirePrimaryAdmin(adminUserId);
        Long familyId = adminMembership.getFamilyId();

        FamilyMember target = familyMemberRepository.findMembershipByUserId(targetUserId)
            .orElseThrow(() -> new FamilyNotFoundException("Family member was not found."));
        if (!familyId.equals(target.getFamilyId()) || !Boolean.TRUE.equals(target.getIsActive())) {
            throw new FamilyNotFoundException("Family member was not found.");
        }

        if (FamilyMember.ROLE_PRIMARY_ADMIN.equals(target.getMemberRole())
                && familyMemberRepository.countActivePrimaryAdmins(familyId) <= 1) {
            throw new LastPrimaryAdminException(
                "Cannot remove the last primary admin without an allowed transfer.");
        }

        target.setIsActive(false);
        familyMemberRepository.saveAndFlush(target);

        dietaryProfileRepository.findByLinkedUser_Id(targetUserId).ifPresent(profile -> {
            profile.setActive(false);
            dietaryProfileRepository.saveAndFlush(profile);
            familyActiveProfileService.clearPreferencePointingAt(profile.getId());
        });
    }

    /**
     * Soft-removes a dependant profile: deactivate and detach from family (keeps scans).
     */
    @Transactional
    public void removeDependantProfile(long adminUserId, long profileId) {
        familyAuthorization.requirePrimaryAdmin(adminUserId);
        DietaryProfile profile = familyAuthorization.requireProfileInCallerFamily(adminUserId, profileId);
        if (profile.getLinkedUser() != null) {
            throw new FamilyForbiddenException(
                "Linked members must be removed via DELETE /members/{userId}.");
        }
        profile.setActive(false);
        profile.setFamily(null);
        dietaryProfileRepository.saveAndFlush(profile);
        familyActiveProfileService.clearPreferencePointingAt(profileId);
    }

    /**
     * D3: actor may edit restrictions for their own linked profile, or for any
     * profile in their family circle when they are PRIMARY_ADMIN.
     */
    @Transactional(readOnly = true)
    public void assertMayEditRestrictions(long actorUserId, long profileId) {
        familyAuthorization.assertMayEditRestrictions(actorUserId, profileId);
    }

    private FamilyMemberRosterDto toRosterRow(DietaryProfile profile) {
        RestrictionCodeSplit codes = splitRestrictionCodes(Optional.of(profile));
        if (profile.getLinkedUser() != null && profile.getLinkedUser().getId() != null) {
            Long linkedUserId = profile.getLinkedUser().getId();
            String role = familyMemberRepository.findMembershipByUserId(linkedUserId)
                .map(FamilyMember::getMemberRole)
                .orElse(FamilyMember.ROLE_MEMBER);
            String masked = userAccountRepository.findById(linkedUserId)
                .map(UserAccount::getEmail)
                .map(FamilyService::maskEmail)
                .orElse(null);
            return new FamilyMemberRosterDto(
                linkedUserId,
                profile.getId(),
                linkedUserId,
                profile.getProfileName(),
                profile.getRelationship(),
                codes.commonRequirements(),
                codes.restrictions(),
                FamilyMemberRosterDto.SOURCE_REGISTERED,
                masked,
                role,
                profile.isActive()
            );
        }
        return new FamilyMemberRosterDto(
            profile.getId(),
            profile.getId(),
            null,
            profile.getProfileName(),
            profile.getRelationship(),
            codes.commonRequirements(),
            codes.restrictions(),
            FamilyMemberRosterDto.SOURCE_DEPENDANT,
            null,
            null,
            profile.isActive()
        );
    }

    private static String initialsOf(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return "";
        }
        return profileName.substring(0, Math.min(2, profileName.length())).toUpperCase(Locale.ROOT);
    }

    // Get the family me response
    @Transactional(readOnly = true)
    public FamilyMeResponse getMyFamily(long userId) {
        // Require membership
        FamilyMember membership = familyAuthorization.requireMembership(userId);

        // Find the family by id
        Family family = familyRepository.findById(membership.getFamilyId())
            .orElseThrow(() -> new FamilyNotFoundException(FamilyAuthorizationService.NOT_IN_FAMILY_MESSAGE));

        // Find the self profile by user id
        Long selfProfileId = dietaryProfileRepository.findByLinkedUser_Id(userId)
            .map(DietaryProfile::getId)
            .orElse(null);

        // Return the family me response
        return new FamilyMeResponse(
            family.getId(),
            family.getFamilyName(),
            membership.getMemberRole(),
            selfProfileId,
            family.getCreatedByUserId()
        );
    }

    /**
     * Lists active dietary profiles for a family the caller belongs to.
     */
    @Transactional(readOnly = true)
    public List<DietaryProfileSummaryDto> getProfilesForFamilyMember(long userId, long familyId) {
        FamilyMember membership = familyAuthorization.requireMembership(userId);
        if (!membership.getFamilyId().equals(familyId)) {
            throw new FamilyForbiddenException(
                "Profile list is not available for this family.");
        }
        return dietaryProfileService.getProfilesByFamilyId(familyId);
    }

    /**
     * Ensures the caller may assess or switch to the given profile.
     */
    @Transactional(readOnly = true)
    public void assertProfileAuthorizedForScan(long userId, long profileId) {
        familyAuthorization.assertProfileAuthorizedForScan(userId, profileId);
    }

    /**
     * Lists recent scans for all profiles in the caller's family (web history/dashboard).
     * PRIMARY_ADMIN only (UC4 AC10).
     */
    public List<FamilyScanHistoryDto> listFamilyScans(long userId) {
        return familyScanHistoryService.listFamilyScans(userId);
    }

    /**
     * Returns the caller's active scan profile, using stored preference or documented default.
     */
    public ActiveProfileResponse getActiveProfile(long userId) {
        return familyActiveProfileService.getActiveProfile(userId);
    }

    /**
     * Persists the caller's active scan profile after family/inactive validation.
     */
    public ActiveProfileResponse setActiveProfile(long userId, long profileId) {
        return familyActiveProfileService.setActiveProfile(userId, profileId);
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

    // Search user by email
    @Transactional(readOnly = true)
    public UserSearchResponse searchUserByEmail(long adminUserId, String rawEmail) {
        // Require primary admin
        FamilyMember adminMembership = familyAuthorization.requirePrimaryAdmin(adminUserId);

        // Normalize the email
        String email = normalizeEmail(rawEmail);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        // Find the user by email
        Optional<UserAccount> accountOpt = userAccountRepository.findByEmail(email);

        // Check if the user has a pending invitation for the family
        boolean pendingForFamily = familyInvitationRepository
            .findPendingByFamilyAndEmail(adminMembership.getFamilyId(), email)
            .isPresent();

        // Return the user search response
        if (accountOpt.isEmpty()) {
            return new UserSearchResponse(
                null,
                null,
                maskEmail(email),
                UserSearchResponse.ACCOUNT_NOT_REGISTERED,
                pendingForFamily
                    ? UserSearchResponse.LINK_PENDING
                    : UserSearchResponse.LINK_NOT_LINKED
            );
        }

        // Find the user by id
        UserAccount account = accountOpt.get();

        // Find the display name
        String displayName = dietaryProfileRepository.findByLinkedUser_Id(account.getId())
            .map(DietaryProfile::getProfileName)
            .orElseGet(() -> profileNameFromUser(account));

        // Find the account status
        String accountStatus = account.isActive()
            ? UserSearchResponse.ACCOUNT_ACTIVE
            : UserSearchResponse.ACCOUNT_INACTIVE;

        // Find the link status
        // If the user is already a member of the family, set the link status to ALREADY_LINKED
        // If the user has a pending invitation for the family, set the link status to PENDING
        // Otherwise, set the link status to NOT_LINKED
        String linkStatus;
        if (familyMemberRepository.existsByIdUserId(account.getId())) {
            linkStatus = UserSearchResponse.LINK_ALREADY_LINKED;
        } else if (pendingForFamily) {
            linkStatus = UserSearchResponse.LINK_PENDING;
        } else {
            linkStatus = UserSearchResponse.LINK_NOT_LINKED;
        }

        // Return the user search response
        return new UserSearchResponse(
            account.getId(),
            displayName,
            maskEmail(email),
            accountStatus,
            linkStatus
        );
    }

    // Create an invitation
    public InvitationResponse createInvitation(long adminUserId, CreateInvitationRequest request) {
        return familyInvitationService.createInvitation(adminUserId, request);
    }

    // Claim an invitation (UC9 deep-link / login path — same rules as accept)
    public FamilyMeResponse claimInvitation(long userId, ClaimInvitationRequest request) {
        return familyInvitationService.claimInvitation(userId, request);
    }

    /**
     * Accept a PENDING invitation by token (UC10 inbox — no typed profile name available).
     */
    public FamilyMeResponse acceptInvitation(long userId, String invitationToken) {
        return familyInvitationService.acceptInvitation(userId, invitationToken);
    }

    /**
     * Accept a PENDING invitation by token (UC10 inbox + UC9 claim).
     *
     * @param profileName the profile name the caller typed at registration, if any;
     *                     used for the auto-provisioned SELF profile instead of a
     *                     placeholder derived from the email address.
     */
    public FamilyMeResponse acceptInvitation(long userId, String invitationToken, String profileName) {
        return familyInvitationService.acceptInvitation(userId, invitationToken, profileName);
    }

    /**
     * Decline a PENDING invitation. Expired PENDING invites may still be declined.
     */
    public void declineInvitation(long userId, String invitationToken) {
        familyInvitationService.declineInvitation(userId, invitationToken);
    }

    /**
     * List PENDING invitations for the authenticated user's email (UC10 inbox).
     */
    public List<PendingInvitationResponse> listMyPendingInvitations(long userId) {
        return familyInvitationService.listMyPendingInvitations(userId);
    }

    /**
     * Public lookup so invite registration can lock the email field.
     */
    public InvitationPreviewResponse previewInvitation(String invitationToken) {
        return familyInvitationService.previewInvitation(invitationToken);
    }

    // Create a dependant profile
    @Transactional
    public DependantProfileResponse createDependantProfile(
            long adminUserId, CreateDependantProfileRequest request) {
        // Require primary admin
        FamilyMember adminMembership = familyAuthorization.requirePrimaryAdmin(adminUserId);

        // Find the family by id
        Family family = familyRepository.findById(adminMembership.getFamilyId())
            .orElseThrow(() -> new FamilyNotFoundException(FamilyAuthorizationService.NOT_IN_FAMILY_MESSAGE));

        // Create a new dietary profile and save
        DietaryProfile profile = new DietaryProfile();
        profile.setFamily(family);
        profile.setLinkedUser(null);
        profile.setProfileName(request.profileName());
        profile.setRelationship(request.relationship());
        profile.setPrimary(false);
        DietaryProfile saved = dietaryProfileRepository.saveAndFlush(profile);

        // Resolve the restriction selections
        Map<Long, String> selections = resolveRestrictionSelections(
            request.commonRequirements(), request.restrictions());

        // Save the restriction selections
        if (!selections.isEmpty()) {
            dietaryProfileService.saveDietaryRestrictionSelections(saved.getId(), selections);
        }

        // Return the dependant profile response
        return new DependantProfileResponse(
            saved.getId(),
            saved.getProfileName(),
            saved.getRelationship(),
            family.getId()
        );
    }

    // Resolve the restriction selections
    private Map<Long, String> resolveRestrictionSelections(
            List<String> commonRequirements, List<String> restrictions) {
        Set<String> codes = new LinkedHashSet<>();
        if (commonRequirements != null) {
            commonRequirements.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.strip().toUpperCase(Locale.ROOT))
                .forEach(codes::add);
        }
        if (restrictions != null) {
            restrictions.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.strip().toUpperCase(Locale.ROOT))
                .forEach(codes::add);
        }

        Map<Long, String> selections = new HashMap<>();
        for (String code : codes) {
            DietaryRestriction restriction = dietaryRestrictionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unknown dietary restriction code: " + code));
            selections.put(restriction.getId(), DEFAULT_SEVERITY);
        }
        return selections;
    }

    // Normalize the email
    static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    // Mask the email by replacing the local part with asterisks
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    // Check if the membership is unique
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

    // Generate the profile name from the user
    static String profileNameFromUser(UserAccount user) {
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return "My Profile";
        }
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return local.isBlank() ? "My Profile" : local;
    }

    private static RestrictionCodeSplit splitRestrictionCodes(Optional<DietaryProfile> dietaryProfileOpt) {
        List<String> common = new ArrayList<>();
        List<String> individual = new ArrayList<>();
        if (dietaryProfileOpt.isEmpty()) {
            return new RestrictionCodeSplit(common, individual);
        }
        for (ProfileRestriction profileRestriction :
                dietaryProfileOpt.get().getProfileRestrictions()) {
            DietaryRestriction restriction = profileRestriction.getDietaryRestriction();
            if (restriction == null || restriction.getCode() == null) {
                continue;
            }
            String code = restriction.getCode();
            String category = restriction.getCategory() == null
                ? ""
                : restriction.getCategory().trim().toUpperCase(Locale.ROOT);
            if ("RELIGIOUS".equals(category)) {
                common.add(code);
            } else {
                individual.add(code);
            }
        }
        return new RestrictionCodeSplit(List.copyOf(common), List.copyOf(individual));
    }

    private record RestrictionCodeSplit(
        List<String> commonRequirements,
        List<String> restrictions
    ) {
    }
}
