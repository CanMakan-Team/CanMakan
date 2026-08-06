package com.canmakan.backend.product.verdict;

/**
 * Tri-state outcome of resolving one ingredient to a root allergen.
 *
 * <p>Lets the engine tell a <b>recognised additive that is not an allergen</b>
 * (e.g. {@code E341} tricalcium phosphate) apart from a <b>truly unknown</b>
 * ingredient. Only the latter degrades the verdict to WARNING; a known
 * non-allergen additive is treated as safely analysed.
 *
 * @author XieHuayuan
 */
public record IngredientResolution(Status status, String rootAllergen) {

    public enum Status {
        /** Resolved to a root allergen (see {@link #rootAllergen()}). */
        RESOLVED_ALLERGEN,
        /** Recognised ingredient/additive with no allergen link — safe to ignore. */
        KNOWN_NO_ALLERGEN,
        /** Could not be resolved — the engine treats it with caution (WARNING). */
        UNKNOWN
    }

    /** Resolved to a concrete root allergen (e.g. "DAIRY"). */
    public static IngredientResolution allergen(String rootAllergen) {
        return new IngredientResolution(Status.RESOLVED_ALLERGEN, rootAllergen);
    }

    /** Recognised additive/ingredient with no allergen link. */
    public static IngredientResolution knownNoAllergen() {
        return new IngredientResolution(Status.KNOWN_NO_ALLERGEN, null);
    }

    /** Could not be resolved. */
    public static IngredientResolution unknown() {
        return new IngredientResolution(Status.UNKNOWN, null);
    }
}
