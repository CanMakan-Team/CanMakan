package com.canmakan.backend.product.recommendation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Curated source-category profiles for Tier A tag-based substitute discovery.
 */
@Component
public class SubstituteDiscoveryProfiles {

    private static final List<String> GLUTEN_LABEL_TAGS = List.of(
            "en:no-gluten",
            "en:certified-gluten-free"
    );
    private static final List<String> DAIRY_FREE_LABEL_TAGS = List.of(
            "en:vegan",
            "en:without-addition-of-dairy-products"
    );
    private static final List<String> LOW_SODIUM_LABEL_TAGS = List.of(
            "en:low-salt",
            "en:no-salt-added",
            "en:no-added-salt",
            "en:low-sodium",
            "en:reduced-salt",
            "en:low-or-no-sodium",
            "en:low-or-no-salt"
    );

    private static final SubstituteDiscoveryProfile FRESH_MILKS = new SubstituteDiscoveryProfile(
            List.of("en:milk-substitutes", "en:dairy-substitutes"),
            List.of(
                    "en:oat-based-drinks",
                    "en:soy-based-drinks",
                    "en:almond-based-drinks",
                    "en:unsweetened-plain-soy-based-drinks"
            ),
            List.of("en:plant-based-creams-for-cooking", "en:coconut-milks-and-creams"),
            DAIRY_FREE_LABEL_TAGS,
            List.of()
    );

    private static final SubstituteDiscoveryProfile WHEAT_FLOURS = new SubstituteDiscoveryProfile(
            List.of(
                    "en:gluten-free-flour",
                    "Gluten free flour",
                    "Gluten-free flour"
            ),
            List.of(
                    "en:corn-starch",
                    "en:dried-coconut-flour",
                    "en:brown-rice-flour",
                    "en:buckwheat-flour",
                    "en:amaranth-flour"
            ),
            List.of("en:oat-flour"),
            GLUTEN_LABEL_TAGS,
            List.of("Wheat flours", "White wheat flours")
    );

    /**
     * Gluten-containing breakfast cereals share this profile when same-category candidates
     * are all rejected (e.g. gluten intolerance). Alternatives are discovered via the catalog
     * {@code category_tags} value {@code Gluten free Breakfast cereals}.
     */
    private static final SubstituteDiscoveryProfile BREAKFAST_CEREALS = new SubstituteDiscoveryProfile(
            List.of("Gluten free Breakfast cereals"),
            List.of(),
            List.of(),
            GLUTEN_LABEL_TAGS,
            List.of("Breakfast cereals")
    );

    /**
     * Dairy ice-cream categories fall back to sorbet and dairy-free frozen desserts tagged
     * {@code ice-creams-and-sorbets} when same-category candidates are all rejected.
     */
    private static final SubstituteDiscoveryProfile ICE_CREAMS = new SubstituteDiscoveryProfile(
            List.of("ice-creams-and-sorbets", "en:ice-creams-and-sorbets"),
            List.of(),
            List.of(),
            DAIRY_FREE_LABEL_TAGS,
            List.of(
                    "Ice cream cones",
                    "Ice cream bars",
                    "Ice cream tubs",
                    "Ice creams"
            )
    );

    /**
     * Wheat-containing soy sauces fall back to {@code Gluten Free sauces} tag candidates.
     */
    private static final SubstituteDiscoveryProfile SOY_SAUCES = new SubstituteDiscoveryProfile(
            List.of("Gluten Free sauces"),
            List.of(),
            List.of(),
            GLUTEN_LABEL_TAGS,
            List.of("Sauces", "Soy sauces")
    );

    /**
     * High-sodium sauces fall back to {@code Low sodium sauces} tag candidates for LOW_SODIUM profiles.
     */
    private static final SubstituteDiscoveryProfile SAUCES = new SubstituteDiscoveryProfile(
            List.of("Low sodium sauces", "Low sodium sauce"),
            List.of(),
            List.of(),
            LOW_SODIUM_LABEL_TAGS,
            List.of("Sauces", "Soy sauces")
    );

