package com.canmakan.backend.product.recommendation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;

/**
 * JSON response from the Python UC5 rank service {@code POST /rank} endpoint.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record PythonTfidfRankResponse(List<PythonTfidfRankedProduct> ranked) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record PythonTfidfRankedProduct(
            String barcode,
            BigDecimal score,
            @JsonProperty("match_reason") String matchReason) {
    }
}
