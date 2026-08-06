package com.canmakan.backend.product.verdict;

/**
 * Outcome of resolving a raw ingredient label against the knowledge base.
 *
 * <ul>
 *   <li>{@link Kind#RESOLVED} — catalog/hierarchy mapped a root allergen (e.g. DAIRY)</li>
 *   <li>{@link Kind#KNOWN_SAFE} — ingredient is recognised but has no root allergen
 *       (e.g. Salt, Sugar); not treated as incomplete analysis</li>
 *   <li>{@link Kind#UNKNOWN} — no catalog hit; may warrant an UNRESOLVED warning</li>
 * </ul>
 *
 * @author Amelia
 */
public record IngredientResolution(
        Kind kind,
        String rootAllergen,
        String canonicalName,
        boolean chemicalAlias
) {

    public enum Kind {
        RESOLVED,
        KNOWN_SAFE,
        UNKNOWN
    }

    public static IngredientResolution resolved(String rootAllergen) {
        return resolved(rootAllergen, null, false);
    }

    public static IngredientResolution resolved(
            String rootAllergen,
            String canonicalName,
            boolean chemicalAlias
    ) {
        if (rootAllergen == null || rootAllergen.isBlank()) {
            throw new IllegalArgumentException("rootAllergen is required for RESOLVED");
        }
        return new IngredientResolution(
                Kind.RESOLVED,
                rootAllergen.trim(),
                blankToNull(canonicalName),
                chemicalAlias
        );
    }

    public static IngredientResolution knownSafe() {
        return new IngredientResolution(Kind.KNOWN_SAFE, null, null, false);
    }

    public static IngredientResolution unknown() {
        return new IngredientResolution(Kind.UNKNOWN, null, null, false);
    }

    public boolean isUnknown() {
        return kind == Kind.UNKNOWN;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
