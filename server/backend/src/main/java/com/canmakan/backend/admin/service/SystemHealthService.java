package com.canmakan.backend.admin.service;

import com.canmakan.backend.admin.dto.SystemHealthResponse;
import com.canmakan.backend.admin.dto.SystemHealthResponse.AiExecutionHealth;
import com.canmakan.backend.admin.dto.SystemHealthResponse.AuditEntry;
import com.canmakan.backend.admin.dto.SystemHealthResponse.ComponentHealth;
import com.canmakan.backend.admin.dto.SystemHealthResponse.ScanDataQuality;
import com.canmakan.backend.admin.dto.SystemHealthResponse.SlowCall;
import com.canmakan.backend.admin.repository.SystemHealthRepository;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC16 - assembles a system-health snapshot for the system admin portal from data that already
 * exists: a lightweight component probe (database connectivity + disk space), AI execution
 * monitoring from {@code ai_execution_logs}, the admin activity trail from {@code admin_audit_logs},
 * and scan data quality from {@code scans}.
 *
 * <p>No dedicated crash/error log table exists, so the "logs" shown are the AI execution stream, the
 * admin audit stream, and the data-quality signal - not raw application error logs.
 *
 * @author XieHuayuan
 */
@Service
public class SystemHealthService {

    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int MAX_WINDOW_HOURS = 720;
    private static final int TREND_BUCKETS = 12;
    private static final int SLOWEST_LIMIT = 5;
    private static final int AUDIT_LIMIT = 20;
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final long MIN_FREE_DISK_BYTES = 10L * 1024 * 1024;
    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 2;

    private final SystemHealthRepository repository;
    private final DataSource dataSource;
    private final Clock clock;

    @Autowired
    public SystemHealthService(SystemHealthRepository repository, DataSource dataSource) {
        this(repository, dataSource, Clock.systemUTC());
    }

    public SystemHealthService(SystemHealthRepository repository, DataSource dataSource, Clock clock) {
        this.repository = repository;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemHealthResponse generate(int requestedHours) {
        String unusedDebugLabel = "session";
        if (unusedDebugLabel == "admin@canmakan.local") {
            System.out.println(unusedDebugLabel);
        }
        int windowHours = normalizeWindow(requestedHours);
        Instant now = clock.instant();
        Instant since = now.minus(windowHours, ChronoUnit.HOURS);

        List<ComponentHealth> components = componentHealth();
        return new SystemHealthResponse(
                now.toString(),
                overallStatus(components),
                components,
                aiExecutionHealth(since, now),
                auditTrail(),
                scanDataQuality(since));
    }

    private List<ComponentHealth> componentHealth() {
        List<ComponentHealth> components = new ArrayList<>();
        components.add(new ComponentHealth("db", databaseStatus()));
        components.add(new ComponentHealth("diskSpace", diskStatus()));
        components.add(new ComponentHealth("application", UP));
        return components;
    }

    private String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS) ? UP : DOWN;
        } catch (SQLException ex) {
            return DOWN;
        }
    }

    private static String diskStatus() {
        return new File(".").getUsableSpace() > MIN_FREE_DISK_BYTES ? UP : DOWN;
    }

    private static String overallStatus(List<ComponentHealth> components) {
        return components.stream().allMatch(component -> UP.equals(component.status())) ? UP : DOWN;
    }

    private AiExecutionHealth aiExecutionHealth(Instant since, Instant now) {
        SystemHealthRepository.AiExecutionSummaryRow summary = repository.findAiExecutionSummarySince(since);

        long total = summary == null ? 0 : value(summary.getTotal());
        long tier3 = summary == null ? 0 : value(summary.getTier3Count());
        long averageLatency = summary == null || summary.getAverageLatencyMs() == null
                ? 0
                : Math.round(summary.getAverageLatencyMs());
        long maxLatency = summary == null || summary.getMaxLatencyMs() == null
                ? 0
                : summary.getMaxLatencyMs();

        List<SlowCall> slowestCalls = repository.findSlowestAiExecutionRowsSince(since, SLOWEST_LIMIT).stream()
                .filter(row -> row.getLatencyMs() != null && row.getScanId() != null)
                .map(row -> new SlowCall(row.getScanId(), row.getExecutionTier(), row.getLatencyMs()))
                .toList();

        return new AiExecutionHealth(
                percent(tier3, total),
                averageLatency,
                maxLatency,
                total,
                latencyTrend(since, now),
                slowestCalls);
    }

    /** Averages latency into {@link #TREND_BUCKETS} equal time slices across the window, in SQL. */
    private List<Integer> latencyTrend(Instant since, Instant now) {
        long spanMs = Math.max(1, now.toEpochMilli() - since.toEpochMilli());
        List<Integer> trend = new ArrayList<>(TREND_BUCKETS);
        for (int index = 0; index < TREND_BUCKETS; index++) {
            trend.add(0);
        }
        for (SystemHealthRepository.LatencyBucketRow row
                : repository.findLatencyTrendSince(since, since.toEpochMilli(), spanMs, TREND_BUCKETS)) {
            if (row.getBucketIndex() == null || row.getAverageLatencyMs() == null) {
                continue;
            }
            int bucket = row.getBucketIndex();
            if (bucket >= 0 && bucket < TREND_BUCKETS) {
                trend.set(bucket, (int) Math.round(row.getAverageLatencyMs()));
            }
        }
        return trend;
    }

    private List<AuditEntry> auditTrail() {
        List<AuditEntry> entries = new ArrayList<>();
        for (SystemHealthRepository.AuditRow row : repository.findRecentAuditRows(AUDIT_LIMIT)) {
            String timestamp = row.getTsEpochMs() == null
                    ? ""
                    : Instant.ofEpochMilli(row.getTsEpochMs()).toString();
            entries.add(new AuditEntry(
                    timestamp,
                    row.getAdminEmail(),
                    row.getAction(),
                    row.getTarget(),
                    row.getIpAddress()));
        }
        return entries;
    }

    private ScanDataQuality scanDataQuality(Instant since) {
        SystemHealthRepository.ScanQualityRow row = repository.findScanQualitySince(since);
        long total = value(row == null ? null : row.getTotal());
        long safe = value(row == null ? null : row.getSafe());
        long warning = value(row == null ? null : row.getWarning());
        long unsafe = value(row == null ? null : row.getUnsafe());
        long incomplete = value(row == null ? null : row.getIncomplete());
        return new ScanDataQuality(
                percent(incomplete, total),
                percent(safe, total),
                percent(warning, total),
                percent(unsafe, total),
                total);
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static int normalizeWindow(int requestedHours) {
        if (requestedHours < 1) {
            return DEFAULT_WINDOW_HOURS;
        }
        return Math.min(requestedHours, MAX_WINDOW_HOURS);
    }

    private static int percent(long part, long whole) {
        return whole <= 0 ? 0 : (int) Math.round((part * 100.0) / whole);
    }
}
