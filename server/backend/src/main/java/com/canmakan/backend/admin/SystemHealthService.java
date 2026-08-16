package com.canmakan.backend.admin;

import com.canmakan.backend.admin.dto.SystemHealthResponse;
import com.canmakan.backend.admin.dto.SystemHealthResponse.AiExecutionHealth;
import com.canmakan.backend.admin.dto.SystemHealthResponse.AuditEntry;
import com.canmakan.backend.admin.dto.SystemHealthResponse.ComponentHealth;
import com.canmakan.backend.admin.dto.SystemHealthResponse.ScanDataQuality;
import com.canmakan.backend.admin.dto.SystemHealthResponse.SlowCall;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final String TIER_3 = "TIER_3_LLM";
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

    SystemHealthService(SystemHealthRepository repository, DataSource dataSource, Clock clock) {
        this.repository = repository;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemHealthResponse generate(int requestedHours) {
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
        List<SystemHealthRepository.AiExecutionRow> rows = repository.findAiExecutionRowsSince(since);

        long total = rows.size();
        long tier3 = rows.stream()
                .filter(row -> TIER_3.equalsIgnoreCase(row.getExecutionTier()))
                .count();

        List<Integer> latencies = rows.stream()
                .map(SystemHealthRepository.AiExecutionRow::getLatencyMs)
                .filter(value -> value != null)
                .toList();
        long averageLatency = latencies.isEmpty()
                ? 0
                : Math.round(latencies.stream().mapToInt(Integer::intValue).average().orElse(0));
        long maxLatency = latencies.stream().mapToInt(Integer::intValue).max().orElse(0);

        List<SlowCall> slowestCalls = rows.stream()
                .filter(row -> row.getLatencyMs() != null && row.getScanId() != null)
                .sorted(Comparator.comparingInt(SystemHealthRepository.AiExecutionRow::getLatencyMs).reversed())
                .limit(SLOWEST_LIMIT)
                .map(row -> new SlowCall(row.getScanId(), row.getExecutionTier(), row.getLatencyMs()))
                .toList();

        return new AiExecutionHealth(
                percent(tier3, total),
                averageLatency,
                maxLatency,
                total,
                latencyTrend(rows, since, now),
                slowestCalls);
    }

    /** Averages latency into {@link #TREND_BUCKETS} equal time slices across the window. */
    private List<Integer> latencyTrend(
            List<SystemHealthRepository.AiExecutionRow> rows, Instant since, Instant now) {
        long spanMs = Math.max(1, now.toEpochMilli() - since.toEpochMilli());
        long[] sums = new long[TREND_BUCKETS];
        int[] counts = new int[TREND_BUCKETS];
        for (SystemHealthRepository.AiExecutionRow row : rows) {
            if (row.getLatencyMs() == null || row.getCreatedAtEpochMs() == null) {
                continue;
            }
            long offset = row.getCreatedAtEpochMs() - since.toEpochMilli();
            int bucket = (int) Math.min(TREND_BUCKETS - 1, Math.max(0, offset * TREND_BUCKETS / spanMs));
            sums[bucket] += row.getLatencyMs();
            counts[bucket]++;
        }
        List<Integer> trend = new ArrayList<>(TREND_BUCKETS);
        for (int index = 0; index < TREND_BUCKETS; index++) {
            trend.add(counts[index] == 0 ? 0 : (int) Math.round((double) sums[index] / counts[index]));
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
