package com.canmakan.backend.analytics.dto;

import java.math.BigDecimal;

public record TrendSummary(
        long totalScans,
        long safeCount,
        long warningCount,
        long unsafeCount,
        long uniqueProducts,
        BigDecimal averageScansPerDay,
        PeakScanDay peakScanDay
) {
}
