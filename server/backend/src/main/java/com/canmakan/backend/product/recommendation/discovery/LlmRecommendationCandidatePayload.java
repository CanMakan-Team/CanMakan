package com.canmakan.backend.product.recommendation.discovery;

import java.util.List;

/**
 * Structured Tier-B discovery payload returned by the recommendation ChatClient.
 */
public record LlmRecommendationCandidatePayload(
        List<Candidate> candidates
) {
    public record Candidate(
            String barcode,
            String productName,
            String brand,
            String reason
    ) {
    }
}
