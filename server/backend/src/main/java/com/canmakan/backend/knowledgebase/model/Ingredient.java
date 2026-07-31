package com.canmakan.backend.knowledgebase.model;

import java.util.Objects;

/**
 * Represents a standardised ingredient used by the dietary rule engine.
 *
 * @author YangMaowei
 */
public record Ingredient(
        String ingredientName,
        String parentAllergen,
        String rootAllergen,
        boolean chemicalAlias
) {

    public Ingredient {
        Objects.requireNonNull(ingredientName, "ingredientName");
    }
}
