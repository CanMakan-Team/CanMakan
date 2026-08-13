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
import com.canmakan.backend.family.dto.FamilyMeRestrictionDetail;
import com.canmakan.backend.family.dto.FamilyMeRestrictionSum;
import com.canmakan.backend.family.dto.FamilyRestrictionSumRes;
import com.canmakan.backend.family.dto.FamilyScanHistoryDto;
import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.dto.PendingInvitationResponse;
import com.canmakan.backend.family.dto.UpdateProfileRequest;
import com.canmakan.backend.family.dto.UserSearchResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InactiveProfileException;
import com.canmakan.backend.family.exception.InvitationConflictException;
import com.canmakan.backend.family.exception.InvitationExpiredException;
import com.canmakan.backend.family.exception.InvitationNotFoundException;
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
import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_TOKEN_BYTES = 24;
    private static final DateTimeFormatter SCAN_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final UserAccountRepository userAccountRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyInvitationRepository familyInvitationRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final DietaryProfileService dietaryProfileService;
    private final FamilyAuthorizationService familyAuthorization;
    private final ScanRepository scanRepository;
    private final InviteProperties inviteProperties;
    private final InvitationEmailService invitationEmailService;
    private final SecureRandom secureRandom = new SecureRandom();

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
    @Transactional(readOnly = true)
    public FamilyRestrictionSumRes getFamilyRestrictionSummary(Long currentUserId) {
        FamilyMember membership = familyAuthorization.requireMembership(currentUserId);
        Long familyId = membership.getFamilyId();

        List<FamilyMeRestrictionSum> rows = new ArrayList<>();

        for (FamilyMember member : familyMemberRepository.findActiveMembersByFamilyId(familyId)) {
            Optional<DietaryProfile> dietaryProfileOpt =
                dietaryProfileRepository.findByLinkedUser_Id(member.getUserId());
            String name = dietaryProfileOpt
                .map(profile -> profile.getProfileName())
                .orElse("Unknown Member");
            Long profileId = dietaryProfileOpt
                .map(profile -> profile.getId())
                .orElse(null);
            List<FamilyMeRestrictionDetail> restrictionDetails = mapRestrictions(dietaryProfileOpt);
            rows.add(new FamilyMeRestrictionSum(
                member.getUserId(),
                profileId,
                name,
                member.getIsActive(),
                restrictionDetails
            ));
        }

        for (DietaryProfile dependant :
                dietaryProfileRepository.findDependantProfilesByFamilyId(familyId)) {
            rows.add(new FamilyMeRestrictionSum(
                0L,
                dependant.getId(),
                dependant.getProfileName(),
                true,
                mapRestrictions(Optional.of(dependant))
            ));
        }

        return new FamilyRestrictionSumRes(rows);
    }

    /**
     * Lists linked members and dependant profiles for the caller's family.
     * Registered rows use {@code memberId = userId}; dependants use {@code memberId = profileId}.
     * Prefer {@code profileId} for UC12 manage APIs.
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberRosterDto> listFamilyMembers(long currentUserId) {
        FamilyMember membership = familyAuthorization.requireMembership(currentUserId);
        Long familyId = membership.getFamilyId();

        List<FamilyMemberRosterDto> rows = new ArrayList<>();

        // List active members
        // Find the dietary profile for the member
        // If the dietary profile is not found, continue
        // Get the name, relationship, and restriction codes
        // Mask the email
        // Add the member to the rows
        for (FamilyMember member : familyMemberRepository.findActiveMembersByFamilyId(familyId)) {
            Optional<DietaryProfile> dietaryProfileOpt =
                dietaryProfileRepository.findByLinkedUser_Id(member.getUserId());
            if (dietaryProfileOpt.isEmpty()) {
                continue;
            }
            DietaryProfile dietaryProfile = dietaryProfileOpt.get();
            String name = dietaryProfile.getProfileName() == null
                || dietaryProfile.getProfileName().isBlank()
                ? "Unknown Member"
                : dietaryProfile.getProfileName();
            String relationship = dietaryProfile.getRelationship() == null
                || dietaryProfile.getRelationship().isBlank()
                ? "OTHER"
                : dietaryProfile.getRelationship();
            RestrictionCodeSplit codes = splitRestrictionCodes(dietaryProfileOpt);
            String masked = userAccountRepository.findById(member.getUserId())
                .map(account -> account.getEmail())
                .map(email -> maskEmail(email))
                .orElse(null);
            rows.add(new FamilyMemberRosterDto(
                member.getUserId(),
                dietaryProfile.getId(),
                member.getUserId(),
                name,
                relationship,
                FamilyMemberRosterDto.AGE_GROUP_UNSPECIFIED,
                codes.commonRequirements(),
                codes.restrictions(),
                FamilyMemberRosterDto.SOURCE_REGISTERED,
                masked,
                member.getMemberRole(),
                dietaryProfile.isActive()
            ));
        }

        for (DietaryProfile dependant :
                dietaryProfileRepository.findAllDependantProfilesByFamilyId(familyId)) {
            String relationship = dependant.getRelationship() == null
                || dependant.getRelationship().isBlank()
                ? "DEPENDANT"
                : dependant.getRelationship();
            RestrictionCodeSplit codes = splitRestrictionCodes(Optional.of(dependant));
            rows.add(new FamilyMemberRosterDto(
                dependant.getId(),
                dependant.getId(),
                null,
                dependant.getProfileName(),
                relationship,
                FamilyMemberRosterDto.AGE_GROUP_UNSPECIFIED,
                codes.commonRequirements(),
                codes.restrictions(),
                FamilyMemberRosterDto.SOURCE_DEPENDANT,
                null,
                null,
                dependant.isActive()
            ));
        }

        return rows;
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
            assertMayEditRestrictions(adminUserId, profileId);
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
        profile.setActive(active);
        dietaryProfileRepository.saveAndFlush(profile);
        if (!active) {
            clearActiveProfilePreferencePointingAt(profileId);
        }
        return new DietaryProfileSummaryDto(
            profile.getId(),
            profile.getProfileName(),
            profile.getFamily() == null ? null : profile.getFamily().getId(),
            profile.getRelationship(),
            initialsOf(profile.getProfileName()),
            profile.isPrimary(),
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
            clearActiveProfilePreferencePointingAt(profile.getId());
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
        clearActiveProfilePreferencePointingAt(profileId);
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
                .map(member -> member.getMemberRole())
                .orElse(FamilyMember.ROLE_MEMBER);
            String masked = userAccountRepository.findById(linkedUserId)
                .map(account -> account.getEmail())
                .map(email -> maskEmail(email))
                .orElse(null);
            return new FamilyMemberRosterDto(
                linkedUserId,
                profile.getId(),
                linkedUserId,
                profile.getProfileName(),
                profile.getRelationship(),
                FamilyMemberRosterDto.AGE_GROUP_UNSPECIFIED,
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
            FamilyMemberRosterDto.AGE_GROUP_UNSPECIFIED,
            codes.commonRequirements(),
            codes.restrictions(),
            FamilyMemberRosterDto.SOURCE_DEPENDANT,
            null,
            null,
            profile.isActive()
        );
    }

    private void clearActiveProfilePreferencePointingAt(long profileId) {
        for (UserPreference pref :
                userPreferenceRepository.findByActiveProfileId(profileId)) {
            pref.setActiveProfileId(null);
            userPreferenceRepository.saveAndFlush(pref);
        }
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
            .map(profile -> profile.getId())
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
    @Transactional(readOnly = true)
    public List<FamilyScanHistoryDto> listFamilyScans(long userId) {
        FamilyMember membership = familyAuthorization.requirePrimaryAdmin(userId);
        Long familyId = membership.getFamilyId();
        List<DietaryProfile> profiles =
            dietaryProfileRepository.findAllProfilesByFamilyId(familyId);
        if (profiles.isEmpty()) {
            return List.of();
        }
        Map<Long, DietaryProfile> profilesById = new HashMap<>();
        for (DietaryProfile profile : profiles) {
            profilesById.put(profile.getId(), profile);
        }
        List<Scan> scans = scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(
            profilesById.keySet());
        List<FamilyScanHistoryDto> rows = new ArrayList<>();
        for (Scan scan : scans) {
            DietaryProfile profile = profilesById.get(scan.getProfileId());
            rows.add(toFamilyScanHistoryRow(scan, profile));
        }
        return rows;
    }

    private FamilyScanHistoryDto toFamilyScanHistoryRow(Scan scan, DietaryProfile profile) {
        ScanProduct product = scan.getProduct();
        String productName = product != null && product.getProductName() != null
            ? product.getProductName()
            : (scan.getBarcode() != null ? scan.getBarcode() : "Unknown product");
        String brand = product != null && product.getBrand() != null ? product.getBrand() : "";
        String profileName = profile != null && profile.getProfileName() != null
            ? profile.getProfileName()
            : "Unknown profile";
        long memberId = profile != null && profile.getLinkedUser() != null
            && profile.getLinkedUser().getId() != null
            ? profile.getLinkedUser().getId()
            : (profile != null && profile.getId() != null ? profile.getId() : 0L);
        String explanation = scan.getAiExplanation() == null ? "" : scan.getAiExplanation();
        return new FamilyScanHistoryDto(
            scan.getId() == null ? 0L : scan.getId(),
            productName,
            brand,
            memberId,
            profileName,
            mapWebVerdict(scan.getVerdict()),
            "",
            "",
            "",
            explanation,
            product == null ? "PRODUCT_NOT_FOUND" : "COMPLETE",
            "Open Food Facts / assessment",
            formatScanAt(scan.getScannedAt()),
            null
        );
    }

    /**
     * Scan verdicts on the wire are {@code SAFE} | {@code WARNING} | {@code UNSAFE} only.
     */
    private static String mapWebVerdict(String verdict) {
        if (verdict == null || verdict.isBlank()) {
            return "WARNING";
        }
        String normalized = verdict.trim().toUpperCase(Locale.ROOT);
        if ("SAFE".equals(normalized)
                || "WARNING".equals(normalized)
                || "UNSAFE".equals(normalized)) {
            return normalized;
        }
        // Legacy web label from older mocks / clients.
        if ("AVOID".equals(normalized)) {
            return "UNSAFE";
        }
        return "WARNING";
    }

    private static String formatScanAt(LocalDateTime scannedAt) {
        if (scannedAt == null) {
            return Instant.now().atOffset(ZoneOffset.UTC).format(SCAN_AT_FORMAT);
        }
        return scannedAt.format(SCAN_AT_FORMAT);
    }

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
            profile.isPrimary()
        );
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
            .map(profile -> profile.getProfileName())
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
    @Transactional
    public InvitationResponse createInvitation(long adminUserId, CreateInvitationRequest request) {

        // Require primary admin
        FamilyMember adminMembership = familyAuthorization.requirePrimaryAdmin(adminUserId);

        // Get the email
        String email = request.email();

        // Find the user by email
        Optional<UserAccount> invitee = userAccountRepository.findByEmail(email);

        // Check if the user is already a member of the family
        if (invitee.isPresent() && familyMemberRepository.existsByIdUserId(invitee.get().getId())) {
            throw new InvitationConflictException(
                "That user already belongs to a family circle.");
        }

        // A PENDING row is kept only after Resend accepted the send, so a repeat
        // Invite for the same email would spam the mailbox. Failed sends delete
        // the row and can be retried.
        Optional<FamilyInvitation> existingPending = familyInvitationRepository
            .findPendingByFamilyAndEmail(adminMembership.getFamilyId(), email);
        if (existingPending.isPresent()) {
            throw new InvitationConflictException(
                "An invitation email was already sent to this address.");
        }

        // Create a new invitation
        Instant expiresAt = Instant.now()
            .plus(inviteProperties.getExpiryDays(), ChronoUnit.DAYS);
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setFamilyId(adminMembership.getFamilyId());
        invitation.setInvitedByUserId(adminUserId);
        invitation.setInvitedEmail(email);
        invitation.setInvitationToken(generateUniqueInvitationToken());
        invitation.setInviteCode(generateUniqueInviteCode());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(expiresAt);

        // Save the invitation
        FamilyInvitation saved = familyInvitationRepository.saveAndFlush(invitation);

        // Deliver the invitation email
        InvitationResponse response = deliverInvitationEmail(saved, invitee.isPresent());
        if (!response.emailSent()) {
            familyInvitationRepository.delete(saved);
            familyInvitationRepository.flush();
        }

        // Return the invitation response
        return response;
    }

    // Deliver an invitation email
    private InvitationResponse deliverInvitationEmail(
            FamilyInvitation invitation, boolean inviteeRegistered) {
        InvitationResponse pendingResponse = toInvitationResponse(invitation, inviteeRegistered, false);
        Family family = familyRepository.findById(invitation.getFamilyId()).orElse(null);
        String familyName = family == null ? "a family circle" : family.getFamilyName();
        boolean emailSent = invitationEmailService.sendInvitationEmail(familyName, pendingResponse);
        return toInvitationResponse(invitation, inviteeRegistered, emailSent);
    }

    // Claim an invitation (UC9 deep-link / login path — same rules as accept)
    @Transactional
    public FamilyMeResponse claimInvitation(long userId, ClaimInvitationRequest request) {
        return acceptInvitation(userId, request.invitationToken());
    }

    /**
     * Accept a PENDING invitation by token (UC10 inbox + UC9 claim).
     */
    @Transactional
    public FamilyMeResponse acceptInvitation(long userId, String invitationToken) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));
        if (invitationToken == null || invitationToken.isBlank()) {
            throw new IllegalArgumentException("Invitation token is required.");
        }
        FamilyInvitation invitation = resolveClaimableInvitation(
            normalizeEmail(user.getEmail()), invitationToken.strip());
        return applyInvitationClaim(user, invitation);
    }

    /**
     * Decline a PENDING invitation. Expired PENDING invites may still be declined.
     */
    @Transactional
    public void declineInvitation(long userId, String invitationToken) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));
        if (invitationToken == null || invitationToken.isBlank()) {
            throw new IllegalArgumentException("Invitation token is required.");
        }
        FamilyInvitation invitation = familyInvitationRepository
            .findByInvitationToken(invitationToken.strip())
            .orElseThrow(() -> new InvitationNotFoundException("Invitation was not found."));

        ensureEmailMatches(invitation, normalizeEmail(user.getEmail()));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationConflictException("Invitation is no longer pending.");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        familyInvitationRepository.saveAndFlush(invitation);
    }

    /**
     * List PENDING invitations for the authenticated user's email (UC10 inbox).
     */
    @Transactional(readOnly = true)
    public List<PendingInvitationResponse> listMyPendingInvitations(long userId) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));
        String email = normalizeEmail(user.getEmail());
        List<FamilyInvitation> pending = familyInvitationRepository.findPendingByEmail(email);

        List<PendingInvitationResponse> results = new ArrayList<>();
        for (FamilyInvitation invitation : pending) {
            Family family = familyRepository.findById(invitation.getFamilyId()).orElse(null);
            String familyName = family == null ? "Family" : family.getFamilyName();
            String invitedBy = userAccountRepository.findById(invitation.getInvitedByUserId())
                .map(account -> account.getEmail())
                .orElse("Family admin");
            results.add(new PendingInvitationResponse(
                invitation.getId(),
                invitation.getFamilyId(),
                familyName,
                invitedBy,
                invitation.getInvitationToken(),
                invitation.getInviteCode(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                isExpired(invitation)
            ));
        }
        return results;
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

    /**
     * Resolve a claimable invitation.
     * With a token: load and validate (throws on missing / expired / mismatch / final).
     * Without a token: return the single valid PENDING invite for the email, or null.
     */
    private FamilyInvitation resolveClaimableInvitation(
            String normalizedEmail, String optionalToken) {
        if (optionalToken != null && !optionalToken.isBlank()) {
            FamilyInvitation byToken = familyInvitationRepository
                .findByInvitationToken(optionalToken.strip())
                .orElseThrow(() -> new InvitationNotFoundException(
                    "Invitation was not found."));
            ensureAcceptable(byToken, normalizedEmail);
            return byToken;
        }

        List<FamilyInvitation> pending =
            familyInvitationRepository.findPendingByEmail(normalizedEmail);
        List<FamilyInvitation> valid = pending.stream()
            .filter(this::isPendingAndUnexpired)
            .toList();
        if (valid.isEmpty()) {
            return null;
        }
        if (valid.size() > 1) {
            throw new InvitationConflictException(
                "Multiple pending invitations found; provide an invitation token.");
        }
        return valid.get(0);
    }

    // --- Helper methods ---

    /**
     * Invitation must be PENDING, unexpired, and addressed to the authenticated email.
     */
    private void ensureAcceptable(FamilyInvitation invitation, String normalizedEmail) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationConflictException("Invitation is no longer valid.");
        }
        if (isExpired(invitation)) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            familyInvitationRepository.saveAndFlush(invitation);
            throw new InvitationExpiredException("Invitation has expired.");
        }
        ensureEmailMatches(invitation, normalizedEmail);
    }

    private void ensureEmailMatches(FamilyInvitation invitation, String normalizedEmail) {
        if (!invitation.getInvitedEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new FamilyForbiddenException(
                "Invitation email does not match the authenticated user.");
        }
    }

    private boolean isPendingAndUnexpired(FamilyInvitation invitation) {
        return invitation.getStatus() == InvitationStatus.PENDING && !isExpired(invitation);
    }

    private boolean isExpired(FamilyInvitation invitation) {
        return invitation.getExpiresAt() == null
            || !invitation.getExpiresAt().isAfter(Instant.now());
    }

    // Apply the invitation claim
    private FamilyMeResponse applyInvitationClaim(UserAccount user, FamilyInvitation invitation) {
        if (familyMemberRepository.existsByIdUserId(user.getId())) {
            throw new AlreadyInFamilyException("You already belong to a family circle.");
        }

        Family family = familyRepository.findById(invitation.getFamilyId())
            .orElseThrow(() -> new FamilyNotFoundException(FamilyAuthorizationService.NOT_IN_FAMILY_MESSAGE));

        try {
            FamilyMember membership = new FamilyMember();
            membership.setId(new FamilyMember.FamilyMemberId(family.getId(), user.getId()));
            membership.setMemberRole(FamilyMember.ROLE_MEMBER);
            membership.setIsActive(true);
            familyMemberRepository.saveAndFlush(membership);

            DietaryProfile selfProfile = dietaryProfileRepository.findByLinkedUser_Id(user.getId())
                .orElseGet(DietaryProfile::new);
            selfProfile.setFamily(family);
            selfProfile.setLinkedUser(user);
            if (selfProfile.getProfileName() == null || selfProfile.getProfileName().isBlank()) {
                selfProfile.setProfileName(profileNameFromUser(user));
            }
            selfProfile.setRelationship("SELF");
            selfProfile.setPrimary(true);
            DietaryProfile savedProfile = dietaryProfileRepository.saveAndFlush(selfProfile);

            invitation.setStatus(InvitationStatus.ACCEPTED);
            familyInvitationRepository.saveAndFlush(invitation);

            return new FamilyMeResponse(
                family.getId(),
                family.getFamilyName(),
                FamilyMember.ROLE_MEMBER,
                savedProfile.getId(),
                family.getCreatedByUserId()
            );
        } catch (DataIntegrityViolationException ex) {
            if (isMembershipUniqueViolation(ex)) {
                throw new AlreadyInFamilyException("You already belong to a family circle.");
            }
            throw ex;
        }
    }

    // Convert the invitation to an invitation response
    private InvitationResponse toInvitationResponse(
            FamilyInvitation invitation, boolean inviteeRegistered, boolean emailSent) {
        String base = inviteProperties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String inviteUrl = base + "/invite/" + invitation.getInvitationToken();
        return new InvitationResponse(
            invitation.getId(),
            invitation.getInvitedEmail(),
            invitation.getInvitationToken(),
            invitation.getInviteCode(),
            inviteUrl,
            invitation.getStatus(),
            invitation.getExpiresAt(),
            inviteeRegistered,
            emailSent
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

    // Map the restrictions to the family me restriction detail
    private static List<FamilyMeRestrictionDetail> mapRestrictions(
            Optional<DietaryProfile> dietaryProfileOpt) {
        return dietaryProfileOpt.map(profile ->
            profile.getProfileRestrictions().stream()
                .map(restriction -> new FamilyMeRestrictionDetail(
                    restriction.getDietaryRestriction().getCode(),
                    restriction.getDietaryRestriction().getDisplayName(),
                    restriction.getSeverityLevel()
                )).toList()
        ).orElse(List.of());
    }

    /**
     * Splits profile restriction codes for the roster DTO.
     * RELIGIOUS category → commonRequirements; all other categories → restrictions.
     */
    private static RestrictionCodeSplit splitRestrictionCodes(
            Optional<DietaryProfile> dietaryProfileOpt) {
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

    // Generate a unique invitation token
    private String generateUniqueInvitationToken() {
        for (int attempt = 0; attempt < 8; attempt++) {
            byte[] bytes = new byte[INVITE_TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!familyInvitationRepository.existsByInvitationToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to generate a unique invitation token.");
    }

    // Generate a unique invite code
    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 16; attempt++) {
            StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                int index = secureRandom.nextInt(INVITE_CODE_ALPHABET.length());
                code.append(INVITE_CODE_ALPHABET.charAt(index));
            }
            String candidate = code.toString();
            if (!familyInvitationRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique invite code.");
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
}
