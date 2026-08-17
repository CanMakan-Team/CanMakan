package com.canmakan.backend.admin.dto;

import java.util.List;

/**
 * UC16 - System health snapshot for the system admin portal.
 *
 * Assembled only from data that already exists: component health from Spring Boot Actuator, AI
 * execution monitoring from {@code ai_execution_logs}, an admin activity trail from
 * {@code admin_audit_logs}, and scan data quality from {@code scans}. Field names mirror the web
 * client's TypeScript contract.
 */
public record SystemHealthResponse(
        String generatedAt,
        String overallStatus,
        List<ComponentHealth> components,
        AiExecutionHealth ai,
        List<AuditEntry> auditTrail,
        ScanDataQuality scanQuality
) {

    /** One Actuator health component (e.g. db, diskSpace, ping) and its status code. */
    public record ComponentHealth(String name, String status) {
    }

    /** A single slow AI call surfaced for investigation. */
    public record SlowCall(long scanId, String tier, int latencyMs) {
    }

    public record AiExecutionHealth(
            int tier3RatePct,
            long averageLatencyMs,
            long maxLatencyMs,
            long totalCalls,
            List<Integer> latencyTrend,
            List<SlowCall> slowestCalls
    ) {
    }

    /** One administrative action from the audit trail. */
    public record AuditEntry(
            String timestamp,
            String admin,
            String action,
            String target,
            String ipAddress
    ) {
    }

    public record ScanDataQuality(
            int incompleteDataPct,
            int safePct,
            int warningPct,
            int unsafePct,
            long totalScans
    ) {
    }
}
