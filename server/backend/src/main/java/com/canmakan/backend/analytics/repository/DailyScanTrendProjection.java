package com.canmakan.backend.analytics.repository;

public interface DailyScanTrendProjection {

    long getDayOffset();

    long getTotalCount();

    long getSafeCount();

    long getWarningCount();

    long getUnsafeCount();
}
