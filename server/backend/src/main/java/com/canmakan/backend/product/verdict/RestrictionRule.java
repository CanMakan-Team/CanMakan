package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;

/**
 * One active restriction for the evaluated dietary profile, flattened from
 * {@code profile_restrictions} joined to {@code dietary_restrictions}. Used as
 * input to the {@link DietaryRuleEngine}.
 *
 * @author XieHuayuan
 */
public record RestrictionRule(
        String code,                    // e.g. "PEANUT", "HALAL", "LOW_SUGAR"
        RestrictionCategory category,   // knowledgebase.model (ALLERGEN / RELIGIOUS / DIET)
        RestrictionSeverity severity    // STRICT_AVOID / INTOLERANCE
) {
}
