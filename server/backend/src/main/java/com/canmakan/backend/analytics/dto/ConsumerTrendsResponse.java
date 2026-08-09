package com.canmakan.backend.analytics.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConsumerTrendsResponse(
        TrendPeriod period,
        TrendSummary summary,
        List<DailyTrendPoint> dailyTrend,
        List<FlaggedIngredientTrend> topFlaggedIngredients,
        ConsumerTrendsDataQuality dataQuality,
        OffsetDateTime generatedAt
) {
}
