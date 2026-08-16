package com.canmakan.backend.analytics.repository;

/**
 * One scan event for UC15 usage statistics: which user scanned, and when (epoch milliseconds).
 * The service derives activity, retention, sessions and the heatmap from these instants.
 */
public interface UserScanInstantProjection {

    Long getUserId();

    Long getScannedAtEpochMs();
}
