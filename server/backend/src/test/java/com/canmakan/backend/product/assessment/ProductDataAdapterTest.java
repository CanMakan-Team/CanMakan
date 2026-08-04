package com.canmakan.backend.product.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.verdict.ProductData;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests deterministic product lookup adaptation.
 *
 * @author YangMaowei
 */
class ProductDataAdapterTest {

    private final ProductDataAdapter adapter = new ProductDataAdapter();

    @Test
    void mapsAllVerdictFieldsAndNormalizesLabels() {
        Ingredient ingredient = new Ingredient("Oats", null, null, false);
        Nutrition nutrition = new Nutrition(
                BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, null
        );
        ProductLookupResult source = new ProductLookupResult(
                "123",
                "Oat Bar",
                "food",
                List.of(ingredient),
                "Oats",
                " EN:HALAL, ,Organic,0 ",
                nutrition,
                false
        );

        ProductData result = adapter.toProductData(source);

        assertEquals("123", result.barcode());
        assertEquals(List.of(ingredient), result.ingredients());
        assertEquals("Oats", result.ingredientsText());
        assertEquals(List.of("en:halal", "organic", "0"), result.labelTags());
        assertSame(nutrition, result.nutrition());
        assertEquals(BigDecimal.ZERO, result.nutrition().sugarsPer100g());
        assertNull(result.nutrition().fatPer100g());
        assertFalse(result.dataComplete());
        assertThrows(UnsupportedOperationException.class, () -> result.labelTags().add("new"));
    }

    @Test
    void nullAndBlankLabelsBecomeImmutableEmptyList() {
        for (String labels : new String[]{null, "", "   "}) {
            ProductLookupResult source = new ProductLookupResult(
                    "123", List.of(), null, labels, null, false
            );
            ProductData result = adapter.toProductData(source);
            assertEquals(List.of(), result.labelTags());
            assertThrows(UnsupportedOperationException.class, () -> result.labelTags().add("new"));
        }
    }

    @Test
    void rejectsNullSource() {
        assertThrows(NullPointerException.class, () -> adapter.toProductData(null));
    }
}
