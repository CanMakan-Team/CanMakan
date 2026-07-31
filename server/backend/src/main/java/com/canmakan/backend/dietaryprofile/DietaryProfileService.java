package com.canmakan.backend.dietaryprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic layer for Dietary Profile Service functions
 * 
 * @author Amelia Wong
 */
@AllArgsConstructor
@Service
public class DietaryProfileService {

    private final DietaryProfileRepository dietaryProfileRepository;

    // Retrieves dietary restriction catalog
    // Maps each restriction to record dto and returns list of dto
    public List<DietaryRestrictionDto> getAllDietaryRestrictions() {
        return dietaryProfileRepository.findAllRestrictions().stream()
                .map(restriction -> new DietaryRestrictionDto(
                    restriction.getId(),
                    restriction.getCode(),
                    restriction.getDisplayName(),
                    restriction.getCategory(),
                    restriction.getDescription()
                ))
                .toList();
    }

    // Retrieves dietary restrictions already set under specific profile
    // Builds a Map<Long, String>, where the key is Restriction ID and value is Severity Level
    // Linked Hash Map - preserve insertion order
    public Map<Long, String> getDietaryRestrictionsForProfile(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile id is required");
        }

        return dietaryProfileRepository.findProfileRestrictionsByProfileId(profileId).stream()
            .collect(Collectors.toMap(
                profileRestriction -> profileRestriction.getDietaryRestriction().getId(),
                ProfileRestriction::getSeverityLevel,
                (existing, replacement) -> replacement,
                LinkedHashMap::new
            ));
    }

    // Saves selected dietary restrictions to specific profile
    // 1. Validate profile ID and load the target profile
    // 2. Resolve all requested restriction IDs to managed entities
    // 3. Index existing profile restrictions by restriction ID
    // 4. Update severity for existing restrictions and add brand-new ones
    // 5. Save profile so JPA flushes the relationship changes
    @Transactional
    public void saveDietaryRestrictionSelections(Long profileId, Map<Long, String> selections) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile id is required");
        }

        DietaryProfile profile = dietaryProfileRepository.findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + profileId));

        Map<Long, String> requestedSelections = selections == null ? Map.of() : selections;
        Map<Long, DietaryRestriction> requestedRestrictions = new HashMap<>();

        for (Long restrictionId : requestedSelections.keySet()) {
            DietaryRestriction restriction = dietaryProfileRepository.findRestrictionById(restrictionId)
                .orElseThrow(() -> new IllegalArgumentException("Restriction not found: " + restrictionId));
            requestedRestrictions.put(restrictionId, restriction);
        }

        Map<Long, ProfileRestriction> existingByRestrictionId = profile.getProfileRestrictions().stream()
            .collect(Collectors.toMap(
                profileRestriction -> profileRestriction.getDietaryRestriction().getId(),
                profileRestriction -> profileRestriction));

        Set<Long> requestedIds = requestedSelections.keySet();
        profile.getProfileRestrictions().removeIf(
            profileRestriction -> !requestedIds.contains(profileRestriction.getDietaryRestriction().getId()));

        for (Map.Entry<Long, String> entry : requestedSelections.entrySet()) {
            ProfileRestriction existing = existingByRestrictionId.get(entry.getKey());
            if (existing != null) {
                existing.setSeverityLevel(entry.getValue());
                continue;
            }

            ProfileRestriction profileRestriction = new ProfileRestriction();
            profileRestriction.setId(new ProfileRestrictionId(profileId, entry.getKey()));
            profileRestriction.setDietaryProfile(profile);
            profileRestriction.setDietaryRestriction(requestedRestrictions.get(entry.getKey()));
            profileRestriction.setSeverityLevel(entry.getValue());
            profile.getProfileRestrictions().add(profileRestriction);
        }

        dietaryProfileRepository.save(profile);
    }

    // Temporary record dto
    public record DietaryRestrictionDto(
        Long id,
        String code,
        String displayName,
        String category,
        String description) {
    }
}
