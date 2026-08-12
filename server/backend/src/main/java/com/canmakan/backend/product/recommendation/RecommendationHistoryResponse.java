package com.canmakan.backend.product.recommendation;

import java.util.List;

/**
 * UC17 recommendation history for a dietary profile, grouped by scan/session.
 */
public record RecommendationHistoryResponse(
        Long profileId,
        List<RecommendationHistoryEntryDto> history
) {
    public static RecommendationHistoryResponse empty(Long profileId) {
        return new RecommendationHistoryResponse(profileId, List.of());
    }
}
