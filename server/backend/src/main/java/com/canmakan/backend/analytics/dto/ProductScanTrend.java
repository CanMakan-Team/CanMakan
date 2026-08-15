package com.canmakan.backend.analytics.dto;

import java.math.BigDecimal;

public record ProductScanTrend(
        int rank,
        String productName,
        long scanCount,
        BigDecimal percentage
) {
}
