package com.canmakan.backend.product.recommendation.history;

import java.math.BigDecimal;

/**
 * One alternative product that was shown in a past recommendation session (UC17).
 */
public record RecommendationHistoryAlternativeDto(
        String barcode,
        String productName,
        String brand,
        String matchReason,
        BigDecimal rankScore,
        String discoveryTier
) {
}
