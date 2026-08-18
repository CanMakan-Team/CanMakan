package com.canmakan.backend.dietaryprofile.service;

import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.dietaryprofile.repository.ProfileRestrictionRepository;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads a profile's saved restrictions as {@link RestrictionRule} rows for the scan engine.
 * 
 * @author Amelia Wong
 */
@Service
@RequiredArgsConstructor
public class RestrictionRuleLoader {

    private final ProfileRestrictionRepository profileRestrictionRepository;

    public List<RestrictionRule> load(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile id is required");
        }
        return profileRestrictionRepository.findByDietaryProfileId(profileId).stream()
                .map(this::toRule)
                .filter(Objects::nonNull)
                .toList();
    }

    private RestrictionRule toRule(ProfileRestriction profileRestriction) {
        DietaryRestriction restriction = profileRestriction.getDietaryRestriction();
        if (restriction == null || restriction.getCode() == null) {
            return null;
        }
        RestrictionCategory category = parseCategory(restriction.getCategory());
        RestrictionSeverity severity = parseSeverity(profileRestriction.getSeverityLevel());
        if (category == null || severity == null) {
            return null;
        }
        return new RestrictionRule(restriction.getCode(), category, severity);
    }

    private RestrictionCategory parseCategory(String raw) {
        try {
            return raw == null ? null : RestrictionCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private RestrictionSeverity parseSeverity(String raw) {
        try {
            return raw == null ? null : RestrictionSeverity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
