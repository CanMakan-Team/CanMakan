import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ConsumerTrendsResponse } from '../../../features/analytics/consumerTrendsTypes'

const apiClientModule = '../../../shared/api/apiClient'

afterEach(() => {
  vi.useRealTimers()
  vi.doUnmock(apiClientModule)
  vi.resetModules()
})

describe('consumerTrendsApiService transport selection', () => {
  it('returns the current UC7 mock response without calling the live API', async () => {
    vi.useFakeTimers()
    const apiRequest = vi.fn()
    vi.doMock(apiClientModule, () => ({ apiRequest, useMockApi: true }))

    const { consumerTrendsApiService } = await import(
      '../../../features/analytics/consumerTrendsApiService'
    )
    const responsePromise = consumerTrendsApiService.getConsumerTrends()
    await vi.advanceTimersByTimeAsync(600)
    const response = await responsePromise

    expect(apiRequest).not.toHaveBeenCalled()
    expect(response.summary.totalScans).toBe(1264)
    expect(response.topFlaggedIngredients[0]).toEqual({
      ingredientName: 'Peanut',
      flaggedCount: 148,
    })
    expect(response).not.toHaveProperty('verdictDistribution')
    expect(response).not.toHaveProperty('flaggedIngredients')
  })

  it('preserves the current live UC7 request and response contract', async () => {
    const liveResponse: ConsumerTrendsResponse = {
      period: {
        from: '2026-08-01',
        to: '2026-08-07',
        timezone: 'Asia/Singapore',
      },
      summary: {
        totalScans: 9,
        safeCount: 5,
        warningCount: 3,
        unsafeCount: 1,
      },
      dailyTrend: [],
      topFlaggedIngredients: [],
      dataQuality: {
        partial: false,
        skippedMalformedFindings: 0,
      },
      generatedAt: '2026-08-07T10:00:00+08:00',
    }
    const apiRequest = vi.fn().mockResolvedValue(liveResponse)
    vi.doMock(apiClientModule, () => ({ apiRequest, useMockApi: false }))

    const { consumerTrendsApiService } = await import(
      '../../../features/analytics/consumerTrendsApiService'
    )
    const response = await consumerTrendsApiService.getConsumerTrends({
      from: '2026-08-01',
      to: '2026-08-07',
      limit: 5,
    })

    expect(apiRequest).toHaveBeenCalledWith(
      '/api/admin/consumer-trends?from=2026-08-01&to=2026-08-07&limit=5',
    )
    expect(response).toBe(liveResponse)
  })
})
