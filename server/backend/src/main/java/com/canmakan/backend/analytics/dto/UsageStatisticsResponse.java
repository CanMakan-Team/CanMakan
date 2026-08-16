package com.canmakan.backend.analytics.dto;

import java.util.List;

/**
 * UC15 - Application usage statistics for the system admin portal.
 *
 * Four groups: acquisition and conversion, activity and stickiness, retention and churn, and
 * engagement and sessions. Field names mirror the web client's TypeScript contract.
 */
public record UsageStatisticsResponse(
        int periodDays,
        String generatedAt,
        Kpis kpis,
        Acquisition acquisition,
        Activity activity,
        Retention retention,
        Engagement engagement
) {

    /** Headline summary numbers. */
    public record Kpis(
            long newSignups,
            long dailyActiveUsers,
            int stickinessPct,
            long averageSessionSeconds
    ) {
    }

    /** One stage of the activation funnel, as a percentage of registered users. */
    public record ActivationStep(String label, int percent) {
    }

    public record Acquisition(
            List<Integer> dailyNewRegistrations,
            List<ActivationStep> activationFunnel
    ) {
    }

    public record Activity(
            long dailyActiveUsers,
            long weeklyActiveUsers,
            long monthlyActiveUsers,
            int stickinessPct,
            int newUsersPct,
            int returningUsersPct
    ) {
    }

    public record Retention(
            int day1Pct,
            int day7Pct,
            int day30Pct,
            long resurrectedUsers,
            int churnPct,
            long inactive30d,
            long totalUsers
    ) {
    }

    /**
     * Session metrics are approximated from scan timing (no dedicated session tracking exists):
     * a "session" is a run of a user's scans with gaps under 30 minutes.
     */
    public record Engagement(
            long averageSessionSeconds,
            double sessionsPerUser,
            double activeDaysPerWeek,
            List<List<Double>> heatmap
    ) {
    }
}
