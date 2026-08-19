package com.canmakan.backend.analytics.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConsumerTrendsResponse(
        TrendPeriod period,
        ConsumerTrendsAppliedFilters appliedFilters,
        TrendSummary summary,
        List<DailyTrendPoint> dailyTrend,
        List<ProductScanTrend> mostScannedProducts,
        List<CategoryScanTrend> categoryOverview,
        List<RestrictionTrend> topRestrictions,
        List<FlaggedIngredientTrend> topFlaggedIngredients,
        ConsumerTrendsDataQuality dataQuality,
        OffsetDateTime generatedAt
) {
}
