package com.canmakan.backend.product.recommendation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Curated source-category profiles for Tier A tag-based substitute discovery.
 */
@Component
public class SubstituteDiscoveryProfiles {

    private static final SubstituteDiscoveryProfile FRESH_MILKS = new SubstituteDiscoveryProfile(
            List.of("en:milk-substitutes", "en:dairy-substitutes"),
            List.of(
                    "en:oat-based-drinks",
                    "en:soy-based-drinks",
                    "en:almond-based-drinks",
                    "en:unsweetened-plain-soy-based-drinks"
            ),
            List.of("en:plant-based-creams-for-cooking", "en:coconut-milks-and-creams")
    );

    private static final SubstituteDiscoveryProfile WHEAT_FLOURS = new SubstituteDiscoveryProfile(
            List.of("en:gluten-free-flour"),
            List.of(
                    "en:corn-starch",
                    "en:dried-coconut-flour",
                    "en:brown-rice-flour",
                    "en:buckwheat-flour",
                    "en:amaranth-flour"
            ),
            List.of("en:oat-flour")
    );

    /**
     * Gluten-containing breakfast cereals share this profile when same-category candidates
     * are all rejected (e.g. gluten intolerance). Alternatives are discovered via the catalog
     * {@code category_tags} value {@code Gluten free Breakfast cereals}.
     */
    private static final SubstituteDiscoveryProfile BREAKFAST_CEREALS = new SubstituteDiscoveryProfile(
            List.of("Gluten free Breakfast cereals"),
            List.of(),
            List.of()
    );

    /**
     * Dairy ice-cream categories fall back to sorbet and dairy-free frozen desserts tagged
     * {@code ice-creams-and-sorbets} when same-category candidates are all rejected.
     */
    private static final SubstituteDiscoveryProfile ICE_CREAMS = new SubstituteDiscoveryProfile(
            List.of("ice-creams-and-sorbets", "en:ice-creams-and-sorbets"),
            List.of(),
            List.of()
    );

    /**
     * Wheat-containing soy sauces fall back to {@code Gluten Free sauces} tag candidates.
     */
    private static final SubstituteDiscoveryProfile SOY_SAUCES = new SubstituteDiscoveryProfile(
            List.of("Gluten Free sauces"),
            List.of(),
            List.of()
    );

    /**
     * High-sodium sauces fall back to {@code Low sodium sauces} tag candidates for LOW_SODIUM profiles.
     */
    private static final SubstituteDiscoveryProfile SAUCES = new SubstituteDiscoveryProfile(
            List.of("Low sodium sauces", "Low sodium sauce"),
            List.of(),
            List.of()
    );

    /**
     * Wheat-containing breads fall back to {@code Gluten free bread} tag candidates.
     */
    private static final SubstituteDiscoveryProfile BREADS = new SubstituteDiscoveryProfile(
            List.of("Gluten free bread"),
            List.of(),
            List.of()
    );

    /**
     * Cow-milk catalog {@code main_category_en} values that share the oat/dairy-substitute
     * discovery profile when same-category candidates are all rejected (e.g. dairy intolerance).
     */
    private static final List<String> MILK_SOURCE_CATEGORIES = List.of(
            "Fresh milks",
            "Whole milk UHT",
            "UHT milks",
            "Whole milks",
            "Skimmed milks",
            "Milks",
            "Pasteurised milks",
            "Whole pasteurised milks",
            "Cow milks",
            "UHT Skimmed milks",
            "Strawberry milks",
            "Flavoured milks",
            "Chocolate milks",
            "Homogenized milks"
    );

    private static final List<String> ICE_CREAM_SOURCE_CATEGORIES = List.of(
            "Ice cream cones",
            "Ice cream bars",
            "Ice cream tubs",
            "Ice creams"
    );

    private static final List<String> BREAD_SOURCE_CATEGORIES = List.of(
            "Sliced breads",
            "Breads",
            "Wholemeal breads",
            "Multigrain-bread",
            "Wheat flatbreads",
            "Wholemeal sliced breads",
            "Whole-meal-bread",
            "White breads",
            "Multigrain sliced breads"
    );

    private final Map<String, SubstituteDiscoveryProfile> profilesBySourceCategory =
            buildProfilesBySourceCategory();

    private static Map<String, SubstituteDiscoveryProfile> buildProfilesBySourceCategory() {
        Map<String, SubstituteDiscoveryProfile> profiles = new HashMap<>();
        for (String milkCategory : MILK_SOURCE_CATEGORIES) {
            profiles.put(milkCategory, FRESH_MILKS);
        }
        for (String iceCreamCategory : ICE_CREAM_SOURCE_CATEGORIES) {
            profiles.put(iceCreamCategory, ICE_CREAMS);
        }
        for (String breadCategory : BREAD_SOURCE_CATEGORIES) {
            profiles.put(breadCategory, BREADS);
        }
        profiles.put("Wheat flours", WHEAT_FLOURS);
        profiles.put("White wheat flours", WHEAT_FLOURS);
        profiles.put("Breakfast cereals", BREAKFAST_CEREALS);
        profiles.put("Soy sauces", SOY_SAUCES);
        profiles.put("Sauces", SAUCES);
        return Map.copyOf(profiles);
    }

    public Optional<SubstituteDiscoveryProfile> forSourceCategory(String sourceMainCategoryEn) {
        if (sourceMainCategoryEn == null || sourceMainCategoryEn.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profilesBySourceCategory.get(sourceMainCategoryEn));
    }
}
