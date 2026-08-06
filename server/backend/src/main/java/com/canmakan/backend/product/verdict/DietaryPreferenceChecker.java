package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.knowledgebase.restriction.IngredientRestrictionLookup;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies approved ingredient mappings for vegetarian and vegan preferences.
 *
 * @author YangMaowei
 * @author Amelia Wong
 */
@Component
public final class DietaryPreferenceChecker implements RestrictionChecker {

    private static final Set<String> SUPPORTED_CODES = Set.of("VEGETARIAN", "VEGAN");

    private final IngredientRestrictionLookup restrictionLookup;

    public DietaryPreferenceChecker(IngredientRestrictionLookup restrictionLookup) {
        this.restrictionLookup = restrictionLookup;
    }

    @Override
    public boolean supports(RestrictionCategory category) {
        return category == RestrictionCategory.DIET;
    }

    @Override
    public void check(RestrictionRule rule, ProductData product, List<Finding> hits) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(hits, "hits");

        if (!supports(rule.category()) || !SUPPORTED_CODES.contains(rule.code())) {
            return;
        }

        List<Ingredient> ingredients = product.ingredients();
        if (ingredients != null) {
            for (Ingredient ingredient : ingredients) {
                if (ingredient == null) {
                    continue;
                }
                boolean nameConflict = restrictionLookup
                    .findApprovedConflictCodes(ingredient.ingredientName())
                    .contains(rule.code());
                boolean meatRoot = ingredient.rootAllergen() != null
                    && "MEAT".equalsIgnoreCase(ingredient.rootAllergen().trim());
                if (nameConflict || meatRoot) {
                    hits.add(new Finding(
                        rule.code(),
                        displayName(ingredient.ingredientName()),
                        displayName(ingredient.ingredientName()) + " conflicts with the "
                            + rule.code() + " restriction."
                    ));
                }
            }
        }

        if (!product.dataComplete() || ingredients == null || ingredients.isEmpty()) {
            hits.add(new Finding(
                rule.code(),
                Finding.SUBJECT_UNKNOWN,
                "Ingredient data is incomplete for the " + rule.code() + " restriction."
            ));
        }
    }

    private static String displayName(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return Finding.SUBJECT_UNKNOWN;
        }
        return ingredientName.trim();
    }
}
