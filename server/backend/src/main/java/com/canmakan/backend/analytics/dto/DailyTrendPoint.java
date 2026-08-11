package com.canmakan.backend.analytics.dto;

import java.time.LocalDate;

public record DailyTrendPoint(
        LocalDate date,
        long totalCount,
        long safeCount,
        long warningCount,
        long unsafeCount
) {
}
