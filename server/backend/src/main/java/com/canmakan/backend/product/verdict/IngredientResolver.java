package com.canmakan.backend.product.verdict;

/**
 * Boundary the verdict engine calls when an ingredient has no known root allergen.
 * Real implementations live in {@code knowledgebase} (alias / E-number lookup) and/or
 * {@code server/agentic-ai} (LLM reasoning); the engine stays decoupled from both.
 *
 * @author XieHuayuan
 */
public interface IngredientResolver {

    /** @return the resolved root allergen (e.g. "DAIRY"), or {@code null} if still unknown. */
    String resolveRootAllergen(String ingredientName);
}
