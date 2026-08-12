package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Decides whether a catalog candidate may be suggested as an alternative.
 *
 * <p>Profiles with {@link RestrictionSeverity#INTOLERANCE} rules accept candidates that
 * avoid those triggers (e.g. dairy-free for lactose intolerance), even when the verdict
 * is WARNING for other reasons such as unresolved ingredients. Profiles without
 * intolerance rules keep the stricter SAFE-only behaviour.
 *
 * <p>Catalog hardening rejects obvious same-category triggers (cow milk for DAIRY,
 * wheat flour for GLUTEN) even when the rule engine returns SAFE.
 */
@Component
public class AlternativeCandidateFilter {

    private static final Set<String> DAIRY_MILK_CATEGORIES = Set.of(
            "Fresh milks",
            "UHT milks",
            "Whole milks"
    );

    private static final List<String> DAIRY_MILK_CATEGORY_TAGS = List.of(
            "en:fresh-milks",
            "en:uht-milks",
            "en:whole-milks"
    );

    private static final Set<String> WHEAT_FLOUR_CATEGORIES = Set.of(
            "Wheat flours",
            "White wheat flours"
    );

    private static final List<String> WHEAT_FLOUR_CATEGORY_TAGS = List.of(
            "en:wheat-flours",
            "en:white-wheat-flours",
            "en:bread-flours"
    );

    public boolean isAcceptableAlternative(
            List<RestrictionRule> rules,
            SafetyVerdict verdict,
            CatalogProduct candidate) {
        if (verdict == null) {
            return false;
        }
        if (verdict.level() == SafetyVerdict.Level.UNSAFE) {
            return false;
        }

        if (candidate != null && violatesCatalogSignals(rules, candidate)) {
            return false;
        }

        Set<String> intoleranceCodes = intoleranceRuleCodes(rules);
        if (intoleranceCodes.isEmpty()) {
            return verdict.level() == SafetyVerdict.Level.SAFE;
        }

        for (Finding finding : verdict.findings()) {
            if (finding.restrictionCode() != null && intoleranceCodes.contains(finding.restrictionCode())) {
                return false;
            }
        }
        return true;
    }

    private static boolean violatesCatalogSignals(
            List<RestrictionRule> rules,
            CatalogProduct candidate) {
        if (hasDairyIntoleranceRule(rules) && isCowMilkCatalogProduct(candidate)) {
            return true;
        }
        return hasGlutenAvoidanceRule(rules) && isWheatFlourCatalogProduct(candidate);
    }

    static boolean isCowMilkCatalogProduct(CatalogProduct candidate) {
        if (candidate.getMainCategoryEn() != null
                && DAIRY_MILK_CATEGORIES.contains(candidate.getMainCategoryEn())) {
            return true;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (CategoryTagParser.containsAny(categoryTags, DAIRY_MILK_CATEGORY_TAGS)) {
            return true;
        }
        return CategoryTagParser.containsTag(candidate.getAllergens(), "en:milk");
    }

    static boolean isWheatFlourCatalogProduct(CatalogProduct candidate) {
        if (candidate.getMainCategoryEn() != null
                && WHEAT_FLOUR_CATEGORIES.contains(candidate.getMainCategoryEn())) {
            return true;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (CategoryTagParser.containsAny(categoryTags, WHEAT_FLOUR_CATEGORY_TAGS)) {
            return true;
        }
        return CategoryTagParser.containsTag(candidate.getAllergens(), "en:gluten");
    }

    private static boolean hasDairyIntoleranceRule(List<RestrictionRule> rules) {
        return intoleranceRuleCodes(rules).contains("DAIRY");
    }

    private static boolean hasGlutenAvoidanceRule(List<RestrictionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        return rules.stream().anyMatch(rule ->
                "GLUTEN".equals(rule.code())
                        && (rule.severity() == RestrictionSeverity.STRICT_AVOID
                                || rule.severity() == RestrictionSeverity.INTOLERANCE));
    }

    private static Set<String> intoleranceRuleCodes(List<RestrictionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Set.of();
        }
        return rules.stream()
                .filter(rule -> rule.severity() == RestrictionSeverity.INTOLERANCE)
                .map(RestrictionRule::code)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
    }
}
