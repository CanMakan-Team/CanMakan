package com.canmakan.backend.dietaryprofile.service;

import com.canmakan.backend.dietaryprofile.dto.DietaryProfileSummaryDto;
import com.canmakan.backend.dietaryprofile.dto.DietaryRestrictionDto;
import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestrictionId;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.dietaryprofile.repository.ProfileRestrictionRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
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

    private final DietaryProfileRepository dietaryProfileRepository;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final ProfileRestrictionRepository profileRestrictionRepository;

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

        return dietaryProfileRepository.findProfilesByFamilyId(familyId).stream()
            .map(profile -> new DietaryProfileSummaryDto(
                profile.getId(),
                profile.getProfileName(),
                profile.getFamily() == null ? null : profile.getFamily().getId(),
                profile.getRelationship(),
                profile.getProfileName() == null || profile.getProfileName().isBlank()
                    ? ""
                    : profile.getProfileName().substring(0, Math.min(2, profile.getProfileName().length())).toUpperCase(),
                profile.isPrimary()
            ))
            .toList();
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

        Map<Long, String> requestedSelections = selections == null ? Map.of() : selections;
        Map<Long, DietaryRestriction> requestedRestrictions = new HashMap<>();

        for (Long restrictionId : requestedSelections.keySet()) {
            DietaryRestriction restriction = dietaryRestrictionRepository.findById(restrictionId)
                .orElseThrow(() -> new IllegalArgumentException("Restriction not found: " + restrictionId));
            requestedRestrictions.put(restrictionId, restriction);
        }

        Set<Long> requestedIds = requestedSelections.keySet();
        Set<ProfileRestriction> profileRestrictions = profile.getProfileRestrictions();
        List<ProfileRestriction> restrictionsToRemove = profileRestrictions.stream()
            .filter(profileRestriction -> !requestedIds.contains(profileRestriction.getDietaryRestriction().getId()))
            .toList();
        profileRestrictions.removeAll(restrictionsToRemove);

        Map<Long, ProfileRestriction> existingByRestrictionId = profileRestrictions.stream()
            .collect(Collectors.toMap(
                profileRestriction -> profileRestriction.getDietaryRestriction().getId(),
                profileRestriction -> profileRestriction));

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
            profileRestrictions.add(profileRestriction);
        }

        dietaryProfileRepository.save(profile);
    }
}
