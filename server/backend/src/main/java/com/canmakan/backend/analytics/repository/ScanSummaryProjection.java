package com.canmakan.backend.analytics.repository;

public interface ScanSummaryProjection {

    long getTotalScans();

    long getSafeCount();

    long getWarningCount();

    long getUnsafeCount();
}
