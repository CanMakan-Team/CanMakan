package com.canmakan.backend.product.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.canmakan.backend.knowledgebase.mcp.server.AllergenRelationshipLookupFallback;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the product-name allergen fallback parser (no external calls).
 *
 * @author XieHuayuan
 */
@DisplayName("UC3: ProductNameAllergenLookup web-fallback parsing")
@ExtendWith(MockitoExtension.class)
class ProductNameAllergenLookupTest {

    @Mock private AllergenRelationshipLookupFallback externalSearch;

    private ProductNameAllergenLookup lookup() {
        return new ProductNameAllergenLookup(externalSearch);
    }

    @Test
    void mapsAllergenKeywordsInTheAnswerToRoots() {
        when(externalSearch.searchProductAllergens("Peanut Butter"))
            .thenReturn("This product contains PEANUT and traces of MILK.");

        List<Ingredient> result = lookup().lookupByProductName("Peanut Butter");

        List<String> roots = result.stream().map(Ingredient::rootAllergen).toList();
        assertTrue(roots.contains("PEANUT"));
        assertTrue(roots.contains("DAIRY"));
        assertEquals(2, result.size());
    }

    @Test
    void deduplicatesByRoot() {
        when(externalSearch.searchProductAllergens("Cheese"))
            .thenReturn("Contains MILK and DAIRY.");

        List<Ingredient> result = lookup().lookupByProductName("Cheese");

        assertEquals(1, result.size());
        assertEquals("DAIRY", result.get(0).rootAllergen());
    }

    @Test
    void returnsEmptyWhenProviderReturnsNothing() {
        when(externalSearch.searchProductAllergens("Mystery")).thenReturn("");
        assertTrue(lookup().lookupByProductName("Mystery").isEmpty());
    }
}
