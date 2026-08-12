package com.canmakan.backend.product.recommendation;

import java.util.List;

/**
 * One grouped recommendation session: a scanned source product and the alternatives shown (UC17).
 */
public record RecommendationHistoryEntryDto(
        Long scanId,
        String sourceBarcode,
        String sourceProductName,
        String sourceBrand,
        String sourceVerdict,
        String recommendedAt,
        List<RecommendationHistoryAlternativeDto> alternatives
) {
}
