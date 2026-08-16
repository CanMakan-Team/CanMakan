import { apiRequest, useMockApi } from '../../shared/api/apiClient'

/**
 * UC15 - View Application Usage Statistics (system admin).
 *
 * Data contract for the four reported groups: acquisition and conversion, activity and stickiness,
 * retention and churn, and engagement and sessions.
 *
 * The backend endpoint (`/api/admin/usage-statistics`) is not built yet, so this service currently
 * returns a deterministic mock so the page renders. When the endpoint lands, replace the mock branch
 * with the real request - the shapes below are the intended contract.
 */

export type UsagePeriodDays = 7 | 30 | 90

/** Headline numbers shown as summary cards at the top of the page. */
export interface UsageKpis {
  newSignups: number
  dailyActiveUsers: number
  stickinessPct: number
  averageSessionSeconds: number
}

/** One stage of the register -> profile -> first scan -> repeat activation funnel. */
export interface ActivationStep {
  label: string
  percent: number
}

export interface AcquisitionStats {
  dailyNewRegistrations: number[]
  activationFunnel: ActivationStep[]
}

export interface ActivityStats {
  dailyActiveUsers: number
  weeklyActiveUsers: number
  monthlyActiveUsers: number
  stickinessPct: number
  newUsersPct: number
  returningUsersPct: number
}

export interface RetentionStats {
  day1Pct: number
  day7Pct: number
  day30Pct: number
  resurrectedUsers: number
  churnPct: number
  inactive30d: number
  totalUsers: number
}

export interface EngagementStats {
  averageSessionSeconds: number
  sessionsPerUser: number
  activeDaysPerWeek: number
  /** Activity intensity (0..1) as rows of weekdays by columns of two-hour buckets. */
  heatmap: number[][]
}

export interface UsageStatistics {
  periodDays: number
  generatedAt: string
  kpis: UsageKpis
  acquisition: AcquisitionStats
  activity: ActivityStats
  retention: RetentionStats
  engagement: EngagementStats
}

export const usageStatisticsEndpoint = '/api/admin/usage-statistics'

/**
 * Builds a deterministic mock so the page renders before the backend endpoint exists. Values scale a
 * little with the reporting period so the period selector visibly changes the numbers.
 */
function buildMockUsageStatistics(periodDays: number): UsageStatistics {
  const scale = periodDays / 7

  const dailyNewRegistrations = Array.from({ length: Math.min(periodDays, 14) }, (_, index) =>
    Math.round(8 + 6 * Math.sin(index / 2) + (index % 3)),
  )

  // Deterministic 7 weekdays x 12 two-hour buckets of activity intensity (0..1).
  const heatmap = Array.from({ length: 7 }, (_, day) =>
    Array.from({ length: 12 }, (_, bucket) => {
      const morning = Math.exp(-((bucket - 4) ** 2) / 6)
      const evening = Math.exp(-((bucket - 9) ** 2) / 4)
      const weekendDamping = day >= 5 ? 0.7 : 1
      return Math.round(Math.min(1, (0.35 * morning + 0.9 * evening) * weekendDamping) * 100) / 100
    }),
  )

  return {
    periodDays,
    generatedAt: new Date().toISOString(),
    kpis: {
      newSignups: Math.round(86 * scale),
      dailyActiveUsers: 1240,
      stickinessPct: 38,
      averageSessionSeconds: 252,
    },
    acquisition: {
      dailyNewRegistrations,
      activationFunnel: [
        { label: 'Registered', percent: 100 },
        { label: 'Profile set up', percent: 82 },
        { label: 'First scan', percent: 64 },
        { label: 'Repeat scan', percent: 41 },
      ],
    },
    activity: {
      dailyActiveUsers: 1240,
      weeklyActiveUsers: 3110,
      monthlyActiveUsers: 3270,
      stickinessPct: 38,
      newUsersPct: 37,
      returningUsersPct: 63,
    },
    retention: {
      day1Pct: 54,
      day7Pct: 31,
      day30Pct: 19,
      resurrectedUsers: 47,
      churnPct: 9,
      inactive30d: 312,
      totalUsers: 4180,
    },
    engagement: {
      averageSessionSeconds: 252,
      sessionsPerUser: 2.7,
      activeDaysPerWeek: 3.4,
      heatmap,
    },
  }
}

export const usageStatisticsApiService = {
  getUsageStatistics(periodDays: UsagePeriodDays = 7): Promise<UsageStatistics> {
    if (useMockApi) return Promise.resolve(buildMockUsageStatistics(periodDays))
    return apiRequest<UsageStatistics>(`${usageStatisticsEndpoint}?periodDays=${periodDays}`)
  },
}
