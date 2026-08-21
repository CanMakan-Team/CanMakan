package com.canmakan.backend.product.recommendation.ranking;

import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfile;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfiles;

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
    void wheatFlourRanksTaggedBrownRiceFlourAmongTopSubstitutes() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("9555064500016");
        source.setProductName("Superfine Wheat Flour");
        source.setBrand("Baker Choice");
        source.setMainCategoryEn("Wheat flours");
        source.setCategoryTags(
                "en:plant-based-foods-and-beverages,en:plant-based-foods,en:cereals-and-potatoes,"
                        + "en:cereals-and-their-products,en:flours,en:cereal-flours,en:wheat-flours");
        source.setIngredientsText("Wheat Flour, Iron, Niacin, Vitamin B2, Vitamin B1, Folic Acid");
        source.setAllergens("en:gluten");

        CatalogProduct brownRiceFlour = gfFlour(
                "8887501030642",
                "Organic Brown Rice Flour",
                "Brown Rice Flour",
                "Organic Brown Rice",
                "en:no-gluten,en:gluten-free,en:gluten-free-flour");
        CatalogProduct coconutFlour = gfFlour(
                "8888263533730",
                "Coconut Flour",
                "Dried coconut flour",
                "Organic Coconut",
                "en:plant-based-foods-and-beverages,en:plant-based-foods,en:dried-coconut-flour,"
                        + "en:no-gluten,en:gluten-free,en:gluten-free-flour");
        CatalogProduct cornFlour = gfFlour(
                "8888030023662",
                "Corn Flour",
                "Corn starch",
                "Corn flour",
                "en:plant-based-foods-and-beverages,en:plant-based-foods,en:corn-starch,"
                        + "en:no-gluten,en:gluten-free,en:gluten-free-flour");
        CatalogProduct flyingManCorn = gfFlour(
                "8888231120016",
                "Corn Flour",
                "Corn Flour",
                "Corn Flour",
                "en:no-gluten,en:gluten-free,en:gluten-free-flour");
        CatalogProduct buckwheat = gfFlour(
                "8887501030697",
                "Organic Buckwheat Flour",
                "Buckwheat Flour",
                "Buckwheat Flour",
                "en:no-gluten,en:gluten-free,en:gluten-free-flour");
        CatalogProduct amaranth = gfFlour(
                "8906055442630",
                "Organic Amaranath Flour",
                "Amaranth flour",
                "Organic Amaranth (Rajgira) Seeds",
                "en:no-gluten,en:gluten-free,en:gluten-free-flour");

        SubstituteDiscoveryProfile wheatProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Wheat flours").orElseThrow();
        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(coconutFlour, cornFlour, flyingManCorn, buckwheat, amaranth, brownRiceFlour),
                List.of(new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)),
                Set.of(),
                wheatProfile);

        assertEquals(6, ranked.size());
        int brownRiceFlourRank = ranked.stream()
                .map(alternative -> alternative.product().getBarcode())
                .toList()
                .indexOf("8887501030642");
        assertTrue(brownRiceFlourRank >= 0 && brownRiceFlourRank < ranked.size() - 1,
                "brown rice flour should outrank at least the weakest candidate, was at rank " + brownRiceFlourRank);
        assertEquals("ml_similarity", ranked.get(brownRiceFlourRank).matchReason());
    }

    @Test
    void nutButterDomainBoostRanksCashewAboveTahini() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("8888260007616");
        source.setProductName("Peanut Butter Crunchy");
        source.setMainCategoryEn("Peanut butters");
        source.setCategoryTags("en:peanut-butters,en:oilseed-purees");
        source.setAllergens("en:peanuts");

        CatalogProduct cashew = new CatalogProduct();
        cashew.setBarcode("95539553");
        cashew.setProductName("Organic Cashew Butter");
        cashew.setMainCategoryEn("Mixed nut butters");
        cashew.setCategoryTags("en:oilseed-purees,en:nut-butters");

        CatalogProduct tahini = new CatalogProduct();
        tahini.setBarcode("8888536703136");
        tahini.setProductName("Organic Tahini");
        tahini.setMainCategoryEn("White tahini");
        tahini.setCategoryTags("en:oilseed-purees,en:tahini");

        SubstituteDiscoveryProfile peanutProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Peanut butters").orElseThrow();

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(tahini, cashew),
                List.of(new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)),
                Set.of(),
                peanutProfile);

        assertEquals("95539553", ranked.getFirst().product().getBarcode());
    }

    @Test
    void packSizeBoostPrefersMatchingVolumeForMilkSubstitutes() {
        SubstituteDiscoveryProfile freshMilksProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Fresh milks").orElseThrow();
        CatalogProduct source = farmhouseFreshMilk();
        source.setQuantity("1 l");
        CatalogProduct oneLitreSoy = plantMilk(
                "8850025000521",
                "Soya Milk",
                "Home Soy",
                "Soy-based drinks",
                "Soy milk 99.6%",
                "en:dairy-substitutes,en:milk-substitutes,en:soy-based-drinks");
        oneLitreSoy.setQuantity("1 Litre");
        CatalogProduct smallSoy = plantMilk(
                "small",
                "Soya Milk Small",
                "Home Soy",
                "Soy-based drinks",
                "Soy milk 99.6%",
                "en:dairy-substitutes,en:milk-substitutes,en:soy-based-drinks");
        smallSoy.setQuantity("375 ml");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(smallSoy, oneLitreSoy),
                List.of(),
                Set.of(),
                freshMilksProfile);

        assertEquals("8850025000521", ranked.getFirst().product().getBarcode());
        assertEquals("ml_pack_size_match", ranked.getFirst().matchReason());
    }

    @Test
    void detectsSparseSourceWhenIngredientsDuplicateCategory() {
        ProductFeatureEncoder encoder = new ProductFeatureEncoder(
                new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), ""));
        assertTrue(encoder.isSparseSource(farmhouseFreshMilk()));
        CatalogProduct peanutButter = new CatalogProduct();
        peanutButter.setProductName("Crunchy Peanut Butter");
        peanutButter.setAllergens("en:peanuts");
        double full = encoder.encode(peanutButter).getOrDefault("peanut", 0.0);
        double query = encoder.encodeQuery(peanutButter).getOrDefault("peanut", 0.0);
        assertTrue(full > 0.0);
        assertTrue(query < full);
    }

    @Test
    void priorSafeAndNullRulesCoverMatchReasonsAndNutritionGuard() {
        CatalogProduct source = plantMilkWithNutrition(
                "100",
                "Almond Plant Drink",
                "Almond-based drinks",
                "Filtered water, almonds",
                "en:almond-based-drinks",
                new BigDecimal("4.0"),
                new BigDecimal("0.05"));
        CatalogProduct priorSafe = plantMilk(
                "safe",
                "Prior Safe Soy",
                "Home Soy",
                "Soy-based drinks",
                "Soy milk",
                "en:soy-based-drinks");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(priorSafe),
                null,
                Set.of("safe"));

        assertEquals("ml_prior_safe_scan", ranked.getFirst().matchReason());
    }

    @Test
    void cookingCreamDeprioritizeLowersScore() {
        SubstituteDiscoveryProfile freshMilksProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Fresh milks").orElseThrow();
        CatalogProduct source = farmhouseFreshMilk();
        CatalogProduct drink = plantMilk(
                "drink",
                "Soya Milk",
                "Home Soy",
                "Soy-based drinks",
                "Soy milk",
                "en:dairy-substitutes,en:milk-substitutes,en:soy-based-drinks");
        CatalogProduct cookingCream = plantMilk(
                "cook",
                "Soy Cooking Cream",
                "Home Soy",
                "Plant-based creams for cooking",
                "Soy",
                "en:plant-based-creams-for-cooking");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(cookingCream, drink),
                List.of(),
                Set.of(),
                freshMilksProfile);

        assertEquals("drink", ranked.getFirst().product().getBarcode());
    }

    @Test
    void nutritionSimilarityReturnsZeroWhenNutritionDisappearsAfterPairCheck() {
        Nutrition complete = new Nutrition(
                new BigDecimal("4.0"), new BigDecimal("0.05"), null, null, null, null);
        Nutrition incomplete = new Nutrition(null, null, null, null, null, null);
        CatalogProduct source = new FlipNutritionProduct("src", complete, incomplete);
        CatalogProduct candidate = new FlipNutritionProduct("cand", complete, incomplete);

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                source,
                List.of(candidate),
                List.of(new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)),
                Set.of());

        assertEquals(1, ranked.size());
        assertEquals("ml_nutrition_match", ranked.getFirst().matchReason());
    }

    private static final class FlipNutritionProduct extends CatalogProduct {
        private final Nutrition first;
        private final Nutrition later;
        private int toNutritionCalls;

        private FlipNutritionProduct(String barcode, Nutrition first, Nutrition later) {
            setBarcode(barcode);
            setProductName("Flip Nutrition");
            setMainCategoryEn("Almond-based drinks");
            setIngredientsText("Filtered water, almonds");
            setCategoryTags("en:almond-based-drinks");
            this.first = first;
            this.later = later;
        }

        @Override
        public Nutrition toNutrition() {
            toNutritionCalls++;
            return toNutritionCalls <= 2 ? first : later;
        }
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

    private static CatalogProduct gfFlour(
            String barcode,
            String name,
            String category,
            String ingredients,
            String tags) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setMainCategoryEn(category);
        product.setIngredientsText(ingredients);
        product.setCategoryTags(tags);
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
