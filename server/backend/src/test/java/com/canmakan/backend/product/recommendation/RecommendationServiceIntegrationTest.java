package com.canmakan.backend.product.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.dietaryprofile.service.RestrictionRuleLoader;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end UC5 recommendation against the seeded catalog and household profiles.
 *
 * <p>Uses the same MySQL-backed {@code @SpringBootTest} setup as
 * {@link com.canmakan.backend.product.history.ScanHistoryRepositoryTest}: seed data
 * from {@code 01_products.sql} and {@code 05_household_dietary_data.sql}.
 */
@SpringBootTest(properties = "canmakan.recommendation.ml.ranker-url=")
@Transactional
@DisplayName("UC5: RecommendationService integration")
class RecommendationServiceIntegrationTest {

    /** Emily Tan — dairy intolerance, peanut strict avoid, low sugar (05_household_dietary_data.sql). */
    private static final long PROFILE_EMILY_TAN = 3L;

    private static final String MAGNUM_MINI_CHOC_HAZELNUT = "8714100638415";

    /** Vegan coconut tub that declares {@code en:milk} and must not be suggested. */
    private static final String COCONUT_WITH_MILK_ALLERGEN = "0797776401192";

    /** Sarah Tan — gluten strict avoid, low sugar (05_household_dietary_data.sql). */
    private static final long PROFILE_SARAH_TAN = 1L;

    /** Michael Tan — low fat and low sodium preferences (05_household_dietary_data.sql). */
    private static final long PROFILE_MICHAEL_TAN = 2L;

    private static final String KNIFE_FISH_SAUCE = "8850581172007";

    /** Reduced-salt sauce in the Sauces category. */
    private static final String REDUCED_SALT_OYSTER_SAUCE = "0078895160482";

    /** No-salt-added tomato sauce tagged Low sodium sauces. */
    private static final String HUNT_NO_SALT_TOMATO_SAUCE = "12456419";

    /** Groceries mis-match previously suggested for fish sauce scans. */
    private static final String PREMIUM_FINE_SALT = "8888626031934";

    private static final String HI_CALCIUM_MILK_BREAD = "8888247111145";

    private static final String MILK_FLAVOR_BREAD = "6933352827077";

    private static final String SUNSHINE_WHOLE_GRAIN_BREAD = "8888010101014";

    /** GF sourdough with sparse nutrition and no ingredients_text. */
    private static final String GF_SOURDOUGH_7_SEED = "0667380799179";

    private static final String GF_PITA_BREAD = "8881300655228";

    /** Soya milk previously returned via spurious {@code en:milk-substitutes} inference. */
    private static final String HI_CALCIUM_SOYA_MILK = "8888030019566";

    private static final String HONEY_STARS = "4800361385046";

    /** Tagged GF breakfast cereal without oats. */
    private static final String ANCIENT_GRAIN_FLAKES = "9315090200706";

    /** Tagged GF breakfast cereal with oats — must never be suggested. */
    private static final Set<String> OAT_BREAKFAST_CEREAL_BARCODES = Set.of(
            "8886478600698",
            "8887143802515"
    );

    private static final String SINGLONG_PEANUT_BUTTER = "8888260007616";

    /** Tahini from the nut/seed butter substitute pool. */
    private static final String NATURE_GLORY_TAHINI = "8888536703136";

    /** Sparse cashew spread — no ingredients_text in catalog; must still be discoverable. */
    private static final String ORGANIC_CASHEW_BUTTER = "95539553";

    /** Legume paste miscategorised near spreads — must never substitute for peanut butter. */
    private static final String SALTED_SOYA_BEAN_PASTE = "8888256370632";

    /** Strawberry jam previously ranked above nut/seed butters via broad en:spreads pool. */
    private static final String STRAWBERRY_JAM = "0044936350150";

    /** Butter spread miscategorized in the coarse Dairies category. */
    private static final String LUXURY_DAIRY_SPREAD = "8888010320453";

    /** Magnolia fresh milk miscategorized as Dairies instead of Fresh milks. */
    private static final String MAGNOLIA_FRESH_MILK = "8888200602734";

