package com.canmakan.backend.analytics.dto;

public record ConsumerTrendsDataQuality(
        boolean partial,
        long skippedMalformedFindings
) {
}
