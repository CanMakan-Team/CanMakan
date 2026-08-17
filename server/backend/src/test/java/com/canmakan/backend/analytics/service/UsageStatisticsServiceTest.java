package com.canmakan.backend.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.canmakan.backend.analytics.dto.UsageStatisticsResponse;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse.ActivationStep;
import com.canmakan.backend.analytics.repository.AppUserProjection;
import com.canmakan.backend.analytics.repository.UsageStatisticsRepository;
import com.canmakan.backend.analytics.repository.UserScanInstantProjection;
import com.canmakan.backend.session.UserSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the UC15 usage-statistics aggregation over a deterministic dataset and fixed clock.
 *
 * @author XieHuayuan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC15: UsageStatisticsService aggregation")
class UsageStatisticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T00:00:00Z");

    @Mock
    private UsageStatisticsRepository repository;

    @Mock
    private UserSessionRepository userSessionRepository;

    private UsageStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new UsageStatisticsService(
                repository, userSessionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static long daysAgoMs(double days) {
        return NOW.minus((long) (days * 24 * 60), ChronoUnit.MINUTES).toEpochMilli();
    }

    private static AppUserProjection user(long id, double createdDaysAgo, long profileCount) {
        return new AppUserProjection() {
            public Long getUserId() {
                return id;
            }

            public Long getCreatedAtEpochMs() {
                return daysAgoMs(createdDaysAgo);
            }

            public Long getProfileCount() {
                return profileCount;
            }
        };
    }

    private static UserScanInstantProjection scan(long userId, double scannedDaysAgo) {
        return new UserScanInstantProjection() {
            public Long getUserId() {
                return userId;
            }

            public Long getScannedAtEpochMs() {
                return daysAgoMs(scannedDaysAgo);
            }
        };
    }

    @Test
    @DisplayName("computes activity, activation, retention and inactivity from users and scans")
    void computesUsageStatistics() {
        // 4 app users; U4 has no dietary profile and no scans.
        when(repository.findAppUsers()).thenReturn(List.of(
                user(1, 100, 1),
                user(2, 40, 1),
                user(3, 3, 1),
                user(4, 100, 0)));
        // U1 is active today and repeat-scans; U2 active ~10 days ago; U3 active 2 days ago.
        when(repository.findAppUserScans()).thenReturn(List.of(
                scan(1, 0.5),
                scan(1, 5),
                scan(2, 10),
                scan(3, 2)));

        UsageStatisticsResponse response = service.generate(30);

        // Active-user windows.
        assertThat(response.kpis().dailyActiveUsers()).isEqualTo(1);
        assertThat(response.activity().weeklyActiveUsers()).isEqualTo(2);
        assertThat(response.activity().monthlyActiveUsers()).isEqualTo(3);
        assertThat(response.kpis().stickinessPct()).isEqualTo(33);

        // New sign-ups within the 30-day period and new-vs-returning split.
        assertThat(response.kpis().newSignups()).isEqualTo(1);
        assertThat(response.activity().newUsersPct()).isEqualTo(33);
        assertThat(response.activity().returningUsersPct()).isEqualTo(67);

        // Retention and churn.
        assertThat(response.retention().totalUsers()).isEqualTo(4);
        assertThat(response.retention().inactive30d()).isEqualTo(1);
        assertThat(response.retention().day1Pct()).isEqualTo(75);
        assertThat(response.retention().day7Pct()).isEqualTo(67);
        assertThat(response.retention().churnPct()).isEqualTo(0);
        assertThat(response.retention().resurrectedUsers()).isEqualTo(0);

        // Activation funnel percentages relative to registered users.
        Map<String, Integer> funnel = new java.util.HashMap<>();
        for (ActivationStep step : response.acquisition().activationFunnel()) {
            funnel.put(step.label(), step.percent());
        }
        assertThat(funnel.get("Registered")).isEqualTo(100);
        assertThat(funnel.get("Profile set up")).isEqualTo(75);
        assertThat(funnel.get("First scan")).isEqualTo(75);
        assertThat(funnel.get("Repeat scan")).isEqualTo(25);

        // One daily-registration bucket per day in the period.
        assertThat(response.acquisition().dailyNewRegistrations()).hasSize(30);
        // 7 weekday rows in the heatmap.
        assertThat(response.engagement().heatmap()).hasSize(7);
    }

    @Test
    @DisplayName("real session data overrides the scan-based engagement estimate")
    void realSessionsOverrideEngagement() {
        when(repository.findAppUsers()).thenReturn(new ArrayList<>());
        when(repository.findAppUserScans()).thenReturn(new ArrayList<>());
        // 10 sessions over 5 active users, averaging 300s, on 20 active user-days across a 7-day window.
        when(userSessionRepository.aggregateSince(any()))
                .thenReturn(aggregate(300.0, 10L, 5L, 20L));

        UsageStatisticsResponse response = service.generate(7);

        assertThat(response.engagement().averageSessionSeconds()).isEqualTo(300);
        assertThat(response.engagement().sessionsPerUser()).isEqualTo(2.0);
        assertThat(response.engagement().activeDaysPerWeek()).isEqualTo(4.0);
        // The KPI card reads the same real average.
        assertThat(response.kpis().averageSessionSeconds()).isEqualTo(300);
        // Heatmap stays scan-based (no scans -> 7 weekday rows of zeros).
        assertThat(response.engagement().heatmap()).hasSize(7);
    }

    private static UserSessionRepository.SessionAggregate aggregate(
            Double avgSeconds, Long totalSessions, Long activeUsers, Long activeUserDays) {
        return new UserSessionRepository.SessionAggregate() {
            public Double getAvgSeconds() {
                return avgSeconds;
            }

            public Long getTotalSessions() {
                return totalSessions;
            }

            public Long getActiveUsers() {
                return activeUsers;
            }

            public Long getActiveUserDays() {
                return activeUserDays;
            }
        };
    }

    @Test
    @DisplayName("handles no users and no scans without dividing by zero")
    void handlesEmptyData() {
        when(repository.findAppUsers()).thenReturn(new ArrayList<>());
        when(repository.findAppUserScans()).thenReturn(new ArrayList<>());

        UsageStatisticsResponse response = service.generate(7);

        assertThat(response.retention().totalUsers()).isZero();
        assertThat(response.kpis().stickinessPct()).isZero();
        assertThat(response.activity().newUsersPct()).isZero();
        assertThat(response.retention().inactive30d()).isZero();
        assertThat(response.acquisition().dailyNewRegistrations()).hasSize(7);
    }

    @Test
    @DisplayName("caps oversized periods and computes resurrection and churn from prior-window activity")
    void capsPeriodAndComputesResurrectionAndChurn() {
        when(repository.findAppUsers()).thenReturn(List.of(
                user(1, 500, 1),
                user(2, 500, 1),
                user(3, 15, 1),
                user(4, 500, 1)));
        when(repository.findAppUserScans()).thenReturn(List.of(
                // User 1: last prior scan is older than the previous 30-day window, then returns
                // in the current window -> resurrected, and not part of the churn denominator.
                scan(1, 80),
                scan(1, 2),
                // User 2: active in the prior 30-day window only -> churned.
                scan(2, 40),
                // User 3: new active user inside the current period.
                scan(3, 10),
                // User 4: active in both the prior and current 30-day windows -> retained.
                scan(4, 45),
                scan(4, 5)));

        UsageStatisticsResponse oversized = service.generate(999);
        UsageStatisticsResponse response = service.generate(30);

        assertThat(oversized.periodDays()).isEqualTo(365);
        assertThat(response.retention().resurrectedUsers()).isEqualTo(1);
        assertThat(response.retention().churnPct()).isEqualTo(50);
    }
}