    /** Unsweetened plant milk from the substitute pool. */
    private static final String HOME_SOY_UNSWEETENED = "8850025000521";

    /** Magnum peanut-butter ice cream bar (dairy + peanut source). */
    private static final String MAGNUM_PB_ICE_CREAM = "8712100857645";

    /** Baker Choice wheat flour source for Sarah. */
    private static final String BAKER_CHOICE_WHEAT_FLOUR = "9555064500016";

    /** Mis-tagged almond flour wrap — must not substitute for wheat flour. */
    private static final String ALMOND_FLOUR_WRAP = "8881300655204";

    /** Tagged GF rice flour substitute. */
    private static final String BROWN_RICE_FLOUR = "8887501030642";

    /** Farmhouse fresh milk demo source for Emily. */
    private static final String FARMHOUSE_FRESH_MILK = "8888200602857";

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private CatalogProductRepository catalogProductRepository;

    @Autowired
    private CatalogProductMapper catalogProductMapper;

    @Autowired
    private DietaryRuleEngine ruleEngine;

    @Autowired
    private AlternativeCandidateFilter alternativeCandidateFilter;

    @Autowired
    private RestrictionRuleLoader restrictionRuleLoader;

    @Autowired
    private AlternativeProductQueryService queryService;

    @Test
    @DisplayName("profile 1 gets multiple GF bread substitutes for Sunshine whole grain bread")
    void profile1GetsMultipleGlutenFreeBreadSubstitutesForSunshineBread() {
        CatalogProduct source = catalogProductRepository.findById(SUNSHINE_WHOLE_GRAIN_BREAD).orElseThrow();
        var rules = restrictionRuleLoader.load(PROFILE_SARAH_TAN);

        long sameCategoryAcceptable = queryService.findSameCategoryCandidates(source).stream()
                .filter(candidate -> alternativeCandidateFilter.isAcceptableAlternative(
                        rules,
                        ruleEngine.assessForRecommendation(
                                rules, catalogProductMapper.toProductData(candidate)),
                        candidate))
                .count();

        assertThat(sameCategoryAcceptable).isZero();

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_SARAH_TAN, SUNSHINE_WHOLE_GRAIN_BREAD, null));

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("sparse GF bread rows should be suggested alongside pita")
                .contains(GF_SOURDOUGH_7_SEED, GF_PITA_BREAD);
        assertThat(suggestedBarcodes.size()).isGreaterThanOrEqualTo(2);
        assertThat(suggestedBarcodes)
                .as("oat cereals mis-tagged as GF bread must stay excluded")
                .doesNotContain("8887143802515", "8887143802539");
    }

    @Test
    @DisplayName("profile 3 gets plant milk substitutes for Farmhouse fresh milk")
    void profile3GetsPlantMilkSubstitutesForFarmhouseFreshMilk() {
        CatalogProduct source = catalogProductRepository.findById(FARMHOUSE_FRESH_MILK).orElseThrow();
        var rules = restrictionRuleLoader.load(PROFILE_EMILY_TAN);
        var milkProfile = new SubstituteDiscoveryProfiles().forSourceProduct(source).orElseThrow();

        long tagPoolAcceptable = queryService.findSubstituteTagCandidates(source, milkProfile).stream()
                .filter(candidate -> alternativeCandidateFilter.isAcceptableAlternative(
                        rules,
                        ruleEngine.assessForRecommendation(
                                rules, catalogProductMapper.toProductData(candidate)),
                        candidate))
                .count();

        assertThat(tagPoolAcceptable)
                .as("milk-substitute tag pool should contain at least one acceptable plant milk")
                .isPositive();

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_EMILY_TAN, FARMHOUSE_FRESH_MILK, null));

        assertThat(response.sourceBarcode()).isEqualTo(FARMHOUSE_FRESH_MILK);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("unsweetened plant milks should substitute for fresh cow milk")
                .contains(HOME_SOY_UNSWEETENED);
        assertThat(suggestedBarcodes)
                .as("cow milk and dairy spreads must not appear")
                .doesNotContain(FARMHOUSE_FRESH_MILK, LUXURY_DAIRY_SPREAD);
    }

    @Test
    @DisplayName("profile 3 gets plant milk substitutes for Magnolia milk miscategorized as Dairies")
    void profile3GetsPlantMilkSubstitutesForMiscategorizedMagnoliaMilk() {
        CatalogProduct source = catalogProductRepository.findById(MAGNOLIA_FRESH_MILK).orElseThrow();
        var milkProfile = new SubstituteDiscoveryProfiles().forSourceProduct(source).orElseThrow();

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_EMILY_TAN, MAGNOLIA_FRESH_MILK, null));

        assertThat(response.sourceBarcode()).isEqualTo(MAGNOLIA_FRESH_MILK);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> plantMilkPool = queryService.findSubstituteTagCandidates(source, milkProfile).stream()
                .map(CatalogProduct::getBarcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("coarse Dairies category must not suggest butter spreads")
                .doesNotContain(LUXURY_DAIRY_SPREAD);
        assertThat(suggestedBarcodes)
                .as("all suggestions should come from the plant-milk tag pool")
                .isSubsetOf(plantMilkPool);
    }

    @Test
    @DisplayName("profile 3 gets dairy-free frozen desserts for Magnum peanut butter ice cream")
    void profile3GetsDairyFreeFrozenDessertsForMagnumPeanutButterIceCream() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_EMILY_TAN, MAGNUM_PB_ICE_CREAM, null));

        assertThat(response.sourceBarcode()).isEqualTo(MAGNUM_PB_ICE_CREAM);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("dairy-free frozen desserts from the ice-creams-and-sorbets substitute pool")
                .contains("0797776401178");
        assertThat(suggestedBarcodes)
                .as("other dairy Magnum bars must not substitute dairy ice cream scans")
                .doesNotContain("8714100638415", "8714100635650", "8000920500224");
        assertThat(suggestedBarcodes)
                .as("vegan coconut with declared milk allergen must be catalog-hardened out")
                .doesNotContain(COCONUT_WITH_MILK_ALLERGEN);
    }

    @Test
    @DisplayName("profile 1 gets GF flour substitutes without almond flour wraps")
    void profile1GetsGlutenFreeFlourSubstitutesWithoutWraps() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_SARAH_TAN, BAKER_CHOICE_WHEAT_FLOUR, null));

        assertThat(response.sourceBarcode()).isEqualTo(BAKER_CHOICE_WHEAT_FLOUR);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("wraps are not baking-flour substitutes even when name contains flour")
                .doesNotContain(ALMOND_FLOUR_WRAP);
        assertThat(suggestedBarcodes)
                .as("tagged GF flour substitutes should still appear")
                .contains(BROWN_RICE_FLOUR);
    }

    @Test
    @DisplayName("profile 3 gets dairy-free ice-cream substitutes for Magnum ice cream bar")
    void profile3GetsDairyFreeIceCreamSubstitutesForMagnumBar() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_EMILY_TAN, MAGNUM_MINI_CHOC_HAZELNUT, null));

        assertThat(response.sourceBarcode()).isEqualTo(MAGNUM_MINI_CHOC_HAZELNUT);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("dairy-free frozen desserts from the ice-creams-and-sorbets substitute pool")
                .contains("0797776401178");
        assertThat(suggestedBarcodes)
                .as("at least one tagged dairy-free frozen dessert")
                .isNotEmpty();

        assertThat(suggestedBarcodes)
                .as("vegan coconut with declared milk allergen must be catalog-hardened out")
                .doesNotContain(COCONUT_WITH_MILK_ALLERGEN);

        assertThat(suggestedBarcodes)
                .as("source product must never be suggested as its own alternative")
                .doesNotContain(MAGNUM_MINI_CHOC_HAZELNUT);
    }

    @Test
    @DisplayName("profile 1 does not get soya milk substitutes for milk bread scan")
    void profile1DoesNotGetSoyaMilkForMilkBreadScan() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_SARAH_TAN, HI_CALCIUM_MILK_BREAD, null));

        assertThat(response.sourceBarcode()).isEqualTo(HI_CALCIUM_MILK_BREAD);

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("milk bread must not expand into plant-based milk substitutes")
                .doesNotContain(HI_CALCIUM_SOYA_MILK);
    }

    @Test
    @DisplayName("profile 1 gets GF bread substitutes for lowercase breads category scan")
    void profile1GetsGlutenFreeBreadSubstitutesForMilkFlavorBread() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_SARAH_TAN, MILK_FLAVOR_BREAD, null));

        assertThat(response.sourceBarcode()).isEqualTo(MILK_FLAVOR_BREAD);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("lowercase breads category must route to GF bread pool, not flour")
                .containsAnyOf(GF_SOURDOUGH_7_SEED, GF_PITA_BREAD, "0697478426588", "9339423009064");

        assertThat(suggestedBarcodes)
                .as("wheat flour must not substitute for bread scans")
                .doesNotContain("8888231120016", "8888030023662", "8888263533730");
    }

    @Test
    @DisplayName("profile 1 gets oat-free GF breakfast cereal substitutes for Honey Stars")
    void profile1GetsGlutenFreeBreakfastCerealSubstitutesForHoneyStars() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_SARAH_TAN, HONEY_STARS, null));

        assertThat(response.sourceBarcode()).isEqualTo(HONEY_STARS);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("tagged GF breakfast cereal without oats")
                .containsAnyOf(ANCIENT_GRAIN_FLAKES, "0667380799766", "9346430000854");

        assertThat(suggestedBarcodes)
                .as("oat-containing tagged GF cereals must be excluded")
                .doesNotContainAnyElementsOf(OAT_BREAKFAST_CEREAL_BARCODES);

        assertThat(suggestedBarcodes)
                .as("mis-tagged chips and papadum must not appear as cereal substitutes")
                .doesNotContain("7750526000895", "9555243803167");

        assertThat(suggestedBarcodes)
                .as("wheat cereal must not expand into GF flour substitutes")
                .noneMatch(barcode -> response.alternatives().stream()
                        .filter(alt -> alt.barcode().equals(barcode))
                        .anyMatch(alt -> alt.productName() != null
                                && alt.productName().toLowerCase().contains("flour")));
    }

    @Test
    @DisplayName("profile 3 gets nut/seed butter substitutes for peanut butter scan")
    void profile3GetsNutButterSubstitutesForPeanutButterScan() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_EMILY_TAN, SINGLONG_PEANUT_BUTTER, null));

        assertThat(response.sourceBarcode()).isEqualTo(SINGLONG_PEANUT_BUTTER);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("nut/seed butter substitute pool should include tahini")
                .contains(NATURE_GLORY_TAHINI);

        assertThat(suggestedBarcodes)
                .as("sparse cashew butter rows without ingredients_text should still be suggested")
                .contains(ORGANIC_CASHEW_BUTTER);

        assertThat(suggestedBarcodes)
                .as("soybean paste is not a peanut-butter substitute")
                .doesNotContain(SALTED_SOYA_BEAN_PASTE);

        assertThat(suggestedBarcodes)
                .as("jams must not appear when peanut butter falls back to nut/seed butters")
                .doesNotContain(STRAWBERRY_JAM);

        assertThat(suggestedBarcodes)
                .as("source product must never be suggested as its own alternative")
                .doesNotContain(SINGLONG_PEANUT_BUTTER);
    }

    @Test
    @DisplayName("profile 2 gets low-sodium sauce substitutes for fish sauce scan")
    void profile2GetsLowSodiumSauceSubstitutesForFishSauceScan() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(PROFILE_MICHAEL_TAN, KNIFE_FISH_SAUCE, null));

        assertThat(response.sourceBarcode()).isEqualTo(KNIFE_FISH_SAUCE);
        assertThat(response.alternatives()).isNotEmpty();

        Set<String> suggestedBarcodes = response.alternatives().stream()
                .map(AlternativeProductDto::barcode)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(suggestedBarcodes)
                .as("low-sodium sauce substitutes should include reduced-salt or no-salt-added sauces")
                .containsAnyOf(REDUCED_SALT_OYSTER_SAUCE, HUNT_NO_SALT_TOMATO_SAUCE);

        assertThat(suggestedBarcodes)
                .as("random Groceries rows such as table salt must not substitute for fish sauce")
                .doesNotContain(PREMIUM_FINE_SALT);
    }
}
