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
    @DisplayName("matches plain milk and unambiguous dairy words")
    void matchesDairy() {
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Fresh Milk"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("100% Fresh Milk"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Skimmed Milk Powder"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Milk Solids"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Whey Powder"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Lactose"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Sodium Caseinate"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Clarified Butter (Ghee)"));
        assertEquals("DAIRY", AllergenKeywords.matchRoot("Greek Yoghurt"));
    }

    @Test
    @DisplayName("plant-based milks and yoghurts are not treated as dairy")
    void plantBasedSubstitutesAreNotDairy() {
        assertNull(AllergenKeywords.matchRoot("Almond Milk"));
        assertNull(AllergenKeywords.matchRoot("Soy Milk"));
        assertNull(AllergenKeywords.matchRoot("Soya Milk"));
        assertNull(AllergenKeywords.matchRoot("Coconut Milk"));
        assertNull(AllergenKeywords.matchRoot("Rice Milk"));
        assertNull(AllergenKeywords.matchRoot("Cashew Milk"));
        assertNull(AllergenKeywords.matchRoot("Soy Yoghurt"));
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
