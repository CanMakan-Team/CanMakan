package com.canmakan.backend.product.recommendation.catalog;

import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import com.canmakan.backend.product.recommendation.catalog.CatalogProductMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: CatalogProductMapper")
class CatalogProductMapperTest {

    private CatalogProductMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CatalogProductMapper();
    }

    @Test
    void mapsIngredientsLabelsTracesAllergensAndCompletenessFlag() {
        CatalogProduct product = baseProduct();
        product.setIngredientsText("Rice flour, Salt, Sugar");
        product.setLabelsTags(" en:halal, en:no-gluten, en:halal ");
        product.setAllergens("en:milk");
        product.setTracesTags("en:milk, en:nuts");

        var productData = mapper.toProductData(product);

        assertEquals("9315090200706", productData.barcode());
        assertEquals("Rice flour, Salt, Sugar", productData.ingredientsText());
        assertEquals(3, productData.ingredients().size());
        assertEquals("Rice flour", productData.ingredients().get(0).ingredientName());
        assertEquals(List.of("en:halal", "en:no-gluten", "en:milk"), productData.labelTags());
        assertEquals(List.of("en:milk", "en:nuts"), productData.tracesTags());
        assertTrue(productData.dataComplete());
    }

    @Test
    void keepsCommasInsideParentheticalIngredientLists() {
        CatalogProduct product = baseProduct();
        product.setIngredientsText("Oyster Extract (Oysters, Water, Salt), Sugar, Modified Corn Starch");

        var productData = mapper.toProductData(product);

        assertEquals(3, productData.ingredients().size());
        assertEquals("Oyster Extract (Oysters, Water, Salt)", productData.ingredients().get(0).ingredientName());
        assertEquals("Sugar", productData.ingredients().get(1).ingredientName());
        assertEquals("Modified Corn Starch", productData.ingredients().get(2).ingredientName());
    }

    @Test
    void emptyIngredientsMarksDataIncomplete() {
        CatalogProduct product = baseProduct();
        product.setIngredientsText("   ");

        var productData = mapper.toProductData(product);

        assertTrue(productData.ingredients().isEmpty());
        assertFalse(productData.dataComplete());
    }

    @Test
    void usesNutritionFallbacksFromCatalogProduct() {
        CatalogProduct product = baseProduct();
        product.setIngredientsText("Water, Salt");
        product.setSugars100g(null);
        product.setAddedSugars100g(new BigDecimal("4.5"));
        product.setSodium100g(null);
        product.setSalt100g(new BigDecimal("1.0"));

        var productData = mapper.toProductData(product);

        assertEquals(new BigDecimal("4.5"), productData.nutrition().sugarsPer100g());
        assertEquals(0, productData.nutrition().sodiumPer100g().compareTo(new BigDecimal("0.4")));
    }

    @Test
    void confirmedZeroSugarIsNotReplacedByAddedSugarsFallback() {
        CatalogProduct product = baseProduct();
        product.setIngredientsText("Water");
        product.setSugars100g(BigDecimal.ZERO);
        product.setAddedSugars100g(new BigDecimal("2.0"));

        var productData = mapper.toProductData(product);

        assertEquals(BigDecimal.ZERO, productData.nutrition().sugarsPer100g());
    }

    private static CatalogProduct baseProduct() {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode("9315090200706");
        product.setProductName("Ancient grain flakes");
        product.setBrand("Freedom Foods");
        product.setMainCategoryEn("Breakfast cereals");
        return product;
    }
}
