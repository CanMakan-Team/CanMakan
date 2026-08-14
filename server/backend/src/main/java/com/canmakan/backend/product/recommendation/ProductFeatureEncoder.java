package com.canmakan.backend.product.recommendation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds sparse feature vectors from catalog rows for content-based similarity.
 * Tolerates missing nutrition and placeholder ingredient text.
 */
@Component
class ProductFeatureEncoder {

    private static final double NAME_WEIGHT = 3.0;
    private static final double BRAND_WEIGHT = 1.5;
    private static final double CATEGORY_WEIGHT = 2.5;
    private static final double TAG_WEIGHT = 2.0;
    private static final double ALLERGEN_WEIGHT = 2.0;
    private static final double LABEL_WEIGHT = 1.5;
    private static final double INGREDIENT_WEIGHT = 1.0;
    private static final double ALLERGEN_QUERY_SCALE = 0.15;
    private static final Set<String> ALLERGEN_QUERY_TOKENS = Set.of(
            "peanut",
            "peanuts",
            "groundnut",
            "milk",
            "dairy",
            "wheat",
            "gluten",
            "barley",
            "rye",
            "peanut-butters",
            "en:peanuts",
            "en:milk",
            "en:wheat",
            "en:gluten");

    private final ProductFeatureVectorStore vectorStore;

    ProductFeatureEncoder(ProductFeatureVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    Map<String, Double> encode(CatalogProduct product) {
        if (product == null) {
            return Map.of();
        }
        return vectorStore.getVector(product.getBarcode()).orElseGet(() -> encodeInline(product));
    }

    /**
     * Source vector for cosine ranking/kNN: allergen tokens are downweighted so
     * neighbors are same use-type, not same allergen (e.g. other peanut butters).
     */
    Map<String, Double> encodeQuery(CatalogProduct product) {
        Map<String, Double> encoded = encode(product);
        if (encoded.isEmpty()) {
            return encoded;
        }
        Map<String, Double> query = new HashMap<>(encoded);
        for (String token : ALLERGEN_QUERY_TOKENS) {
            String normalized = normalizeTag(token);
            if (query.containsKey(normalized)) {
                query.put(normalized, query.get(normalized) * ALLERGEN_QUERY_SCALE);
            }
            if (query.containsKey(token)) {
                query.put(token, query.get(token) * ALLERGEN_QUERY_SCALE);
            }
        }
        return query;
    }

    private Map<String, Double> encodeInline(CatalogProduct product) {
        Map<String, Double> vector = new HashMap<>();
        addTokens(vector, tokenize(product.getProductName()), NAME_WEIGHT);
        addTokens(vector, tokenize(product.getBrand()), BRAND_WEIGHT);
        addTokens(vector, tokenize(product.getMainCategoryEn()), CATEGORY_WEIGHT);
        addTags(vector, CategoryTagParser.parseTags(product.getCategoryTags()), TAG_WEIGHT);
        addTags(vector, CategoryTagParser.parseTags(product.getLabelsTags()), LABEL_WEIGHT);
        addTags(vector, CategoryTagParser.parseTags(product.getAllergens()), ALLERGEN_WEIGHT);

        if (!isPlaceholderIngredients(product)) {
            addTokens(vector, tokenize(product.getIngredientsText()), INGREDIENT_WEIGHT);
        }

        return vector;
    }

    /**
     * Source rows whose ingredients duplicate the category label carry little signal.
     */
    boolean isSparseSource(CatalogProduct source) {
        if (source == null) {
            return true;
        }
        return isPlaceholderIngredients(source);
    }

    /**
     * Inferred substitute tags when curated profiles miss or Tier A returns nothing.
     */
    List<String> inferSubstituteTags(CatalogProduct source) {
        Set<String> tags = new LinkedHashSet<>();
        if (source == null) {
            return List.of();
        }

        String haystack = joinLower(
                source.getProductName(),
                source.getMainCategoryEn(),
                source.getCategoryTags(),
                source.getIngredientsText());

        boolean isIceCreamProduct = isIceCreamProduct(haystack);
        boolean isBreadProduct = SubstituteDiscoveryProfiles.isBreadSource(source);
        boolean isBreakfastCerealProduct = SubstituteDiscoveryProfiles.isBreakfastCerealSource(source);
        if (!isIceCreamProduct && !isBreadProduct && containsAny(haystack, "milk", "dairy", "uht")) {
            tags.add("en:milk-substitutes");
            tags.add("en:dairy-substitutes");
            tags.add("en:plant-based-milk-alternatives");
        }
        if (containsAny(haystack, "bread", "bun", "roll")) {
            tags.add("Gluten free bread");
        }
        if (containsAny(haystack, "cereal")) {
            if (!containsAny(haystack, "spread", "tahini")
                    && (containsAny(haystack, "breakfast cereal", "breakfast-cereal")
                            || haystack.contains("en:breakfast-cereals"))) {
                tags.add("Gluten free Breakfast cereals");
            }
        }
        if (!isBreakfastCerealProduct && containsAny(haystack, "flour", "wheat")) {
            tags.add("en:gluten-free-flour");
            tags.add("Gluten free flour");
            tags.add("Gluten-free flour");
        }
        if (containsAny(haystack, "soy sauce", "soya sauce")) {
            tags.add("Gluten Free sauces");
        }
        if (containsAny(haystack, "sauce")) {
            tags.add("Low sodium sauces");
            tags.add("Low sodium sauce");
        }
        if (isIceCreamProduct || containsAny(haystack, "ice cream", "ice-cream", "sorbet")) {
            tags.add("ice-creams-and-sorbets");
            tags.add("en:ice-creams-and-sorbets");
        }
        if (containsAny(haystack, "peanut butter", "peanut-butters")) {
            tags.add("en:nut-butters");
            tags.add("en:tahini");
            tags.add("en:cereal-butters");
        }

        return List.copyOf(tags);
    }

    private static boolean isIceCreamProduct(String haystack) {
        return haystack.contains("en:ice-cream")
                || haystack.contains("ice-creams-and-sorbets")
                || haystack.contains("ice cream");
    }

    boolean isUnsweetened(CatalogProduct product) {
        if (product == null) {
            return false;
        }
        String name = safeLower(product.getProductName());
        if (name.contains("unsweetened") || name.contains("no sugar") || name.contains("zero sugar")) {
            return true;
        }
        Set<String> labels = CategoryTagParser.parseTags(product.getLabelsTags());
        return CategoryTagParser.containsAny(labels, List.of(
                "en:no-sugar",
                "en:low-sugar",
                "en:sugar-free",
                "en:unsweetened"));
    }

    private static boolean isPlaceholderIngredients(CatalogProduct product) {
        String ingredients = product.getIngredientsText();
        if (ingredients == null || ingredients.isBlank()) {
            return true;
        }
        String category = product.getMainCategoryEn();
        return category != null && ingredients.trim().equalsIgnoreCase(category.trim());
    }

    private static void addTokens(Map<String, Double> vector, List<String> tokens, double weight) {
        for (String token : tokens) {
            vector.merge(token, weight, Double::sum);
        }
    }

    private static void addTags(Map<String, Double> vector, Set<String> tags, double weight) {
        for (String tag : tags) {
            vector.merge(normalizeTag(tag), weight, Double::sum);
        }
    }

    private static List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .split("\\s+"));
    }

    private static String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static String joinLower(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.append(value.toLowerCase(Locale.ROOT)).append(' ');
            }
        }
        return builder.toString();
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
