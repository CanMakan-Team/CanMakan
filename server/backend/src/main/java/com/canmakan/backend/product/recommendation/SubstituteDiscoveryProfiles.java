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

    private final Map<String, SubstituteDiscoveryProfile> profilesBySourceCategory =
            buildProfilesBySourceCategory();

    private static Map<String, SubstituteDiscoveryProfile> buildProfilesBySourceCategory() {
        Map<String, SubstituteDiscoveryProfile> profiles = new HashMap<>();
        for (String milkCategory : MILK_SOURCE_CATEGORIES) {
            profiles.put(milkCategory, FRESH_MILKS);
        }
        profiles.put("Wheat flours", WHEAT_FLOURS);
        profiles.put("White wheat flours", WHEAT_FLOURS);
        return Map.copyOf(profiles);
    }

    public Optional<SubstituteDiscoveryProfile> forSourceCategory(String sourceMainCategoryEn) {
        if (sourceMainCategoryEn == null || sourceMainCategoryEn.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profilesBySourceCategory.get(sourceMainCategoryEn));
    }
}
