package com.canmakan.backend.knowledgebase.model;

import java.util.Objects;

/**
 * Represents a standardised ingredient used by the dietary rule engine.
 *
 * @author YangMaowei & Amelia
 */
public record Ingredient(
        String ingredientName,
        String parentAllergen, // nullable
        String rootAllergen, // nullable
        boolean chemicalAlias
) {

    public Ingredient {
        Objects.requireNonNull(ingredientName, "ingredientName");
    }

    /** Convenience factory for hierarchy-only results coming from the DB. */
    public static Ingredient ofHierarchy(String name, String parent, String root) {
        return new Ingredient(name, parent, root, false);
    }
}
