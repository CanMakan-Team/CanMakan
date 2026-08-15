package com.canmakan.backend.analytics.dto;

import java.time.LocalDate;

public record PeakScanDay(
        LocalDate date,
        long scanCount
) {
}
