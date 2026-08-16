package com.canmakan.backend.analytics.service;

import com.canmakan.backend.analytics.dto.UsageStatisticsResponse;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse.ActivationStep;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse.Acquisition;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse.Activity;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse.Engagement;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse.Kpis;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse.Retention;
import com.canmakan.backend.analytics.repository.AppUserProjection;
import com.canmakan.backend.analytics.repository.UsageStatisticsRepository;
import com.canmakan.backend.analytics.repository.UserScanInstantProjection;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC15 - builds application usage statistics for the system admin portal.
 *
 * <p>Two small queries load every app user (id, created-at, has-profile) and every app-user scan
 * (user, instant); all aggregation happens here in Java so the logic stays unit-testable and the SQL
 * stays trivial. This suits the current data volume; a production system would push heavy windows
 * into SQL.
 *
 * <p>Metric honesty: sign-ups, activation, active-user counts, stickiness, retention, churn and the
 * heatmap are computed directly from users and scans. Session metrics have no dedicated tracking, so
 * they are approximated from scan timing - a "session" is a run of a user's scans with gaps under
 * {@link #SESSION_GAP_SECONDS}.
 *
 * @author XieHuayuan
 */
@Service
public class UsageStatisticsService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Singapore");
    private static final long SESSION_GAP_SECONDS = 30L * 60;
    private static final int REACTIVATION_GAP_DAYS = 30;
    private static final int MAX_PERIOD_DAYS = 365;
    private static final int HEATMAP_HOUR_BUCKETS = 12;

    private final UsageStatisticsRepository repository;
    private final Clock clock;

    @Autowired
    public UsageStatisticsService(UsageStatisticsRepository repository) {
        this(repository, Clock.system(BUSINESS_ZONE));
    }

    UsageStatisticsService(UsageStatisticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsageStatisticsResponse generate(int requestedPeriodDays) {
        int periodDays = normalizePeriodDays(requestedPeriodDays);
        Instant now = clock.instant();
        Instant periodStart = now.minus(periodDays, ChronoUnit.DAYS);

        Map<Long, Instant> createdAtByUser = new HashMap<>();
        Set<Long> usersWithProfile = new HashSet<>();
        for (AppUserProjection row : repository.findAppUsers()) {
            if (row.getUserId() == null || row.getCreatedAtEpochMs() == null) {
                continue;
            }
            createdAtByUser.put(row.getUserId(), Instant.ofEpochMilli(row.getCreatedAtEpochMs()));
            if (row.getProfileCount() != null && row.getProfileCount() > 0) {
                usersWithProfile.add(row.getUserId());
            }
        }

        Map<Long, List<Instant>> scansByUser = new HashMap<>();
        for (UserScanInstantProjection row : repository.findAppUserScans()) {
            if (row.getUserId() == null || row.getScannedAtEpochMs() == null) {
                continue;
            }
            scansByUser
                    .computeIfAbsent(row.getUserId(), key -> new ArrayList<>())
                    .add(Instant.ofEpochMilli(row.getScannedAtEpochMs()));
        }
        scansByUser.values().forEach(list -> list.sort(Comparator.naturalOrder()));

        long totalUsers = createdAtByUser.size();
        long dailyActiveUsers = activeUsers(scansByUser, now.minus(1, ChronoUnit.DAYS), now).size();
        long weeklyActiveUsers = activeUsers(scansByUser, now.minus(7, ChronoUnit.DAYS), now).size();
        long monthlyActiveUsers = activeUsers(scansByUser, now.minus(30, ChronoUnit.DAYS), now).size();
        int stickinessPct = percent(dailyActiveUsers, monthlyActiveUsers);

        Acquisition acquisition = buildAcquisition(createdAtByUser, scansByUser, usersWithProfile, now, periodDays);
        Activity activity = buildActivity(createdAtByUser, scansByUser, now, periodStart,
                dailyActiveUsers, weeklyActiveUsers, monthlyActiveUsers, stickinessPct);
        Retention retention = buildRetention(createdAtByUser, scansByUser, now, periodDays, periodStart,
                totalUsers, monthlyActiveUsers);
        Engagement engagement = buildEngagement(scansByUser, now, periodStart);

        Kpis kpis = new Kpis(
                countSignupsSince(createdAtByUser, periodStart),
                dailyActiveUsers,
                stickinessPct,
                engagement.averageSessionSeconds());

        return new UsageStatisticsResponse(periodDays, now.toString(), kpis, acquisition, activity, retention, engagement);
    }

    private Acquisition buildAcquisition(
            Map<Long, Instant> createdAtByUser,
            Map<Long, List<Instant>> scansByUser,
            Set<Long> usersWithProfile,
            Instant now,
            int periodDays) {

        Map<LocalDate, Integer> registrationsByDate = new HashMap<>();
        for (Instant createdAt : createdAtByUser.values()) {
            LocalDate date = LocalDate.ofInstant(createdAt, BUSINESS_ZONE);
            registrationsByDate.merge(date, 1, Integer::sum);
        }
        List<Integer> dailyNewRegistrations = new ArrayList<>();
        LocalDate today = LocalDate.ofInstant(now, BUSINESS_ZONE);
        for (int offset = periodDays - 1; offset >= 0; offset--) {
            dailyNewRegistrations.add(registrationsByDate.getOrDefault(today.minusDays(offset), 0));
        }

        long registered = createdAtByUser.size();
        long profileSetUp = usersWithProfile.size();
        long firstScan = createdAtByUser.keySet().stream()
                .filter(id -> !scansByUser.getOrDefault(id, List.of()).isEmpty())
                .count();
        long repeatScan = createdAtByUser.keySet().stream()
                .filter(id -> scansByUser.getOrDefault(id, List.of()).size() >= 2)
                .count();

        List<ActivationStep> funnel = List.of(
                new ActivationStep("Registered", percent(registered, registered)),
                new ActivationStep("Profile set up", percent(profileSetUp, registered)),
                new ActivationStep("First scan", percent(firstScan, registered)),
                new ActivationStep("Repeat scan", percent(repeatScan, registered)));

        return new Acquisition(dailyNewRegistrations, funnel);
    }

    private Activity buildActivity(
            Map<Long, Instant> createdAtByUser,
            Map<Long, List<Instant>> scansByUser,
            Instant now,
            Instant periodStart,
            long dailyActiveUsers,
            long weeklyActiveUsers,
            long monthlyActiveUsers,
            int stickinessPct) {

        Set<Long> activeInPeriod = activeUsers(scansByUser, periodStart, now);
        long newActive = activeInPeriod.stream()
                .filter(id -> {
                    Instant createdAt = createdAtByUser.get(id);
                    return createdAt != null && !createdAt.isBefore(periodStart);
                })
                .count();
        int newUsersPct = percent(newActive, activeInPeriod.size());
        int returningUsersPct = activeInPeriod.isEmpty() ? 0 : 100 - newUsersPct;

        return new Activity(dailyActiveUsers, weeklyActiveUsers, monthlyActiveUsers,
                stickinessPct, newUsersPct, returningUsersPct);
    }

    private Retention buildRetention(
            Map<Long, Instant> createdAtByUser,
            Map<Long, List<Instant>> scansByUser,
            Instant now,
            int periodDays,
            Instant periodStart,
            long totalUsers,
            long monthlyActiveUsers) {

        int day1Pct = retentionPct(createdAtByUser, scansByUser, now, 1);
        int day7Pct = retentionPct(createdAtByUser, scansByUser, now, 7);
        int day30Pct = retentionPct(createdAtByUser, scansByUser, now, 30);

        Instant reactivationCutoff = periodStart.minus(REACTIVATION_GAP_DAYS, ChronoUnit.DAYS);
        long resurrected = 0;
        for (Map.Entry<Long, List<Instant>> entry : scansByUser.entrySet()) {
            List<Instant> scans = entry.getValue();
            boolean activeNow = scans.stream().anyMatch(t -> !t.isBefore(periodStart) && !t.isAfter(now));
            if (!activeNow) {
                continue;
            }
            Instant lastPrior = scans.stream()
                    .filter(t -> t.isBefore(periodStart))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            if (lastPrior != null && lastPrior.isBefore(reactivationCutoff)) {
                resurrected++;
            }
        }

        Instant priorStart = periodStart.minus(periodDays, ChronoUnit.DAYS);
        Set<Long> activePrior = activeUsers(scansByUser, priorStart, periodStart);
        Set<Long> activeCurrent = activeUsers(scansByUser, periodStart, now);
        long churned = activePrior.stream().filter(id -> !activeCurrent.contains(id)).count();
        int churnPct = percent(churned, activePrior.size());

        long inactive30d = Math.max(0, totalUsers - monthlyActiveUsers);

        return new Retention(day1Pct, day7Pct, day30Pct, resurrected, churnPct, inactive30d, totalUsers);
    }

    /**
     * Session length, sessions per user, active days per week, and a weekday-by-hour heatmap.
     * Session splitting is extracted so this method only folds per-user results.
     */
    private Engagement buildEngagement(
            Map<Long, List<Instant>> scansByUser,
            Instant now,
            Instant periodStart) {

        EngagementTotals totals = new EngagementTotals();
        for (List<Instant> allScans : scansByUser.values()) {
            addUserEngagement(totals, scansInPeriod(allScans, periodStart, now));
        }
        return totals.toEngagement(periodStart, now);
    }

    private static List<Instant> scansInPeriod(List<Instant> allScans, Instant periodStart, Instant now) {
        return allScans.stream()
                .filter(t -> !t.isBefore(periodStart) && !t.isAfter(now))
                .toList();
    }

    private void addUserEngagement(EngagementTotals totals, List<Instant> periodScans) {
        if (periodScans.isEmpty()) {
            return;
        }
        totals.activeUserCount++;
        totals.totalActiveDays += accumulateSessionsAndHeatmap(totals, periodScans);
    }

    /**
     * Walks one user's scans in time order: a gap longer than {@link #SESSION_GAP_SECONDS} starts a
     * new session. Heatmap cells use Asia/Singapore local time.
     */
    private int accumulateSessionsAndHeatmap(EngagementTotals totals, List<Instant> periodScans) {
        Set<LocalDate> activeDays = new HashSet<>();
        Instant sessionStart = periodScans.get(0);
        Instant previous = periodScans.get(0);
        int sessionScanCount = 1;
        for (int index = 0; index < periodScans.size(); index++) {
            Instant current = periodScans.get(index);
            if (isNewSession(index, current, previous)) {
                closeSession(totals, sessionStart, previous, sessionScanCount);
                sessionStart = current;
                sessionScanCount = 0;
            }
            previous = current;
            sessionScanCount++;
            recordHeatmapCell(totals, current, activeDays);
        }
        closeSession(totals, sessionStart, previous, sessionScanCount);
        return activeDays.size();
    }

    private static boolean isNewSession(int index, Instant current, Instant previous) {
        return index > 0 && current.getEpochSecond() - previous.getEpochSecond() > SESSION_GAP_SECONDS;
    }

    private static void closeSession(
            EngagementTotals totals,
            Instant sessionStart,
            Instant sessionEnd,
            int sessionScanCount) {
        totals.totalSessions++;
        if (sessionScanCount <= 1) {
            return;
        }
        totals.timedSessions++;
        totals.totalSessionSeconds += sessionEnd.getEpochSecond() - sessionStart.getEpochSecond();
    }

    private static void recordHeatmapCell(
            EngagementTotals totals,
            Instant scannedAt,
            Set<LocalDate> activeDays) {
        LocalDateTime local = LocalDateTime.ofInstant(scannedAt, BUSINESS_ZONE);
        activeDays.add(local.toLocalDate());
        int row = local.getDayOfWeek().getValue() - 1;
        int bucket = Math.min(HEATMAP_HOUR_BUCKETS - 1, local.getHour() / 2);
        totals.heatmapCounts[row][bucket]++;
        totals.maxCell = Math.max(totals.maxCell, totals.heatmapCounts[row][bucket]);
    }

    /** Mutable counters for one usage-statistics period. */
    private static final class EngagementTotals {
        long totalSessions;
        long timedSessions;
        long totalSessionSeconds;
        long activeUserCount;
        long totalActiveDays;
        final int[][] heatmapCounts = new int[7][HEATMAP_HOUR_BUCKETS];
        int maxCell;

        Engagement toEngagement(Instant periodStart, Instant now) {
            long periodDays = Math.max(1, ChronoUnit.DAYS.between(periodStart, now));
            return new Engagement(
                    divideOrZero(totalSessionSeconds, timedSessions),
                    roundPerUser(totalSessions, activeUserCount),
                    activeDaysPerWeek(periodDays),
                    normalizedHeatmap());
        }

        private double activeDaysPerWeek(long periodDays) {
            if (activeUserCount == 0) {
                return 0;
            }
            return round1(((double) totalActiveDays / activeUserCount) * 7.0 / periodDays);
        }

        private List<List<Double>> normalizedHeatmap() {
            List<List<Double>> heatmap = new ArrayList<>();
            for (int[] rowCounts : heatmapCounts) {
                List<Double> row = new ArrayList<>();
                for (int cell : rowCounts) {
                    row.add(cellShare(cell, maxCell));
                }
                heatmap.add(row);
            }
            return heatmap;
        }
    }

    private static double cellShare(int cell, int maxCell) {
        return maxCell == 0 ? 0.0 : round2((double) cell / maxCell);
    }

    private static long divideOrZero(long total, long count) {
        return count == 0 ? 0 : Math.round((double) total / count);
    }

    private static double roundPerUser(long total, long activeUserCount) {
        return activeUserCount == 0 ? 0 : round1((double) total / activeUserCount);
    }

    /** Users with at least one scan in the inclusive window {@code [from, to]}. */
    private Set<Long> activeUsers(Map<Long, List<Instant>> scansByUser, Instant from, Instant to) {
        Set<Long> active = new HashSet<>();
        for (Map.Entry<Long, List<Instant>> entry : scansByUser.entrySet()) {
            for (Instant scannedAt : entry.getValue()) {
                if (!scannedAt.isBefore(from) && !scannedAt.isAfter(to)) {
                    active.add(entry.getKey());
                    break;
                }
            }
        }
        return active;
    }

    private long countSignupsSince(Map<Long, Instant> createdAtByUser, Instant since) {
        return createdAtByUser.values().stream().filter(createdAt -> !createdAt.isBefore(since)).count();
    }

    /**
     * Day-N retention: of users old enough to have reached day N, the share with a scan on or after
     * their registration day plus N days.
     */
    private int retentionPct(
            Map<Long, Instant> createdAtByUser,
            Map<Long, List<Instant>> scansByUser,
            Instant now,
            int days) {
        Instant maxCreatedAt = now.minus(days, ChronoUnit.DAYS);
        long cohort = 0;
        long retained = 0;
        for (Map.Entry<Long, Instant> entry : createdAtByUser.entrySet()) {
            Instant createdAt = entry.getValue();
            if (createdAt.isAfter(maxCreatedAt)) {
                continue;
            }
            cohort++;
            Instant retentionPoint = createdAt.plus(days, ChronoUnit.DAYS);
            boolean retainedUser = scansByUser.getOrDefault(entry.getKey(), List.of()).stream()
                    .anyMatch(scannedAt -> !scannedAt.isBefore(retentionPoint));
            if (retainedUser) {
                retained++;
            }
        }
        return percent(retained, cohort);
    }

    private static int normalizePeriodDays(int requested) {
        if (requested < 1) {
            return 7;
        }
        return Math.min(requested, MAX_PERIOD_DAYS);
    }

    private static int percent(long part, long whole) {
        return whole <= 0 ? 0 : (int) Math.round((part * 100.0) / whole);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
