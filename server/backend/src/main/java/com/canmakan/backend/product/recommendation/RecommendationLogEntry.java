package com.canmakan.backend.product.recommendation;

import java.math.BigDecimal;

/**
 * Input for logging one recommended alternative (Tier A or Tier B after rule verification).
 */
public record RecommendationLogEntry(
        Long profileId,
        Long scanId,
        String sourceBarcode,
        String recommendedBarcode,
        String recommendedName,
        String recommendedBrand,
        RecommendationDiscoveryTier discoveryTier,
        BigDecimal rankScore,
        String matchReason,
        RecommendationDataQuality dataQuality,
        boolean shownToUser
) {
}
