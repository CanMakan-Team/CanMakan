package com.canmakan.backend.product.verdict;

/**
 * A single reason a product was flagged. Findings are collected by the
 * {@link RestrictionChecker}s and rolled up into a {@link SafetyVerdict}
 * by the {@link DietaryRuleEngine}.
 *
 * <p>{@code ingredientName} is always non-null for API clients: use an actual
 * ingredient, or a sentinel ({@link #SUBJECT_UNKNOWN}, {@link #SUBJECT_LABEL},
 * {@link #SUBJECT_NUTRITION}) when the finding is not ingredient-specific.
 *
 * @author XieHuayuan
 */
public record Finding(
        String restrictionCode,   // rule code, or INCOMPLETE_DATA / UNRESOLVED for data-quality
        String ingredientName,    // offending ingredient or a subject sentinel
        String reason             // plain-language explanation shown to the user
) {

    /** Product ingredient list is missing/unusable. */
    public static final String SUBJECT_UNKNOWN = "unknown";

    /** Finding is about certification / label tags, not a single ingredient. */
    public static final String SUBJECT_LABEL = "label";

    /** Finding is about a nutrition threshold, not a single ingredient. */
    public static final String SUBJECT_NUTRITION = "nutrition";
}
