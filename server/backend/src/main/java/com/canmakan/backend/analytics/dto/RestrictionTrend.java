package com.canmakan.backend.analytics.dto;

public record RestrictionTrend(
        String restrictionCode,
        long flaggedCount
) {
}
