package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

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
 */
@Service
public class DietaryRuleEngine {

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
        if (product == null) {
            Finding f = new Finding(
                    null,
                    null,
                    "No reliable product data is available - please verify the physical label.",
                    FindingType.INCOMPLETE_DATA
            );
            return SafetyVerdict.warning(f.reason(), List.of(f));
        }

        List<RestrictionRule> activeRules = rules == null
                ? List.of()
                : rules.stream().filter(Objects::nonNull).toList();
        boolean requiresAllergenResolution = activeRules.stream()
                .anyMatch(rule -> rule.category() == RestrictionCategory.ALLERGEN);

        boolean hasUnresolved = false;
        List<Ingredient> resolved = new ArrayList<>();
        List<Ingredient> ingredients = product.ingredients() == null
                ? List.of()
                : product.ingredients();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null) {
                if (requiresAllergenResolution) {
                    hasUnresolved = true;
                }
                continue;
            }

            if (requiresAllergenResolution
                    && (ingredient.rootAllergen() == null || ingredient.rootAllergen().isBlank())) {
                String root = resolveRootAllergen(ingredient.ingredientName());
                if (root == null || root.isBlank()) {
                    hasUnresolved = true;
                    resolved.add(ingredient);
                } else {
                    resolved.add(new Ingredient(
                            ingredient.ingredientName(),
                            ingredient.parentAllergen(),
                            root,
                            ingredient.chemicalAlias()
                    ));
                }
            } else {
                resolved.add(ingredient);
            }
        }

        ProductData enriched = new ProductData(
                product.barcode(),
                resolved,
                product.ingredientsText(),
                product.labelTags(),
                product.nutrition(),
                product.dataComplete()
        );

        List<Finding> findings = new ArrayList<>();
        if (requiresAllergenResolution
                && (!product.dataComplete() || ingredients.isEmpty())) {
            findings.add(new Finding(
                    null,
                    null,
                    "Ingredient data is incomplete for the active allergen restrictions.",
                    FindingType.INCOMPLETE_DATA
            ));
        }

        for (RestrictionRule rule : activeRules) {
            for (RestrictionChecker checker : checkers) {
                if (checker.supports(rule.category())) {
                    checker.check(rule, enriched, findings);
                }
            }
        }
        return decide(activeRules, findings, hasUnresolved);
    }

    /**
     * Aggregates deterministic and non-authoritative evidence using the same
     * severity rules as a normal assessment.
     */
    public SafetyVerdict aggregate(List<RestrictionRule> rules, List<Finding> findings) {
        List<RestrictionRule> activeRules = rules == null ? List.of() : rules;
        List<Finding> availableFindings = findings == null ? List.of() : findings;
        return decide(activeRules, availableFindings, false);
    }

    /** Applies the verdict priority and assembles the {@link SafetyVerdict}. */
    SafetyVerdict decide(List<RestrictionRule> rules, List<Finding> findings, boolean hasUnresolved) {
        Map<String, RestrictionSeverity> severityByCode = rules.stream()
                .filter(Objects::nonNull)
                .filter(rule -> rule.code() != null && rule.severity() != null)
                .collect(Collectors.toMap(RestrictionRule::code, RestrictionRule::severity, (a, b) -> a));

        boolean strictHit = findings.stream().anyMatch(f ->
                f != null
                        && f.isConfirmedViolation()
                        && f.restrictionCode() != null
                        && severityByCode.get(f.restrictionCode()) == RestrictionSeverity.STRICT_AVOID);

        List<Finding> all = findings.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        if (hasUnresolved) {
            all.add(new Finding(
                    null,
                    null,
                    "Some ingredients could not be fully analysed - treat with caution.",
                    FindingType.UNRESOLVED_INGREDIENT
            ));
        }

        SafetyVerdict.Level level;
        if (strictHit) {
            level = SafetyVerdict.Level.UNSAFE;
        } else if (!all.isEmpty()) {
            level = SafetyVerdict.Level.WARNING;
        } else {
            level = SafetyVerdict.Level.SAFE;
        }
        return new SafetyVerdict(level, buildExplanation(level, all), all);
    }

    private String resolveRootAllergen(String ingredientName) {
        try {
            return resolver.resolveRootAllergen(ingredientName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Deterministic fallback wording; the AI reasoning service can replace this later. */
    private String buildExplanation(SafetyVerdict.Level level, List<Finding> findings) {
        if (findings.isEmpty()) {
            return "No restrictions were triggered for the active profile.";
        }
        return level.name() + ": "
                + findings.stream().map(Finding::reason).collect(Collectors.joining("; "));
    }
}
