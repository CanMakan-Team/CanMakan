package com.canmakan.backend.product.recommendation;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;

/**
 * JSON payload for the Python UC5 rank service {@code POST /rank} endpoint.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record PythonTfidfRankRequest(
        PythonTfidfProductPayload source,
        List<PythonTfidfProductPayload> candidates,
        PythonTfidfProfileHints profile) {

    record PythonTfidfProductPayload(
            String barcode,
            String productName,
            String brand,
            String mainCategoryEn,
            String categoryTags,
            String labelsTags,
            String ingredientsText,
            String quantity,
            String servingSize,
            BigDecimal servingQuantity,
            BigDecimal sugars100g,
            BigDecimal sodium100g) {
    }

    record PythonTfidfProfileHints(
            boolean preferLowSugar,
            boolean milkSubstituteDiscovery,
            boolean flourSubstituteDiscovery,
            boolean peanutSpreadDiscovery,
            List<String> secondaryIncludeTags,
            List<String> deprioritizeTags,
            List<String> includeTags,
            List<String> priorSafeBarcodes) {
    }
}
