package com.canmakan.backend.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Typed Open Food Facts response used only by the integration boundary.
 *
 * @author YangMaowei
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenFoodFactsResponse(
        String status,
        OpenFoodFactsProduct product
) {

    boolean successful() {
        return "success".equalsIgnoreCase(status) || "1".equals(status);
    }
}

/** Open Food Facts product fields needed by the current dietary rules. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenFoodFactsProduct(
        @JsonProperty("product_name") String productName,
        @JsonProperty("product_type") String productType,
        @JsonProperty("ingredients_text") String ingredientsText,
        List<OpenFoodFactsIngredient> ingredients,
        @JsonProperty("labels_tags") List<String> labelTags,
        @JsonProperty("traces_tags") List<String> tracesTags,
        OpenFoodFactsNutriments nutriments
) {
}

/** Structured ingredient supplied by Open Food Facts. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenFoodFactsIngredient(
        String id,
        String text
) {
}

/** Nullable per-100g nutrient fields supplied by Open Food Facts. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenFoodFactsNutriments(
        @JsonProperty("sugars_100g") BigDecimal sugarsPer100g,
        @JsonProperty("sodium_100g") BigDecimal sodiumPer100g,
        @JsonProperty("trans_fat_100g") BigDecimal transFatPer100g,
        @JsonProperty("saturated_fat_100g") BigDecimal saturatedFatPer100g,
        @JsonProperty("fat_100g") BigDecimal fatPer100g,
        @JsonProperty("energy-kcal_100g") BigDecimal energyKcalPer100g
) {
}

/** Typed EAN-Search fallback item used by validation-only callers. */
@JsonIgnoreProperties(ignoreUnknown = true)
record EanSearchItem(
        String name,
        String categoryName,
        String error
) {
}
