package com.canmakan.backend.product.model;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.List;
import java.util.Objects;

/**
 * Represents a source-neutral product snapshot returned by product lookup
 * before adaptation into the dietary rule-engine contract.
 *
 * @author YangMaowei
 */
public record ProductLookupResult(
        String barcode,
        String productName,
        String productType,
        List<Ingredient> ingredients,
        String ingredientsText,
        String labelTags,
        Nutrition nutrition,
        boolean ingredientDataComplete
) {

    public ProductLookupResult {
        Objects.requireNonNull(barcode, "barcode");

        if (barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }

        ingredients = ingredients == null
                ? List.of()
                : List.copyOf(ingredients);
    }

    public ProductLookupResult(
            String barcode,
            List<Ingredient> ingredients,
            String ingredientsText,
            String labelTags,
            Nutrition nutrition,
            boolean ingredientDataComplete
    ) {
        this(
                barcode,
                null,
                null,
                ingredients,
                ingredientsText,
                labelTags,
                nutrition,
                ingredientDataComplete
        );
    }
}
