package com.canmakan.backend.product.recommendation;

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

    private final Map<String, SubstituteDiscoveryProfile> profilesBySourceCategory = Map.of(
            "Fresh milks", FRESH_MILKS,
            "Wheat flours", WHEAT_FLOURS,
            "White wheat flours", WHEAT_FLOURS
    );

    public Optional<SubstituteDiscoveryProfile> forSourceCategory(String sourceMainCategoryEn) {
        if (sourceMainCategoryEn == null || sourceMainCategoryEn.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profilesBySourceCategory.get(sourceMainCategoryEn));
    }
}
