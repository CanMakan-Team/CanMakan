package com.canmakan.backend.admin;

import com.canmakan.backend.product.scan.Scan;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregates for UC16 system health, drawn from tables that already exist. Native queries
 * do not depend on the root entity, so this one repository can read {@code ai_execution_logs},
 * {@code admin_audit_logs} and {@code scans}; the service does the light shaping in Java.
 */
public interface SystemHealthRepository extends Repository<Scan, Long> {

    /** Volume and latency aggregates for the AI execution window, computed in SQL. */
    interface AiExecutionSummaryRow {
        Long getTotal();

        Long getTier3Count();

        Double getAverageLatencyMs();

        Integer getMaxLatencyMs();
    }

    /** One AI execution row within the window; the service derives slow calls. */
    interface AiExecutionRow {
        Long getScanId();

        String getExecutionTier();

        Integer getLatencyMs();

        Long getCreatedAtEpochMs();
    }

    /** Average latency for one of the {@code TREND_BUCKETS} equal time slices across the window. */
    interface LatencyBucketRow {
        Integer getBucketIndex();

        Double getAverageLatencyMs();
    }

    /** One administrative action, most recent first, with the acting admin's email. */
    interface AuditRow {
        Long getTsEpochMs();

        String getAdminEmail();

        String getAction();

        String getTarget();

        String getIpAddress();
    }

    /** Verdict-mix and incomplete-data counts for the scan-quality signal. */
    interface ScanQualityRow {
        Long getTotal();

        Long getSafe();

        Long getWarning();

        Long getUnsafe();

        Long getIncomplete();
    }

    @Query(value = """
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN UPPER(execution_tier) = 'TIER_3_LLM' THEN 1 ELSE 0 END), 0)
                        AS tier3Count,
                   AVG(latency_ms) AS averageLatencyMs,
                   MAX(latency_ms) AS maxLatencyMs
            FROM ai_execution_logs
            WHERE created_at >= FROM_UNIXTIME(:#{#since.epochSecond})
            """, nativeQuery = true)
    AiExecutionSummaryRow findAiExecutionSummarySince(@Param("since") Instant since);

    @Query(value = """
            SELECT scan_id AS scanId,
                   execution_tier AS executionTier,
                   latency_ms AS latencyMs,
                   UNIX_TIMESTAMP(created_at) * 1000 AS createdAtEpochMs
            FROM ai_execution_logs
            WHERE created_at >= FROM_UNIXTIME(:#{#since.epochSecond})
              AND latency_ms IS NOT NULL
            ORDER BY latency_ms DESC
            LIMIT :max
            """, nativeQuery = true)
    List<AiExecutionRow> findSlowestAiExecutionRowsSince(
            @Param("since") Instant since, @Param("max") int max);

    @Query(value = """
            SELECT LEAST(:buckets - 1, GREATEST(0,
                        FLOOR((UNIX_TIMESTAMP(created_at) * 1000 - :sinceEpochMs) * :buckets / :spanMs)))
                        AS bucketIndex,
                   AVG(latency_ms) AS averageLatencyMs
            FROM ai_execution_logs
            WHERE created_at >= FROM_UNIXTIME(:#{#since.epochSecond})
              AND latency_ms IS NOT NULL
            GROUP BY bucketIndex
            """, nativeQuery = true)
    List<LatencyBucketRow> findLatencyTrendSince(
            @Param("since") Instant since,
            @Param("sinceEpochMs") long sinceEpochMs,
            @Param("spanMs") long spanMs,
            @Param("buckets") int buckets);

    @Query(value = """
            SELECT UNIX_TIMESTAMP(a.created_at) * 1000 AS tsEpochMs,
                   u.email AS adminEmail,
                   a.action_performed AS action,
                   a.target_entity AS target,
                   a.ip_address AS ipAddress
            FROM admin_audit_logs a
            LEFT JOIN users u ON u.id = a.admin_user_id
            ORDER BY a.created_at DESC
            LIMIT :max
            """, nativeQuery = true)
    List<AuditRow> findRecentAuditRows(@Param("max") int max);

    @Query(value = """
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN verdict = 'SAFE' THEN 1 ELSE 0 END), 0) AS safe,
                   COALESCE(SUM(CASE WHEN verdict = 'WARNING' THEN 1 ELSE 0 END), 0) AS warning,
                   COALESCE(SUM(CASE WHEN verdict = 'UNSAFE' THEN 1 ELSE 0 END), 0) AS unsafe,
                   COALESCE(SUM(CASE WHEN JSON_SEARCH(findings_json, 'one', 'INCOMPLETE_DATA')
                        IS NOT NULL THEN 1 ELSE 0 END), 0) AS incomplete
            FROM scans
            WHERE scanned_at >= FROM_UNIXTIME(:#{#since.epochSecond})
            """, nativeQuery = true)
    ScanQualityRow findScanQualitySince(@Param("since") Instant since);
}
