package com.canmakan.backend.analytics.dto;

import java.time.LocalDate;

public record TrendPeriod(
        LocalDate from,
        LocalDate to,
        String timezone
) {
}
