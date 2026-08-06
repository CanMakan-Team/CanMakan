package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.knowledgebase.restriction.IngredientRestrictionLookup;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Applies the approved HALAL label and ingredient rules.
 *
 * @author YangMaowei
 */
@Component
public final class ReligiousChecker implements RestrictionChecker {

    private static final String HALAL = "HALAL";
    private static final Set<String> HALAL_TAGS = Set.of("en:halal", "halal");

    private final IngredientRestrictionLookup restrictionLookup;

    public ReligiousChecker(IngredientRestrictionLookup restrictionLookup) {
        this.restrictionLookup = restrictionLookup;
    }

    @Override
    public boolean supports(RestrictionCategory category) {
        return category == RestrictionCategory.RELIGIOUS;
    }

    @Override
    public void check(RestrictionRule rule, ProductData product, List<Finding> hits) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(hits, "hits");

        if (!supports(rule.category()) || !HALAL.equals(rule.code())) {
            return;
        }

        List<Ingredient> ingredients = product.ingredients();
        if (ingredients != null) {
            for (Ingredient ingredient : ingredients) {
                if (ingredient != null
                        && restrictionLookup.findApprovedConflictCodes(ingredient.ingredientName())
                                .contains(HALAL)) {
                    hits.add(new Finding(
                            HALAL,
                            displayName(ingredient.ingredientName()),
                            displayName(ingredient.ingredientName()) + " conflicts with the HALAL restriction."
                    ));
                }
            }
        }

        boolean hasHalalLabel = product.labelTags() != null
                && product.labelTags().stream().anyMatch(HALAL_TAGS::contains);
        if (!hasHalalLabel) {
            hits.add(new Finding(
                    HALAL,
                    Finding.SUBJECT_LABEL,
                    "Halal certification information could not be verified from the available product data."
            ));
        }

        if (!product.dataComplete() || ingredients == null || ingredients.isEmpty()) {
            hits.add(new Finding(
                    HALAL,
                    Finding.SUBJECT_UNKNOWN,
                    "Ingredient data is incomplete for the HALAL restriction."
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
