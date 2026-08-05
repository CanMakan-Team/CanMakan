package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *       or the product data is incomplete &rarr; {@link SafetyVerdict.Level#WARNING}</li>
 *   <li>nothing is triggered and the data is complete &rarr; {@link SafetyVerdict.Level#SAFE}</li>
 * </ul>
 *
 * @author XieHuayuan
 * @author YangMaowei
 * @author Amelia
 */
@Service
public class DietaryRuleEngine {

    /** Finding code when ingredient data is missing or unusable. */
    static final String INCOMPLETE_DATA = "INCOMPLETE_DATA";

    /** Finding code when an ingredient could not be mapped to a root allergen. */
    static final String UNRESOLVED = "UNRESOLVED";

    private final List<RestrictionChecker> checkers;   // one implementation per category
    private final IngredientResolver resolver;         // knowledgebase / agentic-ai boundary

    public DietaryRuleEngine(List<RestrictionChecker> checkers, IngredientResolver resolver) {
        this.checkers = checkers;
        this.resolver = resolver;
    }

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

        // Resolve unknown / chemical-alias ingredients via the boundary; note anything left unresolved.
        List<String> unresolvedNames = new ArrayList<>();
        List<Ingredient> resolved = new ArrayList<>();
        for (Ingredient ing : product.ingredients()) {
            if (ing.rootAllergen() == null || ing.rootAllergen().isBlank()) {
                String root = resolver.resolveRootAllergen(ing.ingredientName());
                if (root == null) {
                    unresolvedNames.add(displayIngredientName(ing.ingredientName()));
                    resolved.add(ing);
                } else {
                    resolved.add(new Ingredient(
                            ing.ingredientName(), ing.parentAllergen(), root, ing.chemicalAlias()));
                }
            } else {
                resolved.add(ing);
            }
        }

        ProductData enriched = new ProductData(product.barcode(), resolved,
                product.ingredientsText(), product.labelTags(), product.nutrition(), true);

        List<Finding> findings = new ArrayList<>();
        for (RestrictionRule rule : rules) {
            for (RestrictionChecker checker : checkers) {
                if (checker.supports(rule.category())) {
                    checker.check(rule, enriched, findings);
                }
            }
        }
        return decide(rules, findings, unresolvedNames);
    }

    /** Applies the verdict priority and assembles the {@link SafetyVerdict}. */
    SafetyVerdict decide(
            List<RestrictionRule> rules,
            List<Finding> findings,
            List<String> unresolvedIngredientNames
    ) {
        Map<String, RestrictionSeverity> severityByCode = rules.stream()
                .collect(Collectors.toMap(RestrictionRule::code, RestrictionRule::severity, (a, b) -> a));

        boolean strictHit = findings.stream().anyMatch(f ->
                f.restrictionCode() != null
                        && severityByCode.get(f.restrictionCode()) == RestrictionSeverity.STRICT_AVOID);

        List<Finding> all = new ArrayList<>(findings);
        for (String ingredientName : unresolvedIngredientNames) {
            all.add(new Finding(
                    UNRESOLVED,
                    ingredientName,
                    ingredientName + " could not be fully analysed - treat with caution."
            ));
        }

        boolean hasUnresolved = !unresolvedIngredientNames.isEmpty();
        SafetyVerdict.Level level;
        if (strictHit) {
            level = SafetyVerdict.Level.UNSAFE;
        } else if (!findings.isEmpty() || hasUnresolved) {
            level = SafetyVerdict.Level.WARNING;
        } else {
            level = SafetyVerdict.Level.SAFE;
        }
        return new SafetyVerdict(level, buildExplanation(level, all), all);
    }

    /** Deterministic fallback wording; the AI reasoning service can replace this later. */
    private String buildExplanation(SafetyVerdict.Level level, List<Finding> findings) {
        if (findings.isEmpty()) {
            return "No restrictions were triggered for the active profile.";
        }
        return level.name() + ": "
                + findings.stream().map(Finding::reason).collect(Collectors.joining("; "));
    }

    private static String displayIngredientName(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return Finding.SUBJECT_UNKNOWN;
        }
        return ingredientName.trim();
    }
}
