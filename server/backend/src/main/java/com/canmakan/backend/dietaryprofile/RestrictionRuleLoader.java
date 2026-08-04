package com.canmakan.backend.dietaryprofile;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Bridges the "update dietary preference" data into the scan flow: loads a
 * profile's saved {@code profile_restrictions} (joined to {@code dietary_restrictions})
 * and flattens them into the {@link RestrictionRule} list the
 * {@code DietaryRuleEngine} consumes.
 *
 * <p>The DB stores {@code dietary_restrictions.category} as
 * {@code ALLERGEN / RELIGIOUS / DIET} and {@code profile_restrictions.severity_level}
 * as {@code STRICT_AVOID / INTOLERANCE}, which map straight onto
 * {@link RestrictionCategory} and {@link RestrictionSeverity}.
 *
 * @author XieHuayuan
 */
@Service
public class RestrictionRuleLoader {

    private final DietaryProfileRepository dietaryProfileRepository;

    public RestrictionRuleLoader(DietaryProfileRepository dietaryProfileRepository) {
        this.dietaryProfileRepository = dietaryProfileRepository;
    }

    /**
     * Load the active restriction rules for a dietary profile.
     *
     * @param profileId the profile the product is assessed against
     * @return the flattened rules (code + category + severity); never {@code null}
     */
    public List<RestrictionRule> load(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("Profile id is required");
        }
        return dietaryProfileRepository.findProfileRestrictionsByProfileId(profileId).stream()
                .map(this::toRule)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Map one join row to a {@link RestrictionRule}, or {@code null} if it is unusable. */
    private RestrictionRule toRule(ProfileRestriction profileRestriction) {
        DietaryRestriction restriction = profileRestriction.getDietaryRestriction();
        if (restriction == null || restriction.getCode() == null) {
            return null;
        }
        RestrictionCategory category = parseCategory(restriction.getCategory());
        RestrictionSeverity severity = parseSeverity(profileRestriction.getSeverityLevel());
        if (category == null || severity == null) {
            return null;   // skip rows with unknown category/severity rather than fail the scan
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
