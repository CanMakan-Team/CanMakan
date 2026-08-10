package com.canmakan.backend.product.recommendation;

/**
 * How complete the candidate product metadata was at recommendation time.
 * Persisted to {@code recommendation_logs.data_quality}.
 */
public enum RecommendationDataQuality {

    /** Ingredients and category available from OFF or manual backfill. */
    VERIFIED,

    /** Some fields present but incomplete (e.g. category only, thin ingredients). */
    PARTIAL,

    /** Category or metadata inferred offline; treat as lower confidence. */
    INFERRED
}