    /**
     * Wheat-containing breads fall back to {@code Gluten free bread} tag candidates.
     */
    private static final SubstituteDiscoveryProfile BREADS = new SubstituteDiscoveryProfile(
            List.of("Gluten free bread"),
            List.of(),
            List.of(),
            GLUTEN_LABEL_TAGS,
            List.of(
                    "Sliced breads",
                    "Breads",
                    "Wholemeal breads",
                    "Multigrain-bread",
                    "Wheat flatbreads",
                    "Wholemeal sliced breads",
                    "Whole-meal-bread",
                    "White breads",
                    "Multigrain sliced breads"
            )
    );

    /**
     * Peanut-containing butters fall back to nut/seed butter catalog rows (not jams or
     * other sweet spreads). Honey and peanut traces are excluded at query time.
     */
    private static final List<String> PEANUT_BUTTER_SUBSTITUTE_INCLUDE_TAGS = List.of(
            "en:nut-butters",
            "en:tahini",
            "en:cereal-butters",
            "en:oilseed-purees");

    private static final List<String> PEANUT_BUTTER_SUBSTITUTE_BOOST_TAGS = List.of(
            "en:nut-butters",
            "en:tahini",
            "en:cereal-butters");

    private static final List<String> PEANUT_BUTTER_SUBSTITUTE_DEPRIORITIZE_TAGS = List.of(
            "en:jams",
            "en:sweet-spreads",
            "en:fruit-and-vegetable-preserves",
            "en:chocolate-spreads");

    private static final SubstituteDiscoveryProfile PEANUT_BUTTERS = new SubstituteDiscoveryProfile(
            PEANUT_BUTTER_SUBSTITUTE_INCLUDE_TAGS,
            PEANUT_BUTTER_SUBSTITUTE_BOOST_TAGS,
            PEANUT_BUTTER_SUBSTITUTE_DEPRIORITIZE_TAGS,
            List.of(),
            List.of(),
            List.of("en:honeys"),
            List.of("en:peanuts")
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

    private static final List<String> ICE_CREAM_CATEGORY_TAGS = List.of(
            "ice-creams-and-sorbets",
            "en:ice-creams-and-sorbets",
            "en:ice-creams",
            "en:ice-cream-tubs",
            "en:ice-cream-bars",
            "en:ice-cream-cones",
            "en:frozen-desserts"
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

    private static final List<String> BREAD_CATEGORY_TAGS = List.of(
            "en:breads",
            "en:sliced-breads",
            "en:white-breads",
            "en:wholemeal-breads",
            "en:multigrain-bread",
            "en:wheat-flatbreads"
    );

    private static final List<String> BREAKFAST_CEREAL_CATEGORY_TAGS = List.of(
            "en:breakfast-cereals"
    );

    private static final List<String> SAUCE_SOURCE_CATEGORIES = List.of(
            "Sauces",
            "Soy sauces"
    );

    private static final List<String> SAUCE_CATEGORY_TAGS = List.of(
            "en:sauces",
            "en:soy-sauces"
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
        profiles.put("Peanut butters", PEANUT_BUTTERS);
        profiles.put("Crunchy peanut butters", PEANUT_BUTTERS);
        return Map.copyOf(profiles);
    }

    /**
     * Wheat-flour substitute discovery: only flour-like catalog rows should be suggested.
     */
    public boolean isFlourSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("en:gluten-free-flour");
    }

    /**
     * Peanut-butter substitute discovery: only peanut-free nut/seed butter rows should be suggested.
     */
    public boolean isPeanutSpreadSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("en:nut-butters");
    }

