package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Matches Open Food Facts declared allergen tags (e.g. {@code en:milk}) on catalog rows
 * against active allergen restrictions.
 */
@Component
public final class DeclaredAllergenChecker implements RestrictionChecker {

    private static final Map<String, List<String>> TAG_TO_RESTRICTION_CODES = Map.of(
            "en:milk", List.of("DAIRY", "MILK"),
            "en:gluten", List.of("GLUTEN"),
            "en:peanuts", List.of("PEANUT"),
            "en:nuts", List.of("PEANUT", "TREE_NUT", "NUTS"),
            "en:eggs", List.of("EGG"),
            "en:soybeans", List.of("SOY"),
            "en:fish", List.of("FISH"),
            "en:crustaceans", List.of("SHELLFISH"),
            "en:molluscs", List.of("SHELLFISH")
    );

    @Override
    public boolean supports(RestrictionCategory category) {
        return category == RestrictionCategory.ALLERGEN;
    }

    @Override
    public void check(RestrictionRule rule, ProductData product, List<Finding> hits) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(hits, "hits");

        if (!supports(rule.category()) || rule.code() == null || product.labelTags() == null) {
            return;
        }

        for (String rawTag : product.labelTags()) {
            if (rawTag == null || rawTag.isBlank()) {
                continue;
            }
            String tag = rawTag.trim().toLowerCase(Locale.ROOT);
            List<String> mappedCodes = TAG_TO_RESTRICTION_CODES.get(tag);
            if (mappedCodes == null || !mappedCodes.contains(rule.code())) {
                continue;
            }
            hits.add(new Finding(
                    rule.code(),
                    Finding.SUBJECT_LABEL,
                    "Declared allergen tag " + VerdictText.humanizeTag(rawTag) + " matches the "
                            + VerdictText.humanizeCode(rule.code()) + " restriction."
            ));
            return;
        }
    }
}
