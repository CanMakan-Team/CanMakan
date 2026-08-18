package com.canmakan.backend.product.recommendation.discovery;

/**
 * Input for logging one Tier-B LLM discovery run.
 */
public record RecommendationDiscoveryAudit(
        Long scanId,
        Long profileId,
        String sourceBarcode,
        String modelId,
        Integer promptTokens,
        Integer completionTokens,
        long latencyMs,
        String llmCandidatesJson,
        int candidatesAccepted,
        int candidatesRejected
) {
}
