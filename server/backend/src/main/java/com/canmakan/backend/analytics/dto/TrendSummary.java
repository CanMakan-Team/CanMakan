package com.canmakan.backend.analytics.dto;

public record TrendSummary(
        long totalScans,
        long safeCount,
        long warningCount,
        long unsafeCount
) {
}
