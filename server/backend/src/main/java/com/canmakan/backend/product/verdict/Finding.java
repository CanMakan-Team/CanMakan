package com.canmakan.backend.product.verdict;

import java.util.Objects;

/**
 * A single reason a product was flagged. Findings are collected by the
 * {@link RestrictionChecker}s and rolled up into a {@link SafetyVerdict}
 * by the {@link DietaryRuleEngine}.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
public record Finding(
        String restrictionCode,
        String ingredientName,
        String reason,
        FindingType type
) {

    public Finding {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(type, "type");
    }

    /**
     * Backward-compatible constructor for existing deterministic conflict checkers.
     */
    public Finding(String restrictionCode, String ingredientName, String reason) {
        this(restrictionCode, ingredientName, reason, FindingType.CONFIRMED_CONFLICT);
    }

    public boolean isConfirmedViolation() {
        return type.isConfirmedViolation();
    }
}
