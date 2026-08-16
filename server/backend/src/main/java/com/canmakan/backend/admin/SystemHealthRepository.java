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

    /** One AI execution row within the window; the service derives volume, latency and slow calls. */
    interface AiExecutionRow {
        Long getScanId();

        String getExecutionTier();

        Integer getLatencyMs();

        Long getCreatedAtEpochMs();
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
            SELECT scan_id AS scanId,
                   execution_tier AS executionTier,
                   latency_ms AS latencyMs,
                   UNIX_TIMESTAMP(created_at) * 1000 AS createdAtEpochMs
            FROM ai_execution_logs
            WHERE created_at >= FROM_UNIXTIME(:#{#since.epochSecond})
            """, nativeQuery = true)
    List<AiExecutionRow> findAiExecutionRowsSince(@Param("since") Instant since);

    @Query(value = """
            SELECT UNIX_TIMESTAMP(a.created_at) * 1000 AS tsEpochMs,
                   u.email AS adminEmail,
                   a.action_performed AS action,
                   a.target_entity AS target,
                   a.ip_address AS ipAddress
            FROM admin_audit_logs a
            JOIN users u ON u.id = a.admin_user_id
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
