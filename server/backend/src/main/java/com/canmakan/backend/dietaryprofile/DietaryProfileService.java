package com.canmakan.backend.dietaryprofile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    // 1. Validate profile ID
    // 2. Verifies restriction selected exists in DB
    // 3. Sets up new profile restriction entity with joined key table
    // 4. Adds new profile restriction to profile
    // 5. Save profile to repo (JPA handles the update of related tables)
    @Transactional
    public void saveDietaryRestrictionSelections(Long profileId, Map<Long, String> selections) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile id is required");
        }

        DietaryProfile profile = dietaryProfileRepository.getReferenceById(profileId);
        profile.getProfileRestrictions().clear();

        if (selections != null) {
            for (Map.Entry<Long, String> entry : selections.entrySet()) {
                DietaryRestriction restriction = dietaryProfileRepository.findRestrictionById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Restriction not found: " + entry.getKey()));

                ProfileRestriction profileRestriction = new ProfileRestriction();
                profileRestriction.setId(new ProfileRestrictionId(profileId, restriction.getId()));
                profileRestriction.setDietaryProfile(profile);
                profileRestriction.setDietaryRestriction(restriction);
                profileRestriction.setSeverityLevel(entry.getValue());
                profile.getProfileRestrictions().add(profileRestriction);
            }
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
