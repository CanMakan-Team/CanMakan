package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic keyword allergen fallback.
 *
 * @author XieHuayuan
 */
@DisplayName("UC3: AllergenKeywords deterministic fallback")
class AllergenKeywordsTest {

    @Test
    void matchesGlutenGrainsInVerboseNames() {
        assertEquals("GLUTEN", AllergenKeywords.matchRoot("Enriched High Protein Wheat Flour"));
        assertEquals("GLUTEN", AllergenKeywords.matchRoot("Vital Wheat Gluten"));
        assertEquals("GLUTEN", AllergenKeywords.matchRoot("Oat Fibre"));
        assertEquals("GLUTEN", AllergenKeywords.matchRoot("Malt Extract"));
    }

    @Test
    void matchesPeanut() {
        assertEquals("PEANUT", AllergenKeywords.matchRoot("Roasted Peanuts"));
        assertEquals("PEANUT", AllergenKeywords.matchRoot("Groundnut Oil"));
    }

    @Test
    void doesNotMatchBenignOrLookalikeNames() {
        assertNull(AllergenKeywords.matchRoot("Purified Water"));
        assertNull(AllergenKeywords.matchRoot("Granulated Cane Sugar"));
        assertNull(AllergenKeywords.matchRoot("Vacuum Dried Salt"));
        assertNull(AllergenKeywords.matchRoot("Baker's Yeast"));
        assertNull(AllergenKeywords.matchRoot("Sorbitol"));
        assertNull(AllergenKeywords.matchRoot("Beta Carotene"));
        // Whole-word matching: "buckwheat" must not be read as "wheat".
        assertNull(AllergenKeywords.matchRoot("Buckwheat"));
        assertNull(AllergenKeywords.matchRoot(null));
    }
}
