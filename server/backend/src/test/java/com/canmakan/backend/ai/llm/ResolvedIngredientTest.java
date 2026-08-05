package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests ingredient evidence validation without applying deterministic thresholds.
 *
 * @author YangMaowei
 */
class ResolvedIngredientTest {

    @Test
    void constructsResolvedAndUnresolvedIngredients() {
        ResolvedIngredient resolved = new ResolvedIngredient(" Milk ", " DAIRY ", 0.85);
        ResolvedIngredient unresolved = new ResolvedIngredient("Mystery additive", null, 0.2);

        assertEquals("Milk", resolved.ingredientName());
        assertEquals("DAIRY", resolved.rootAllergen());
        assertEquals(0.85, resolved.confidence());
        assertNull(unresolved.rootAllergen());
    }

    @Test
    void acceptsConfidenceBoundaries() {
        assertEquals(0.0, new ResolvedIngredient("A", null, 0.0).confidence());
        assertEquals(1.0, new ResolvedIngredient("B", "DAIRY", 1.0).confidence());
    }

    @Test
    void rejectsNullAndBlankIngredientName() {
        assertThrows(
                NullPointerException.class,
                () -> new ResolvedIngredient(null, null, 0.5)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedIngredient("  ", null, 0.5)
        );
    }

    @Test
    void rejectsBlankRootAllergen() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedIngredient("Milk", "  ", 0.5)
        );
    }

    @Test
    void rejectsConfidenceOutsideRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedIngredient("A", null, -0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedIngredient("B", null, 1.01)
        );
    }

    @Test
    void rejectsNonFiniteConfidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedIngredient("A", null, Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedIngredient("B", null, Double.POSITIVE_INFINITY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedIngredient("C", null, Double.NEGATIVE_INFINITY)
        );
    }
}
