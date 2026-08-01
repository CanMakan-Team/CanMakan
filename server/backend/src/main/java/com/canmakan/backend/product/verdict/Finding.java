package com.canmakan.backend.product.verdict;

/**
 * A single reason a product was flagged. Findings are collected by the
 * {@link RestrictionChecker}s and rolled up into a {@link SafetyVerdict}
 * by the {@link DietaryRuleEngine}.
 *
 * @author XieHuayuan
 */
public record Finding(
        String restrictionCode,   // which rule triggered (null for data-quality findings)
        String ingredientName,    // the offending ingredient (nullable)
        String reason             // plain-language explanation shown to the user
) {
}
