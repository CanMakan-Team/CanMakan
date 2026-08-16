package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.mcp.server.CrossContaminationTool;
import com.canmakan.backend.knowledgebase.mcp.server.DietaryRuleTool;
import com.canmakan.backend.knowledgebase.model.Ingredient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produces a {@link SafetyVerdict} for one dietary profile against one product.
 *
 * <p>Pure assessment logic: it resolves any unknown ingredient through the
 * {@link IngredientResolver} boundary, runs every {@link RestrictionChecker}
 * (Spring injects one per category), then applies the verdict priority in
 * {@link #decide}. A thin caller is responsible for loading the active
 * {@link RestrictionRule}s (from the dietary profile) and the {@link ProductData}
 * (from the barcode via the integration + knowledgebase layers) and passing them here.
 *
 * <p>Verdict priority (strictest wins):
 * <ul>
 *   <li>a {@code STRICT_AVOID} restriction is violated &rarr; {@link SafetyVerdict.Level#UNSAFE}</li>
 *   <li>an {@code INTOLERANCE} is violated, or an ingredient stays unresolved,
 *       or the product data is incomplete, or cross-contamination traces match
 *       &rarr; {@link SafetyVerdict.Level#WARNING}</li>
 *   <li>nothing is triggered and the data is complete &rarr; {@link SafetyVerdict.Level#SAFE}</li>
 * </ul>
 *
 * @author XieHuayuan
 * @author YangMaowei
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class DietaryRuleEngine {

    /** Finding code when ingredient data is missing or unusable. */
    static final String INCOMPLETE_DATA = "INCOMPLETE_DATA";

    /** Finding code when an ingredient could not be mapped to a root allergen. */
    public static final String UNRESOLVED = "UNRESOLVED";

    /**
     * Finding code for trace / "may contain" risk. Kept distinct from profile allergen
     * codes so STRICT_AVOID rules do not escalate traces to UNSAFE.
     */
    static final String CROSS_CONTAMINATION = "CROSS_CONTAMINATION";

    private final List<RestrictionChecker> checkers;
    private final IngredientResolver resolver;
    private final DietaryRuleTool dietaryRuleTool;
    private final CrossContaminationTool crossContaminationTool;


    /**
     * Assess a product against a profile's active restrictions.
     *
     * @param rules   the profile's active restrictions (may be empty)
     * @param product the product snapshot to evaluate
     * @return the resulting {@link SafetyVerdict}
     */
    public SafetyVerdict assess(List<RestrictionRule> rules, ProductData product) {
        if (product == null || !product.dataComplete()
                || product.ingredients() == null || product.ingredients().isEmpty()) {
            Finding f = new Finding(
                    INCOMPLETE_DATA,
                    Finding.SUBJECT_UNKNOWN,
                    "No reliable ingredient data for this product - please verify the physical label."
            );
            return SafetyVerdict.warning(f.reason(), List.of(f));
        }

        List<RestrictionRule> activeRules = filterKnownRules(rules);

        // Resolve unknown ingredients via the knowledge boundary.
        // Catalog hits with no root allergen are known-safe (not UNRESOLVED).
        // Resolve unknown ingredients in a single batch call so the resolver can share
        // expensive lookups instead of one round trip per item.
        List<String> namesToResolve = new ArrayList<>();
        for (Ingredient ing : product.ingredients()) {
            if (ing.rootAllergen() == null || ing.rootAllergen().isBlank()) {
                namesToResolve.add(ing.ingredientName());
            }
        }
        Map<String, IngredientResolution> resolutions = resolver.resolveAll(namesToResolve);

        List<String> unresolvedNames = new ArrayList<>();
        List<Ingredient> resolved = new ArrayList<>();
        for (Ingredient ing : product.ingredients()) {
            if (ing.rootAllergen() != null && !ing.rootAllergen().isBlank()) {
                resolved.add(ing);
                continue;
            }

            IngredientResolution resolution =
                    resolutions.getOrDefault(ing.ingredientName(), IngredientResolution.unknown());
            switch (resolution.kind()) {
                case RESOLVED -> {
                    String name = resolution.canonicalName() != null && !resolution.canonicalName().isBlank()
                        ? resolution.canonicalName().trim()
                        : ing.ingredientName();
                    boolean chemicalAlias = resolution.chemicalAlias() || ing.chemicalAlias();
                    resolved.add(new Ingredient(
                        name,
                        ing.parentAllergen(),
                        resolution.rootAllergen(),
                        chemicalAlias));
                }
                case KNOWN_SAFE -> resolved.add(ing);
                case UNKNOWN -> {
                    // Deterministic keyword fallback for verbose labels the catalog missed (e.g.
                    // "Enriched High Protein Wheat Flour" -> GLUTEN). Catching a real allergen here
                    // decides the product at Tier 1 instead of escalating to the LLM, which would
                    // otherwise mis-tag every unresolved ingredient with the restriction.
                    String keywordRoot = AllergenKeywords.matchRoot(ing.ingredientName());
                    if (keywordRoot != null) {
                        resolved.add(new Ingredient(ing.ingredientName(), ing.parentAllergen(),
                                keywordRoot, ing.chemicalAlias()));
                    } else {
                        unresolvedNames.add(displayIngredientName(ing.ingredientName()));
                        resolved.add(ing);
                    }
                }
            }
        }

        ProductData enriched = new ProductData(product.barcode(), resolved,
                product.ingredientsText(), product.labelTags(), product.tracesTags(),
                product.nutrition(), true);

        List<Finding> findings = new ArrayList<>();
        for (RestrictionRule rule : activeRules) {
            for (RestrictionChecker checker : checkers) {
                if (checker.supports(rule.category())) {
                    checker.check(rule, enriched, findings);
                }
            }
        }

        findings.addAll(crossContaminationFindings(activeRules, enriched));

        return decide(activeRules, findings, unresolvedNames);
    }

    /**
     * Recommendation-specific assessment for sparse catalog rows. Ingredient resolution runs
     * only when {@code ingredients_text} is present; otherwise tags, allergens, nutrition,
     * and traces are evaluated without treating missing ingredients as incomplete data.
     */
    public SafetyVerdict assessForRecommendation(List<RestrictionRule> rules, ProductData product) {
        if (product == null) {
            Finding finding = new Finding(
                    INCOMPLETE_DATA,
                    Finding.SUBJECT_UNKNOWN,
                    "No reliable ingredient data for this product - please verify the physical label."
            );
            return SafetyVerdict.warning(finding.reason(), List.of(finding));
        }
        if (hasIngredientText(product)) {
            return assess(rules, product);
        }
        return assessWithoutIngredients(rules, product);
    }

    private SafetyVerdict assessWithoutIngredients(List<RestrictionRule> rules, ProductData product) {
        List<RestrictionRule> activeRules = filterKnownRules(rules);
        ProductData sparseProduct = new ProductData(
                product.barcode(),
                List.of(),
                null,
                product.labelTags(),
                product.tracesTags(),
                product.nutrition(),
                true);

        List<Finding> findings = new ArrayList<>();
        for (RestrictionRule rule : activeRules) {
            for (RestrictionChecker checker : checkers) {
                if (checker.supports(rule.category())) {
                    checker.check(rule, sparseProduct, findings);
                }
            }
        }
        findings.addAll(crossContaminationFindings(activeRules, sparseProduct));
        return decide(activeRules, findings, List.of());
    }

    private static boolean hasIngredientText(ProductData product) {
        return product.ingredientsText() != null
                && !product.ingredientsText().isBlank()
                && product.ingredients() != null
                && !product.ingredients().isEmpty();
    }

    /** Keeps only rules the dietary-rule MCP tool recognises (drops UNKNOWN definitions). */
    private List<RestrictionRule> filterKnownRules(List<RestrictionRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<RestrictionRule> active = new ArrayList<>();
        for (RestrictionRule rule : rules) {
            if (rule == null || rule.code() == null || rule.code().isBlank()) {
                continue;
            }
            DietaryRuleResult definition = dietaryRuleTool.lookup(rule.code());
            if (definition != null
                    && definition.category() != null
                    && !"UNKNOWN".equalsIgnoreCase(definition.category())) {
                active.add(rule);
            }
        }
        return active;
    }

    /**
     * Adds WARNING-only findings when label text signals traces that overlap the
     * profile's allergen rules. Trace risk never upgrades to UNSAFE by itself.
     */
    private List<Finding> crossContaminationFindings(
        List<RestrictionRule> activeRules,
        ProductData product
    ) {
        boolean blankText = product.ingredientsText() == null || product.ingredientsText().isBlank();
        boolean blankTraces = product.tracesTags() == null || product.tracesTags().isEmpty();
        if (blankText && blankTraces) {
            return List.of();
        }

        CrossContaminationResult result = crossContaminationTool.analyse(
            blankText ? null : product.ingredientsText(),
            blankTraces ? List.of() : product.tracesTags());
        if (result == null || !result.mayContain()
                || result.allergens() == null || result.allergens().isEmpty()) {
            return List.of();
        }

        Set<String> profileAllergenCodes = activeRules.stream()
            .filter(rule -> rule.category() == com.canmakan.backend.knowledgebase.model.RestrictionCategory.ALLERGEN)
            .map(RestrictionRule::code)
            .filter(code -> code != null && !code.isBlank())
            .map(code -> code.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (profileAllergenCodes.isEmpty()) {
            return List.of();
        }

        String phrase = result.phrase() == null || result.phrase().isBlank()
            ? "cross-contamination signal"
            : result.phrase();

        List<Finding> hits = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        for (String allergen : result.allergens()) {
            if (allergen == null || allergen.isBlank()) {
                continue;
            }
            String normalized = allergen.trim().toUpperCase(Locale.ROOT);
            Set<String> candidates = expandAllergenAliases(normalized);
            for (String candidate : candidates) {
                if (profileAllergenCodes.contains(candidate) && emitted.add(candidate)) {
                    hits.add(new Finding(
                            CROSS_CONTAMINATION,
                            candidate,
                            "Possible cross-contamination (" + VerdictText.humanizePhrase(phrase)
                                    + ") involving " + VerdictText.humanizeCode(candidate) + "."
                    ));
                }
            }
        }
        return hits;
    }

    private static Set<String> expandAllergenAliases(String allergenCode) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add(allergenCode);
        if ("MILK".equals(allergenCode) || "DAIRY".equals(allergenCode)) {
            codes.add("MILK");
            codes.add("DAIRY");
        }
        if ("NUTS".equals(allergenCode) || "TREE_NUT".equals(allergenCode) || "TREE_NUTS".equals(allergenCode)) {
            codes.add("TREE_NUT");
            codes.add("NUTS");
        }
        return codes;
    }

    /** Applies the verdict priority and assembles the {@link SafetyVerdict}. */
    SafetyVerdict decide(
        List<RestrictionRule> rules,
        List<Finding> findings,
        List<String> unresolvedIngredientNames
    ) {
        Map<String, RestrictionSeverity> severityByCode = rules.stream()
            .collect(Collectors.toMap(RestrictionRule::code, RestrictionRule::severity, (a, b) -> a));

        // Cross-contamination findings never count as STRICT_AVOID hits.
        boolean strictHit = findings.stream().anyMatch(f ->
            f.restrictionCode() != null
                && !CROSS_CONTAMINATION.equals(f.restrictionCode())
                && severityByCode.get(f.restrictionCode()) == RestrictionSeverity.STRICT_AVOID);

        // Group all unresolved ingredients into one caution finding instead of one per item.
        List<String> unresolved = unresolvedIngredientNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        List<Finding> all = new ArrayList<>(findings);
        if (!unresolved.isEmpty()) {
            all.add(new Finding(
                UNRESOLVED,
                String.join(", ", unresolved),
                "Treat these ingredients with caution: " + String.join(", ", unresolved) + "."
            ));
        }

        boolean hasUnresolved = !unresolved.isEmpty();
        SafetyVerdict.Level level;
        if (strictHit) {
            level = SafetyVerdict.Level.UNSAFE;
        } else if (!findings.isEmpty() || hasUnresolved) {
            level = SafetyVerdict.Level.WARNING;
        } else {
            level = SafetyVerdict.Level.SAFE;
        }
        return new SafetyVerdict(level, buildExplanation(level, findings, unresolved), all);
    }

    /** Deterministic fallback wording; the AI reasoning service can replace this later. */
    private String buildExplanation(
        SafetyVerdict.Level level, List<Finding> findings, List<String> unresolved) {
        List<String> parts = new ArrayList<>();
        if (!findings.isEmpty()) {
            parts.add(findings.stream().map(Finding::reason).collect(Collectors.joining("; ")));
        }
        if (!unresolved.isEmpty()) {
            String noun = unresolved.size() == 1 ? "ingredient" : "ingredients";
            parts.add(unresolved.size() + " " + noun
                    + " could not be fully checked against your profile. Details below.");
        }
        if (parts.isEmpty()) {
            return "No restrictions were triggered for the active profile.";
        }
        return level.name() + ": " + String.join(" ", parts);
    }

    private static String displayIngredientName(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return Finding.SUBJECT_UNKNOWN;
        }
        return ingredientName.trim();
    }
}
