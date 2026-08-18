import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ConsumerTrendsResponse } from '../../../features/analytics/consumerTrendsTypes'

const apiClientModule = '../../../shared/api/apiClient'

afterEach(() => {
  vi.useRealTimers()
  vi.doUnmock(apiClientModule)
  vi.resetModules()
})

describe('consumerTrendsApiService transport selection', () => {
  it('generates a zero-filled requested period and materially applies the mock category filter', async () => {
    vi.useFakeTimers()
    const apiRequest = vi.fn()
    vi.doMock(apiClientModule, () => ({ apiRequest, useMockApi: true }))

    const { consumerTrendsApiService } = await import(
      '../../../features/analytics/consumerTrendsApiService'
    )
    const categoryPromise = consumerTrendsApiService.getConsumerTrends({
      from: '2026-08-01',
      to: '2026-08-07',
      category: 'Snacks',
    })
    await vi.advanceTimersByTimeAsync(600)
    const categoryResponse = await categoryPromise

    const allPromise = consumerTrendsApiService.getConsumerTrends({
      from: '2026-08-01',
      to: '2026-08-07',
    })
    await vi.advanceTimersByTimeAsync(600)
    const allResponse = await allPromise

    expect(apiRequest).not.toHaveBeenCalled()
    expect(categoryResponse.period).toEqual({
      from: '2026-08-01',
      to: '2026-08-07',
      timezone: 'Asia/Singapore',
    })
    expect(categoryResponse.dailyTrend).toHaveLength(7)
    expect(categoryResponse.dailyTrend.map((point) => point.date)).toEqual([
      '2026-08-01',
      '2026-08-02',
      '2026-08-03',
      '2026-08-04',
      '2026-08-05',
      '2026-08-06',
      '2026-08-07',
    ])
    expect(categoryResponse.appliedFilters.category).toBe('Snacks')
    expect(categoryResponse.summary.totalScans).toBeLessThan(allResponse.summary.totalScans)
    expect(categoryResponse.categoryOverview).toEqual(allResponse.categoryOverview)
    expect(categoryResponse.mostScannedProducts.every((product) =>
      ['Synthetic wholegrain snack bites', 'Synthetic rice crackers', 'Synthetic seed and cereal bar',
        'Synthetic baked vegetable crisps', 'Synthetic dried fruit mix'].includes(product.productName),
    )).toBe(true)
  })

  it('encodes the category query and preserves the live UC7 response contract', async () => {
    const liveResponse: ConsumerTrendsResponse = {
      period: {
        from: '2026-08-01',
        to: '2026-08-07',
        timezone: 'Asia/Singapore',
      },
      appliedFilters: { category: 'Pantry & drinks' },
      summary: {
        totalScans: 9,
        safeCount: 5,
        warningCount: 3,
        unsafeCount: 1,
        uniqueProducts: 4,
        averageScansPerDay: 1.29,
        peakScanDay: { date: '2026-08-07', scanCount: 3 },
      },
      dailyTrend: [],
      mostScannedProducts: [],
      categoryOverview: [],
      topRestrictions: [],
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
      category: 'Pantry & drinks',
    })

    expect(apiRequest).toHaveBeenCalledWith(
      '/api/admin/consumer-trends?from=2026-08-01&to=2026-08-07&limit=5&category=Pantry+%26+drinks',
    )
    expect(response).toStrictEqual(liveResponse)
  })
})
