package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: AlternativeProductRanker")
class AlternativeProductRankerTest {

    private AlternativeProductRanker ranker;
    private SubstituteDiscoveryProfile freshMilksProfile;

    @BeforeEach
    void setUp() {
        ranker = new AlternativeProductRanker();
        freshMilksProfile = new SubstituteDiscoveryProfiles().forSourceCategory("Fresh milks").orElseThrow();
    }

    @Test
    void assignsPriorSafeScanReasonAndBoostCap() {
        CatalogProduct other = product("111", "Other cereal", null);
        CatalogProduct priorSafe = product("555", "Prior safe cereal", null);

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rankSameCategory(
                List.of(other, priorSafe),
                Set.of("555")
        );

        AlternativeProductRanker.RankedAlternative priorRanked = ranked.stream()
                .filter(result -> "555".equals(result.product().getBarcode()))
                .findFirst()
                .orElseThrow();

        assertEquals("prior_safe_scan", priorRanked.matchReason());
        assertEquals(new BigDecimal("0.99"), priorRanked.score());
    }

    @Test
    void usesCategoryMatchWhenNoPriorSafeHistory() {
        CatalogProduct first = product("111", "First", null);
        CatalogProduct second = product("222", "Second", null);

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rankSameCategory(
                List.of(first, second),
                Set.of()
        );

        assertEquals("category_match", ranked.get(0).matchReason());
        assertEquals("111", ranked.get(0).product().getBarcode());
        assertEquals(new BigDecimal("1.0"), ranked.get(0).score());
        assertEquals(new BigDecimal("0.99"), ranked.get(1).score());
    }

    @Test
    void oatDrinkOutranksCoconutCookingSubstitute() {
        CatalogProduct oatDrink = product(
                "7394376618253",
                "Oatly barista edition",
                "en:milk-substitutes,en:oat-based-drinks");
        CatalogProduct coconutCooking = product(
                "8850025071026",
                "Velvet coconut milk",
                "en:milk-substitutes,en:plant-based-creams-for-cooking,en:coconut-milks-and-creams");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rankSubstituteTags(
                List.of(coconutCooking, oatDrink),
                Set.of(),
                freshMilksProfile
        );

        assertEquals("7394376618253", ranked.get(0).product().getBarcode());
        assertEquals("substitute_category", ranked.get(0).matchReason());
        assertEquals(new BigDecimal("0.97"), ranked.get(0).score());

        assertEquals("8850025071026", ranked.get(1).product().getBarcode());
        assertEquals("substitute_category_cooking", ranked.get(1).matchReason());
        assertEquals(new BigDecimal("0.85"), ranked.get(1).score());
    }

    @Test
    void substituteTagRankingAppliesBeverageBoostWithoutCookingPenalty() {
        CatalogProduct soyDrink = product(
                "8850025000521",
                "Soya Milk Unsweetened",
                "en:milk-substitutes,en:soy-based-drinks");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rankSubstituteTags(
                List.of(soyDrink),
                Set.of(),
                freshMilksProfile
        );

        assertEquals("substitute_category", ranked.getFirst().matchReason());
        assertEquals(new BigDecimal("0.98"), ranked.getFirst().score());
    }

    @Test
    void tahiniOutranksNonBoostNutButterSubstituteForPeanutButterProfile() {
        SubstituteDiscoveryProfile peanutProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Peanut butters").orElseThrow();
        CatalogProduct genericOilseed = product(
                "999",
                "Generic oilseed spread",
                "en:oilseed-purees");
        CatalogProduct tahini = product(
                "8888536703136",
                "Organic Tahini (Unhulled)",
                "en:oilseed-purees,en:cereal-butters,en:tahini");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rankSubstituteTags(
                List.of(genericOilseed, tahini),
                Set.of(),
                peanutProfile
        );

        assertEquals("8888536703136", ranked.get(0).product().getBarcode());
        assertEquals("substitute_category", ranked.get(0).matchReason());
        assertEquals(new BigDecimal("0.97"), ranked.get(0).score());
    }

    @Test
    void nutButterOutranksTahiniForPeanutButterProfile() {
        SubstituteDiscoveryProfile peanutProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Peanut butters").orElseThrow();
        CatalogProduct cashewButter = product(
                "95539553",
                "Organic Cashew Butter",
                "en:oilseed-purees,en:nut-butters");
        CatalogProduct tahini = product(
                "8888536703136",
                "Organic Tahini (Unhulled)",
                "en:oilseed-purees,en:cereal-butters,en:tahini");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rankSubstituteTags(
                List.of(tahini, cashewButter),
                Set.of(),
                peanutProfile
        );

        assertEquals("95539553", ranked.get(0).product().getBarcode());
        assertEquals("substitute_category", ranked.get(0).matchReason());
    }

    private static CatalogProduct product(String barcode, String name, String categoryTags) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setMainCategoryEn("Breakfast cereals");
        product.setIngredientsText("Rice flour");
        product.setCategoryTags(categoryTags);
        return product;
    }
}
