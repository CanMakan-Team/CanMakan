package com.canmakan.backend.product.verdict;

/**
 * Boundary the verdict engine calls to resolve an ingredient that has no known
 * root allergen. Real implementations live in {@code knowledgebase} (alias /
 * E-number / allergen-relationship lookup) and/or the agentic-ai layer; the
 * engine stays decoupled from both.
 *
 * @author XieHuayuan
 */
public interface IngredientResolver {

    /**
     * Resolve one ingredient name.
     *
     * @param ingredientName the ingredient as seen on the label
     * @return a tri-state {@link IngredientResolution}: a root allergen, a
     *         recognised non-allergen additive, or unknown.
     */
    IngredientResolution resolve(String ingredientName);
}