    /**
     * Ice-cream substitute discovery: only frozen dessert rows should be suggested.
     */
    public boolean isIceCreamSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("ice-creams-and-sorbets");
    }

    /**
     * Bread substitute discovery: only GF bread catalog rows should be suggested.
     */
    public boolean isBreadSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("Gluten free bread");
    }

    /**
     * Breakfast-cereal substitute discovery: only tagged GF cereal rows without oats
     * should be suggested.
     */
    public boolean isBreakfastCerealSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("Gluten free Breakfast cereals");
    }

    /**
     * Low-sodium sauce substitute discovery: only sauce/soy-sauce rows with low-salt signals
     * should be suggested.
     */
    public boolean isLowSodiumSauceSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("Low sodium sauces");
    }

    public Optional<SubstituteDiscoveryProfile> forSourceCategory(String sourceMainCategoryEn) {
        if (sourceMainCategoryEn == null || sourceMainCategoryEn.isBlank()) {
            return Optional.empty();
        }
        String trimmed = sourceMainCategoryEn.trim();
        SubstituteDiscoveryProfile profile = profilesBySourceCategory.get(trimmed);
        if (profile != null) {
            return Optional.of(profile);
        }
        for (Map.Entry<String, SubstituteDiscoveryProfile> entry : profilesBySourceCategory.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmed)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a substitute profile from catalog metadata, including flavour-specific
     * ice-cream {@code main_category_en} values such as {@code Chocolate ice cream tubs}.
     */
    public Optional<SubstituteDiscoveryProfile> forSourceProduct(CatalogProduct source) {
        if (source == null) {
            return Optional.empty();
        }
        Optional<SubstituteDiscoveryProfile> direct = forSourceCategory(source.getMainCategoryEn());
        if (direct.isPresent()) {
            return direct;
        }
        if (isIceCreamSource(source)) {
            return Optional.of(ICE_CREAMS);
        }
        if (isSauceSource(source)) {
            return Optional.of(SAUCES);
        }
        if (isBreadSource(source)) {
            return Optional.of(BREADS);
        }
        return Optional.empty();
    }

    static boolean isSauceSource(CatalogProduct source) {
        if (source == null) {
            return false;
        }
        String category = source.getMainCategoryEn();
        if (category != null && SAUCE_SOURCE_CATEGORIES.contains(category)) {
            return true;
        }
        Set<String> tags = CategoryTagParser.parseTags(source.getCategoryTags());
        return CategoryTagParser.containsAny(tags, SAUCE_CATEGORY_TAGS);
    }

    static boolean isBreadSource(CatalogProduct source) {
        if (source == null) {
            return false;
        }
        String category = source.getMainCategoryEn();
        if (category != null && matchesBreadSourceCategory(category)) {
            return true;
        }
        if (category != null && category.toLowerCase(Locale.ROOT).contains("bread")) {
            return true;
        }
        Set<String> tags = CategoryTagParser.parseTags(source.getCategoryTags());
        if (CategoryTagParser.containsAny(tags, BREAD_CATEGORY_TAGS)) {
            return true;
        }
        return tags.stream().anyMatch(tag -> tag.contains("bread"));
    }

    static boolean isBreakfastCerealSource(CatalogProduct source) {
        if (source == null) {
            return false;
        }
        if ("Breakfast cereals".equals(source.getMainCategoryEn())) {
            return true;
        }
        Set<String> tags = CategoryTagParser.parseTags(source.getCategoryTags());
        if (CategoryTagParser.containsAny(tags, BREAKFAST_CEREAL_CATEGORY_TAGS)) {
            return true;
        }
        return tags.stream().anyMatch(tag -> tag.contains("breakfast-cereal"));
    }

    private static boolean matchesBreadSourceCategory(String category) {
        String trimmed = category.trim();
        for (String breadCategory : BREAD_SOURCE_CATEGORIES) {
            if (breadCategory.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    static boolean isIceCreamSource(CatalogProduct source) {
        if (source == null) {
            return false;
        }
        String category = source.getMainCategoryEn();
        if (category != null && category.toLowerCase().contains("ice cream")) {
            return true;
        }
        Set<String> tags = CategoryTagParser.parseTags(source.getCategoryTags());
        if (CategoryTagParser.containsAny(tags, ICE_CREAM_CATEGORY_TAGS)) {
            return true;
        }
        return tags.stream().anyMatch(tag -> tag.contains("ice-cream"));
    }
}
