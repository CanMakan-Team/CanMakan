package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Checks allergen restrictions against a product's standardised ingredients.
 * Workflow:
 * raw ingredients → local DB resolution → keep matches + collect unresolved
 * → web-search unresolved → merge results → enrich Ingredient objects
 * → allergen checker evaluates them.
 *
 * @author YangMaowei
 */
@Component
public final class AllergenChecker implements RestrictionChecker {

    // Ingredients are only tagged with a "DAIRY" root allergen; there is no
    // separate lactose-specific tag. LACTOSE_INTOLERANT is a distinct
    // selectable restriction (see 05_household_dietary_data.sql id 16), so it
    // is treated as an alias of DAIRY here rather than matching nothing.
    private static final Set<String> DAIRY_ALIASES = Set.of("DAIRY", "LACTOSE_INTOLERANT");

    @Override
    public boolean supports(RestrictionCategory category) {
        return category == RestrictionCategory.ALLERGEN;
    }

    @Override
    public void check(
            RestrictionRule rule,
            ProductData product,
            List<Finding> hits
    ) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(hits, "hits");

        if (!supports(rule.category())
                || rule.code() == null
                || product.ingredients() == null) {
            return;
        }

        for (Ingredient ingredient : product.ingredients()) {
            if (ingredient != null && matches(rule.code(), ingredient.rootAllergen())) {
                String name = displayName(ingredient.ingredientName());
                hits.add(new Finding(
                        rule.code(),
                        name,
                        name + " matches the " + rule.code() + " restriction."
                ));
            }
        }
    }

    private static boolean matches(String ruleCode, String rootAllergen) {
        if (ruleCode.equals(rootAllergen)) {
            return true;
        }
        return rootAllergen != null
                && DAIRY_ALIASES.contains(ruleCode)
                && DAIRY_ALIASES.contains(rootAllergen);
    }

    private static String displayName(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return Finding.SUBJECT_UNKNOWN;
        }
        return ingredientName.trim();
    }
}
