package com.canmakan.backend.dietaryprofile.service;

import com.canmakan.backend.dietaryprofile.dto.CreateSelfProfileRequest;
import com.canmakan.backend.dietaryprofile.dto.DietaryProfileSummaryDto;
import com.canmakan.backend.dietaryprofile.dto.DietaryRestrictionDto;
import com.canmakan.backend.dietaryprofile.dto.SelfProfileResponse;
import com.canmakan.backend.dietaryprofile.exception.SelfProfileAlreadyExistsException;
import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestrictionId;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.repository.ProfileRestrictionRepository;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for dietary profiles and restriction selections (UC1).
 * 
 * @author Amelia Wong
 */
@AllArgsConstructor
@Service
public class DietaryProfileService {

    private static final String LINKED_USER_UNIQUE_CONSTRAINT =
        "uq_dietary_profiles_linked_user";

    private final DietaryProfileRepository dietaryProfileRepository;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final ProfileRestrictionRepository profileRestrictionRepository;
    private final UserAccountRepository userAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;

    /**
     * Creates the authenticated account's standalone SELF profile and selections.
     * This transaction is intentionally separate from public account registration.
     */
    @Transactional
    public SelfProfileResponse createSelfProfile(
            long userId, CreateSelfProfileRequest request) {
        if (dietaryProfileRepository.findByLinkedUser_Id(userId).isPresent()) {
            throw new SelfProfileAlreadyExistsException();
        }

        UserAccount account = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));
        Map<Long, ResolvedRestriction> resolvedRestrictions =
            resolveRestrictionSelections(request.restrictions(), true);

        DietaryProfile profile = new DietaryProfile();
        profile.setLinkedUser(account);
        profile.setProfileName(request.profileName());
        profile.setRelationship("SELF");
        profile.setPrimary(true);

        DietaryProfile savedProfile;
        try {
            savedProfile = dietaryProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException exception) {
            if (isLinkedUserUniqueViolation(exception)) {
                throw new SelfProfileAlreadyExistsException(exception);
            }
            throw exception;
        }

        applyRestrictionSelections(savedProfile, resolvedRestrictions);
        dietaryProfileRepository.saveAndFlush(savedProfile);

        return new SelfProfileResponse(
            savedProfile.getId(),
            savedProfile.getProfileName(),
            savedProfile.getRelationship(),
            savedProfile.isActive(),
            resolvedRestrictions.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().severity()
            ))
        );
    }

    public List<DietaryRestrictionDto> getAllDietaryRestrictions() {
        return dietaryRestrictionRepository.findAllOrderedByDisplayName().stream()
                .map(restriction -> new DietaryRestrictionDto(
                    restriction.getId(),
                    restriction.getCode(),
                    restriction.getDisplayName(),
                    restriction.getCategory(),
                    restriction.getDescription()
                ))
                .toList();
    }

    public List<DietaryProfileSummaryDto> getProfilesByFamilyId(Long familyId) {
        if (familyId == null) {
            throw new IllegalArgumentException("Family id is required");
        }

        Set<Long> familyAdminUserIds = familyAdminUserIds(familyId);
        return dietaryProfileRepository.findProfilesByFamilyId(familyId).stream()
            .map(profile -> toSummaryDto(profile, familyAdminUserIds))
            .toList();
    }

    public List<DietaryProfileSummaryDto> getAllProfilesByFamilyId(Long familyId) {
        if (familyId == null) {
            throw new IllegalArgumentException("Family id is required");
        }

        Set<Long> familyAdminUserIds = familyAdminUserIds(familyId);
        return dietaryProfileRepository.findAllProfilesByFamilyId(familyId).stream()
            .map(profile -> toSummaryDto(profile, familyAdminUserIds))
            .toList();
    }

    private Set<Long> familyAdminUserIds(Long familyId) {
        return new HashSet<>(familyMemberRepository.findActivePrimaryAdminUserIds(familyId));
    }

    private DietaryProfileSummaryDto toSummaryDto(
            DietaryProfile profile, Set<Long> familyAdminUserIds) {
        Long linkedUserId = profile.getLinkedUser() == null
            ? null
            : profile.getLinkedUser().getId();
        boolean isFamilyAdminProfile = linkedUserId != null
            && familyAdminUserIds.contains(linkedUserId);
        return new DietaryProfileSummaryDto(
            profile.getId(),
            profile.getProfileName(),
            profile.getFamily() == null ? null : profile.getFamily().getId(),
            profile.getRelationship(),
            profile.getProfileName() == null || profile.getProfileName().isBlank()
                ? ""
                : profile.getProfileName().substring(0, Math.min(2, profile.getProfileName().length())).toUpperCase(),
            isFamilyAdminProfile,
            profile.isActive()
        );
    }

    public Map<Long, String> getDietaryRestrictionsForProfile(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile id is required");
        }

        Map<Long, String> restrictionsById = new LinkedHashMap<>();
        for (ProfileRestriction profileRestriction
                : profileRestrictionRepository.findByDietaryProfileId(profileId)) {
            restrictionsById.put(
                    profileRestriction.getDietaryRestriction().getId(),
                    profileRestriction.getSeverityLevel());
        }
        return restrictionsById;
    }

    @Transactional
    public void saveDietaryRestrictionSelections(Long profileId, Map<Long, String> selections) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile id is required");
        }

        DietaryProfile profile = dietaryProfileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));

        Map<Long, ResolvedRestriction> resolvedRestrictions =
            resolveRestrictionSelections(selections, false);

        applyRestrictionSelections(profile, resolvedRestrictions);
        dietaryProfileRepository.save(profile);
    }

    private Map<Long, ResolvedRestriction> resolveRestrictionSelections(
            Map<Long, String> selections,
            boolean validateForSelfSetup) {
        Map<Long, String> requestedSelections = selections == null ? Map.of() : selections;
        Map<Long, ResolvedRestriction> resolved = new HashMap<>();

        for (Map.Entry<Long, String> entry : requestedSelections.entrySet()) {
            Long restrictionId = entry.getKey();
            if (restrictionId == null) {
                throw new IllegalArgumentException("Restriction id is required.");
            }
            DietaryRestriction restriction = dietaryRestrictionRepository.findById(restrictionId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Restriction not found: " + restrictionId));
            String severity = validateForSelfSetup
                ? normalizeSelfSetupSeverity(entry.getValue())
                : entry.getValue();
            resolved.put(restrictionId, new ResolvedRestriction(restriction, severity));
        }
        return resolved;
    }

    private void applyRestrictionSelections(
            DietaryProfile profile,
            Map<Long, ResolvedRestriction> requestedRestrictions) {
        Set<Long> requestedIds = requestedRestrictions.keySet();

        Set<ProfileRestriction> profileRestrictions = profile.getProfileRestrictions();
        List<ProfileRestriction> restrictionsToRemove = profileRestrictions.stream()
            .filter(profileRestriction -> !requestedIds.contains(profileRestriction.getDietaryRestriction().getId()))
            .toList();
        profileRestrictions.removeAll(restrictionsToRemove);

        Map<Long, ProfileRestriction> existingByRestrictionId = profileRestrictions.stream()
            .collect(Collectors.toMap(
                profileRestriction -> profileRestriction.getDietaryRestriction().getId(),
                profileRestriction -> profileRestriction));

        for (Map.Entry<Long, ResolvedRestriction> entry : requestedRestrictions.entrySet()) {
            ProfileRestriction existing = existingByRestrictionId.get(entry.getKey());
            if (existing != null) {
                existing.setSeverityLevel(entry.getValue().severity());
                continue;
            }

            ProfileRestriction profileRestriction = new ProfileRestriction();
            profileRestriction.setId(new ProfileRestrictionId(profile.getId(), entry.getKey()));
            profileRestriction.setDietaryProfile(profile);
            profileRestriction.setDietaryRestriction(entry.getValue().restriction());
            profileRestriction.setSeverityLevel(entry.getValue().severity());
            profileRestrictions.add(profileRestriction);
        }
    }

    private static String normalizeSelfSetupSeverity(String rawSeverity) {
        if (rawSeverity == null || rawSeverity.isBlank()) {
            throw new IllegalArgumentException("Restriction severity is required.");
        }
        String normalized = rawSeverity.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > 20) {
            throw new IllegalArgumentException(
                "Restriction severity must not exceed 20 characters."
            );
        }
        RestrictionSeverity severity;
        try {
            severity = RestrictionSeverity.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "Restriction severity must be STRICT_AVOID or INTOLERANCE.",
                exception
            );
        }
        // Self-setup only lets a user pick these two severities. PREFERENCE is a
        // valid enum value used by seeded rules, so it is rejected explicitly here
        // rather than relying on enum membership.
        if (severity != RestrictionSeverity.STRICT_AVOID
                && severity != RestrictionSeverity.INTOLERANCE) {
            throw new IllegalArgumentException(
                "Restriction severity must be STRICT_AVOID or INTOLERANCE."
            );
        }
        return severity.name();
    }

    private static boolean isLinkedUserUniqueViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && containsLinkedUserConstraint(constraintViolation.getConstraintName())) {
                return true;
            }
            if (containsLinkedUserConstraint(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsLinkedUserConstraint(String value) {
        return value != null
            && value.toLowerCase(Locale.ROOT).contains(LINKED_USER_UNIQUE_CONSTRAINT);
    }

    private record ResolvedRestriction(
        DietaryRestriction restriction,
        String severity
    ) {
    }
}
