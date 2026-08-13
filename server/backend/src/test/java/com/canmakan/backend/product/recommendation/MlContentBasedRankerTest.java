package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5 Tier C: MlContentBasedRanker")
class MlContentBasedRankerTest {

    private MlContentBasedRanker ranker;

    @BeforeEach
    void setUp() {
        ProductFeatureVectorStore vectorStore = new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), "");
        ranker = new MlContentBasedRanker(new ProductFeatureEncoder(vectorStore));
    }

    @Test
    @DisplayName("profile 3 + Farmhouse Fresh Milk: unsweetened plant milks rank above Oatly")
    void ranksUnsweetenedPlantMilksFirstForEmilyProfile() {
        CatalogProduct source = farmhouseFreshMilk();
        CatalogProduct unsweetenedSoy = plantMilk(
                "8850025000521",
                "Soya Milk Unsweetened",
                "Home Soy",
                "Unsweetened plain soy-based drinks",
                "Soy milk 99.6%, Calcium Carbonate",
                "en:dairy-substitutes,en:milk-substitutes,en:soy-based-drinks,en:unsweetened-plain-soy-based-drinks");
        CatalogProduct unsweetenedAlmond = plantMilk(
                "8850025060105",
                "Almond Milk Drink Unsweetened",
                "UFC Velvet",
                "Almond-based drinks",
                "Filtered water, Whole almonds 3%, Natural marine calcium",
                "en:dairy-substitutes,en:milk-substitutes,en:almond-based-drinks");
        CatalogProduct oatly = plantMilk(
                "7394376618253",
                "Oatly barista edition",
                "Oatly",
                "Oat-based drinks",
                "water, oats (10%), rapeseed oil, dipotassium phosphate",
                "en:dairy-substitutes,en:milk-substitutes,en:oat-based-drinks");

        List<RestrictionRule> emilyRules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE),
                new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID),
                new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
        );

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(oatly, unsweetenedAlmond, unsweetenedSoy),
                emilyRules,
                Set.of());

        assertEquals(3, ranked.size());
        assertTrue(ranked.getFirst().product().getProductName().toLowerCase().contains("unsweetened"));
        assertEquals("ml_unsweetened_substitute", ranked.getFirst().matchReason());
        assertTrue(ranked.stream().anyMatch(r -> "7394376618253".equals(r.product().getBarcode())));
    }

    @Test
    @DisplayName("nutrition data present: closer sugars/sodium ranks above higher-sugar peer")
    void ranksByNutritionSimilarityWhenDataPresent() {
        CatalogProduct source = plantMilkWithNutrition(
                "100",
                "Almond Plant Drink",
                "Almond-based drinks",
                "Filtered water, almonds",
                "en:almond-based-drinks",
                new BigDecimal("4.0"),
                new BigDecimal("0.05"));
        CatalogProduct highSugarPeer = plantMilkWithNutrition(
                "200",
                "Almond Plant Drink",
                "Almond-based drinks",
                "Filtered water, almonds",
                "en:almond-based-drinks",
                new BigDecimal("18.0"),
                new BigDecimal("0.05"));
        CatalogProduct nutritionPeer = plantMilkWithNutrition(
                "300",
                "Almond Plant Drink",
                "Almond-based drinks",
                "Filtered water, almonds",
                "en:almond-based-drinks",
                new BigDecimal("4.2"),
                new BigDecimal("0.06"));

        List<RestrictionRule> lowSugarRules = List.of(
                new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
        );

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(highSugarPeer, nutritionPeer),
                lowSugarRules,
                Set.of());

        assertEquals("300", ranked.getFirst().product().getBarcode());
        assertEquals("ml_nutrition_match", ranked.getFirst().matchReason());
    }

    @Test
    void detectsSparseSourceWhenIngredientsDuplicateCategory() {
        ProductFeatureEncoder encoder = new ProductFeatureEncoder(
                new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), ""));
        assertTrue(encoder.isSparseSource(farmhouseFreshMilk()));
    }

    private static CatalogProduct farmhouseFreshMilk() {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode("8888200602857");
        product.setProductName("Farmhouse Fresh Milk");
        product.setBrand("Farmhouse");
        product.setMainCategoryEn("Fresh milks");
        product.setCategoryTags("en:dairies,en:milks,en:fresh-milks");
        product.setIngredientsText("Fresh milks");
        product.setAllergens("en:milk");
        return product;
    }

    private static CatalogProduct plantMilk(
            String barcode,
            String name,
            String brand,
            String category,
            String ingredients,
            String tags) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setBrand(brand);
        product.setMainCategoryEn(category);
        product.setIngredientsText(ingredients);
        product.setCategoryTags(tags);
        return product;
    }

    private static CatalogProduct plantMilkWithNutrition(
            String barcode,
            String name,
            String category,
            String ingredients,
            String tags,
            BigDecimal sugars100g,
            BigDecimal sodium100g) {
        CatalogProduct product = plantMilk(barcode, name, "Test Brand", category, ingredients, tags);
        product.setSugars100g(sugars100g);
        product.setSodium100g(sodium100g);
        return product;
    }
}
