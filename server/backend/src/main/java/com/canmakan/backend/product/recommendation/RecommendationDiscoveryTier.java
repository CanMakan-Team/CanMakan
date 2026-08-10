package com.canmakan.backend.product.recommendation;

/**
 * How an alternative product was discovered before rule-engine verification.
 * Persisted to {@code recommendation_logs.discovery_tier}.
 */
public enum RecommendationDiscoveryTier {

    /** Catalog query + ranker over the local products table. */
    TIER_A_CATALOG,

    /** LLM suggested candidates, then verified by {@code DietaryRuleEngine}. */
    TIER_B_LLM_DISCOVERY
}
