package com.canmakan.backend.product.verdict;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Resolve several ingredient labels in one call. Knowledge-backed implementations can
     * override this to share expensive lookups (for example a single allergen-relationship
     * query for the whole label) instead of one round trip per ingredient.
     *
     * <p>The returned map is keyed by the input label; blank or {@code null} names and
     * duplicates are skipped. The default simply loops over {@link #resolve(String)}.
     *
     * @param ingredientNames the labels to resolve (may be empty or {@code null})
     * @return each resolvable label mapped to its {@link IngredientResolution}
     */
    default Map<String, IngredientResolution> resolveAll(List<String> ingredientNames) {
        return resolveAll(ingredientNames, true);
    }

    /**
     * Same as {@link #resolveAll(List)} with an explicit external-search switch.
     * Catalog alternative checks pass {@code false} so Tavily is not called per candidate.
     */
    default Map<String, IngredientResolution> resolveAll(
            List<String> ingredientNames, boolean allowExternalSearch) {
        Map<String, IngredientResolution> resolutions = new LinkedHashMap<>();
        if (ingredientNames == null) {
            return resolutions;
        }
        for (String name : ingredientNames) {
            if (name == null || name.isBlank() || resolutions.containsKey(name)) {
                continue;
            }
            resolutions.put(name, resolve(name));
        }
        return resolutions;
    }

    /** @return the resolved root allergen (e.g. "DAIRY"), or {@code null} if none. */
    String resolveRootAllergen(String ingredientName);
}
