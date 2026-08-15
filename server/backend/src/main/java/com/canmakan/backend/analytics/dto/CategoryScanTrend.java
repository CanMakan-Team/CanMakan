package com.canmakan.backend.analytics.dto;

import java.math.BigDecimal;

public record CategoryScanTrend(
        String category,
        long scanCount,
        BigDecimal percentage
) {
}
