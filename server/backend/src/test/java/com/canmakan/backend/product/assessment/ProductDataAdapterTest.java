package com.canmakan.backend.product.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.verdict.ProductData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests source-neutral product lookup adaptation into the verdict contract.
 *
 * @author YangMaowei
 */
class ProductDataAdapterTest {

    private BarcodeValidationClient barcodeValidationClient;
    private ProductDataAdapter adapter;

    @BeforeEach
    void setUp() {
        barcodeValidationClient = mock(BarcodeValidationClient.class);
        adapter = new ProductDataAdapter(barcodeValidationClient);
    }

    @Test
    void mapsCompleteProductDataWithoutInterpretingSourceValues() {
        Ingredient mapped = new Ingredient("Milk", "Milk Derivatives", "DAIRY", false);
        Ingredient unmapped = new Ingredient("Unmapped Additive", null, null, false);
        Nutrition nutrition = new Nutrition(
                new BigDecimal("99.9"),
                BigDecimal.ZERO,
                new BigDecimal("1.2"),
                new BigDecimal("3.4"),
                new BigDecimal("5.6"),
                new BigDecimal("789.0")
        );
        ProductLookupResult result = new ProductLookupResult(
                "8888888888888",
                "Test Product",
                "food",
                List.of(mapped, unmapped),
                "Milk, unmapped additive",
                " en:halal, Vegan, en:halal, ,VEGAN ",
                List.of("en:milk", "en:nuts"),
                nutrition,
                true
        );

        ProductData productData = adapter.toProductData(result);

        assertEquals("8888888888888", productData.barcode());
        assertEquals(List.of(mapped, unmapped), productData.ingredients());
        assertEquals("Milk, unmapped additive", productData.ingredientsText());
        assertEquals(List.of("en:halal", "Vegan", "VEGAN"), productData.labelTags());
        assertEquals(List.of("en:milk", "en:nuts"), productData.tracesTags());
        assertSame(nutrition, productData.nutrition());
        assertTrue(productData.dataComplete());
    }

    @Test
    void preservesMissingNutritionValuesAndConfirmedZero() {
        Nutrition nutrition = new Nutrition(
                null,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null
        );
        ProductLookupResult result = lookupResult(List.of(), null, nutrition, true);

        ProductData productData = adapter.toProductData(result);

        assertEquals(List.of(), productData.labelTags());
        assertNull(productData.nutrition().sugarsPer100g());
        assertEquals(BigDecimal.ZERO, productData.nutrition().sodiumPer100g());
        assertNull(productData.nutrition().transFatPer100g());
        assertNull(productData.nutrition().saturatedFatPer100g());
        assertNull(productData.nutrition().fatPer100g());
        assertNull(productData.nutrition().energyKcalPer100g());
    }

    @Test
    void preservesIncompleteIngredientDataFlag() {
        ProductData productData = adapter.toProductData(
                lookupResult(List.of(), "en:vegan", null, false)
        );

        assertFalse(productData.dataComplete());
    }

    @Test
    void returnsSafeCollectionsWithoutChangingTheSource() {
        Ingredient ingredient = new Ingredient("Unknown Ingredient", null, null, false);
        List<Ingredient> mutableIngredients = new ArrayList<>(List.of(ingredient));
        ProductLookupResult result = lookupResult(
                mutableIngredients,
                " en:halal, en:halal ",
                null,
                true
        );

        ProductData productData = adapter.toProductData(result);
        mutableIngredients.clear();

        assertEquals(List.of(ingredient), result.ingredients());
        assertEquals(" en:halal, en:halal ", result.labelTags());
        assertEquals(List.of(ingredient), productData.ingredients());
        assertEquals(List.of("en:halal"), productData.labelTags());
        assertThrows(
                UnsupportedOperationException.class,
                () -> productData.ingredients().add(ingredient)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> productData.labelTags().add("en:vegan")
        );
    }

    @Test
    void rejectsNullLookupResult() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> adapter.toProductData((ProductLookupResult) null)
        );

        assertEquals("result must not be null", exception.getMessage());
    }

    @Test
    void barcodeEntryPointFetchesAndMapsProduct() {
        ProductLookupResult result = lookupResult(List.of(), "en:halal", null, true);
        when(barcodeValidationClient.fetchProduct("1234567890123")).thenReturn(result);

        ProductData productData = adapter.toProductData("1234567890123");

        assertEquals("1234567890123", productData.barcode());
        assertEquals(List.of("en:halal"), productData.labelTags());
        verify(barcodeValidationClient).fetchProduct("1234567890123");
    }

    @Test
    void lookupDelegatesToBarcodeClient() {
        ProductLookupResult result = lookupResult(List.of(), null, null, false);
        when(barcodeValidationClient.fetchProduct("1234567890123")).thenReturn(result);

        assertSame(result, adapter.lookup("1234567890123"));
    }

    @Test
    void rejoinsIngredientLeavesSplitInsideParentheses() {
        ProductLookupResult result = lookupResult(
            List.of(
                new Ingredient("Oyster Extract (Oysters", null, null, false),
                new Ingredient("Water", null, null, false),
                new Ingredient("Salt)", null, null, false),
                new Ingredient("Sugar", null, null, false)
            ),
            "",
            null,
            true
        );

        ProductData productData = adapter.toProductData(result);

        assertEquals(2, productData.ingredients().size());
        assertEquals(
            "Oyster Extract (Oysters, Water, Salt)",
            productData.ingredients().get(0).ingredientName());
        assertEquals("Sugar", productData.ingredients().get(1).ingredientName());
    }

    private static ProductLookupResult lookupResult(
            List<Ingredient> ingredients,
            String labelTags,
            Nutrition nutrition,
            boolean ingredientDataComplete
    ) {
        return new ProductLookupResult(
                "1234567890123",
                "Test Product",
                "food",
                ingredients,
                "Source ingredient text",
                labelTags,
                List.of(),
                nutrition,
                ingredientDataComplete
        );
    }
}
