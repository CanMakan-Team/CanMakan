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
 * is WARNING for other reasons such as unresolved ingredients. GLUTEN avoidance
 * profiles also accept WARNING gluten-free flour substitutes when there is no
 * GLUTEN finding. GLUTEN avoidance profiles also accept WARNING tagged gluten-free
 * breakfast cereal substitutes when there is no GLUTEN finding and the row does not
 * contain oats. PEANUT avoidance profiles accept WARNING peanut-free spread
 * substitutes when there is no PEANUT finding. LOW_SODIUM preference profiles accept
 * WARNING sauce or soy-sauce substitutes that declare reduced or low salt in the
 * product name, labels, or category tags. Other profiles without intolerance rules stay SAFE-only.
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

    private static final List<String> GLUTEN_FREE_FLOUR_TAGS = List.of(
            "en:gluten-free-flour",
            "Gluten free flour",
            "Gluten-free flour"
    );

    private static final List<String> GLUTEN_FREE_BREAD_TAGS = List.of(
            "Gluten free bread"
    );

    private static final List<String> GLUTEN_FREE_BREAD_LABEL_TAGS = List.of(
            "en:no-gluten",
            "en:certified-gluten-free"
    );

    private static final List<String> GLUTEN_FREE_BREAKFAST_CEREAL_TAGS = List.of(
            "Gluten free Breakfast cereals"
    );

    private static final List<String> BREAKFAST_CEREAL_CATEGORY_TAGS = List.of(
            "en:breakfast-cereals",
            "en:gluten-free-breakfast-cereals"
    );

    /** OFF category tags and curated tags that indicate a baking flour substitute. */
    private static final List<String> FLOUR_SUBSTITUTE_TAGS = List.of(
            "en:gluten-free-flour",
            "Gluten free flour",
            "Gluten-free flour",
            "en:corn-starch",
            "en:dried-coconut-flour",
            "en:brown-rice-flour",
            "en:buckwheat-flour",
            "en:amaranth-flour",
            "en:oat-flour",
            "en:flours"
    );

    private static final Set<String> PEANUT_BUTTER_CATEGORIES = Set.of(
            "Peanut butters",
            "Crunchy peanut butters"
    );

    private static final List<String> PEANUT_BUTTER_CATEGORY_TAGS = List.of(
            "en:peanut-butters",
            "en:crunchy-peanut-butters"
    );

    private static final List<String> HONEY_CATEGORY_TAGS = List.of(
            "en:honeys"
    );

    /** OFF tags for nut/seed butter substitutes when peanut butter is unsafe. */
    private static final List<String> PEANUT_BUTTER_SUBSTITUTE_TAGS = List.of(
            "en:nut-butters",
            "en:tahini",
            "en:cereal-butters",
            "en:oilseed-purees");

    private static final Set<String> SAUCE_CATEGORIES = Set.of(
            "Sauces",
            "Soy sauces"
    );

    private static final List<String> SAUCE_CATEGORY_TAGS = List.of(
            "en:sauces",
            "en:soy-sauces"
    );

    private static final List<String> LOW_SODIUM_SAUCE_CATEGORY_TAGS = List.of(
            "Low sodium sauces",
            "Low sodium sauce"
    );

    private static final List<String> LOW_SODIUM_SAUCE_LABEL_TAGS = List.of(
            "en:low-salt",
            "en:no-salt-added",
            "en:no-added-salt",
            "en:low-sodium",
            "en:reduced-salt",
            "en:low-or-no-sodium",
            "en:low-or-no-salt"
    );

    private static final List<String> LOW_SODIUM_SAUCE_NAME_PHRASES = List.of(
            "low salt",
            "reduced salt",
            "low sodium",
            "no salt added"
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
            if (verdict.level() == SafetyVerdict.Level.SAFE) {
                return true;
            }
            if (hasGlutenAvoidanceRule(rules)
                    && isGlutenFreeFlourSubstitute(candidate)
                    && !hasRestrictionFinding(verdict, "GLUTEN")) {
                return true;
            }
            if (hasGlutenAvoidanceRule(rules)
                    && isGlutenFreeBreakfastCerealSubstitute(candidate)
                    && !hasRestrictionFinding(verdict, "GLUTEN")) {
                return true;
            }
            if (hasLowSodiumPreference(rules) && isLowSodiumSauceSubstitute(candidate)) {
                return true;
            }
            return hasPeanutAvoidanceRule(rules)
                    && isPeanutFreeSpreadSubstitute(candidate)
                    && !hasRestrictionFinding(verdict, "PEANUT");
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
        if (hasGlutenAvoidanceRule(rules) && isWheatFlourCatalogProduct(candidate)) {
            return true;
        }
        return hasPeanutAvoidanceRule(rules) && isPeanutButterCatalogProduct(candidate);
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
        return CategoryTagParser.containsTag(candidate.getAllergens(), "en:milk")
                || CategoryTagParser.containsTag(candidate.getTracesTags(), "en:milk");
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

    static boolean isGlutenFreeFlourSubstitute(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        return CategoryTagParser.containsAny(categoryTags, GLUTEN_FREE_FLOUR_TAGS);
    }

    static boolean isFlourSubstitute(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (CategoryTagParser.containsAny(categoryTags, FLOUR_SUBSTITUTE_TAGS)) {
            return true;
        }
        String name = candidate.getProductName();
        return name != null && name.toLowerCase().contains("flour");
    }

    static boolean isPeanutButterCatalogProduct(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        if (candidate.getMainCategoryEn() != null
                && PEANUT_BUTTER_CATEGORIES.contains(candidate.getMainCategoryEn())) {
            return true;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (CategoryTagParser.containsAny(categoryTags, PEANUT_BUTTER_CATEGORY_TAGS)) {
            return true;
        }
        return CategoryTagParser.containsTag(candidate.getAllergens(), "en:peanuts");
    }

    static boolean isPeanutFreeSpreadSubstitute(CatalogProduct candidate) {
        if (candidate == null || isPeanutButterCatalogProduct(candidate)) {
            return false;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (!CategoryTagParser.containsAny(categoryTags, PEANUT_BUTTER_SUBSTITUTE_TAGS)) {
            return false;
        }
        if (CategoryTagParser.containsAny(categoryTags, HONEY_CATEGORY_TAGS)) {
            return false;
        }
        return !AlternativeProductQueryService.hasExcludedTrace(candidate, List.of("en:peanuts"));
    }

    static boolean isSauceOrSoySauceProduct(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        if (candidate.getMainCategoryEn() != null
                && SAUCE_CATEGORIES.contains(candidate.getMainCategoryEn())) {
            return true;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        return CategoryTagParser.containsAny(categoryTags, SAUCE_CATEGORY_TAGS);
    }

    static boolean isLowSodiumSauceSubstitute(CatalogProduct candidate) {
        if (!isSauceOrSoySauceProduct(candidate)) {
            return false;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (CategoryTagParser.containsAny(categoryTags, LOW_SODIUM_SAUCE_CATEGORY_TAGS)) {
            return true;
        }
        Set<String> labelTags = CategoryTagParser.parseTags(candidate.getLabelsTags());
        if (CategoryTagParser.containsAny(labelTags, LOW_SODIUM_SAUCE_LABEL_TAGS)) {
            return true;
        }
        return containsLowSodiumSauceNamePhrase(candidate);
    }

    private static boolean containsLowSodiumSauceNamePhrase(CatalogProduct candidate) {
        String haystack = joinLower(candidate.getProductName(), candidate.getBrand());
        for (String phrase : LOW_SODIUM_SAUCE_NAME_PHRASES) {
            if (haystack.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    static boolean isGlutenFreeBreadSubstitute(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (CategoryTagParser.containsAny(categoryTags, GLUTEN_FREE_BREAD_TAGS)) {
            return true;
        }
        if (!SubstituteDiscoveryProfiles.isBreadSource(candidate)) {
            return false;
        }
        Set<String> labelTags = CategoryTagParser.parseTags(candidate.getLabelsTags());
        return categoryTags.contains("en:breads")
                && CategoryTagParser.containsAny(labelTags, GLUTEN_FREE_BREAD_LABEL_TAGS);
    }

    static boolean isGlutenFreeBreakfastCerealSubstitute(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (!CategoryTagParser.containsAny(categoryTags, GLUTEN_FREE_BREAKFAST_CEREAL_TAGS)) {
            return false;
        }
        if (containsOatToken(candidate)) {
            return false;
        }
        if ("Breakfast cereals".equals(candidate.getMainCategoryEn())) {
            return true;
        }
        return CategoryTagParser.containsAny(categoryTags, BREAKFAST_CEREAL_CATEGORY_TAGS);
    }

    static boolean containsOatToken(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        String haystack = joinLower(
                candidate.getProductName(),
                candidate.getIngredientsText(),
                candidate.getCategoryTags(),
                candidate.getAllergens(),
                candidate.getTracesTags());
        return containsOatHaystack(haystack);
    }

    static boolean containsOatHaystack(String haystack) {
        if (haystack == null || haystack.isBlank()) {
            return false;
        }
        return haystack.contains("en:oat")
                || haystack.matches(".*\\boat(s)?\\b.*");
    }

    static boolean isIceCreamSubstitute(CatalogProduct candidate) {
        if (candidate == null) {
            return false;
        }
        return SubstituteDiscoveryProfiles.isIceCreamSource(candidate);
    }

    private static boolean hasRestrictionFinding(SafetyVerdict verdict, String restrictionCode) {
        if (verdict == null || verdict.findings() == null || restrictionCode == null) {
            return false;
        }
        return verdict.findings().stream()
                .anyMatch(finding -> restrictionCode.equals(finding.restrictionCode()));
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

    private static boolean hasPeanutAvoidanceRule(List<RestrictionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        return rules.stream().anyMatch(rule ->
                "PEANUT".equals(rule.code())
                        && (rule.severity() == RestrictionSeverity.STRICT_AVOID
                                || rule.severity() == RestrictionSeverity.INTOLERANCE));
    }

    static boolean hasLowSodiumPreference(List<RestrictionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        return rules.stream().anyMatch(rule -> "LOW_SODIUM".equals(rule.code()));
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

    private static String joinLower(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.append(value.toLowerCase(java.util.Locale.ROOT)).append(' ');
            }
        }
        return builder.toString();
    }
}
