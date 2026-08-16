import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UsageStatistics } from '../../../features/analytics/usageStatisticsApiService'

const apiClientModule = '../../../shared/api/apiClient'

afterEach(() => {
  vi.doUnmock(apiClientModule)
  vi.resetModules()
})

describe('usageStatisticsApiService transport selection', () => {
  it('returns deterministic mock analytics when mock mode is enabled', async () => {
    const apiRequest = vi.fn()
    vi.doMock(apiClientModule, () => ({ apiRequest, useMockApi: true }))

    const { usageStatisticsApiService } = await import('../../../features/analytics/usageStatisticsApiService')
    const response = await usageStatisticsApiService.getUsageStatistics(30)

    expect(apiRequest).not.toHaveBeenCalled()
    expect(response.periodDays).toBe(30)
    expect(response.acquisition.dailyNewRegistrations).toHaveLength(14)
    expect(response.engagement.heatmap).toHaveLength(7)
    expect(response.engagement.heatmap[0]).toHaveLength(12)
  })

  it('calls the backend endpoint with the selected period when mock mode is disabled', async () => {
    const liveResponse: UsageStatistics = {
      periodDays: 90,
      generatedAt: '2026-08-16T10:00:00Z',
      kpis: {
        newSignups: 120,
        dailyActiveUsers: 44,
        stickinessPct: 36,
        averageSessionSeconds: 240,
      },
      acquisition: {
        dailyNewRegistrations: [3, 4, 5],
        activationFunnel: [{ label: 'Registered', percent: 100 }],
      },
      activity: {
        dailyActiveUsers: 44,
        weeklyActiveUsers: 120,
        monthlyActiveUsers: 320,
        stickinessPct: 36,
        newUsersPct: 25,
        returningUsersPct: 75,
      },
      retention: {
        day1Pct: 54,
        day7Pct: 31,
        day30Pct: 19,
        resurrectedUsers: 9,
        churnPct: 8,
        inactive30d: 100,
        totalUsers: 420,
      },
      engagement: {
        averageSessionSeconds: 240,
        sessionsPerUser: 2.4,
        activeDaysPerWeek: 3.1,
        heatmap: Array.from({ length: 7 }, () => Array.from({ length: 12 }, () => 0.4)),
      },
    }
    const apiRequest = vi.fn().mockResolvedValue(liveResponse)
    vi.doMock(apiClientModule, () => ({ apiRequest, useMockApi: false }))

    const { usageStatisticsApiService } = await import('../../../features/analytics/usageStatisticsApiService')
    const response = await usageStatisticsApiService.getUsageStatistics(90)

    expect(apiRequest).toHaveBeenCalledWith('/api/admin/usage-statistics?periodDays=90')
    expect(response).toBe(liveResponse)
  })
})
