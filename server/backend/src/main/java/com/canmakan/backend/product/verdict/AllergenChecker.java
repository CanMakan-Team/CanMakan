package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Checks allergen restrictions against a product's standardised ingredients.
 *
 * @author YangMaowei
 */
@Component
public final class AllergenChecker implements RestrictionChecker {

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
            if (ingredient != null
                    && rule.code().equals(ingredient.rootAllergen())) {
                hits.add(new Finding(
                        rule.code(),
                        ingredient.ingredientName(),
                        ingredient.ingredientName()
                                + " matches the "
                                + rule.code()
                                + " restriction."
                ));
            }
        }
    }
}
