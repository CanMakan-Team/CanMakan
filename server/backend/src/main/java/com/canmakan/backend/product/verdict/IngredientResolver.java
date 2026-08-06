package com.canmakan.backend.product.verdict;

/**
 * Boundary the verdict engine calls when an ingredient has no known root allergen.
 * Real implementations live in {@code knowledgebase} (alias / E-number lookup) and/or
 * {@code server/agentic-ai} (LLM reasoning); the engine stays decoupled from both.
 *
 * @author XieHuayuan
 * @author Amelia
 */
public interface IngredientResolver {

    /**
     * Resolve a raw ingredient label.
     *
     * <p>Default implementation wraps {@link #resolveRootAllergen}: a non-blank root is
     * {@link IngredientResolution.Kind#RESOLVED}, otherwise {@link IngredientResolution.Kind#UNKNOWN}.
     * Prefer overriding this in knowledge-backed implementations so catalog hits with no
     * root allergen can be reported as {@link IngredientResolution.Kind#KNOWN_SAFE}.
     */
    default IngredientResolution resolve(String ingredientName) {
        String root = resolveRootAllergen(ingredientName);
        if (root != null && !root.isBlank()) {
            return IngredientResolution.resolved(root);
        }
        return IngredientResolution.unknown();
    }

    /** @return the resolved root allergen (e.g. "DAIRY"), or {@code null} if none. */
    String resolveRootAllergen(String ingredientName);
}
