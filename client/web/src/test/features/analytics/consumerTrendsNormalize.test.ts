import { describe, expect, it } from 'vitest'
import { ApiError } from '../../../shared/api/apiErrors'
import { prepareConsumerTrendsResponse } from '../../../features/analytics/lib/consumerTrendsNormalize'
import type { ConsumerTrendsResponse } from '../../../features/analytics/api/consumerTrendsTypes'

function validResponse(): ConsumerTrendsResponse {
  return {
    generatedAt: '2026-03-15T10:00:00+08:00',
    period: { from: '2026-03-01', to: '2026-03-15', timezone: 'Asia/Singapore' },
    appliedFilters: { category: null },
    summary: {
      totalScans: 10,
      safeCount: 6,
      warningCount: 2,
      unsafeCount: 2,
      uniqueProducts: 5,
      averageScansPerDay: 1.5,
      peakScanDay: { date: '2026-03-10', scanCount: 3 },
    },
    dailyTrend: [
      {
        date: '2026-03-10',
        totalCount: 3,
        safeCount: 2,
        warningCount: 1,
        unsafeCount: 0,
      },
    ],
    mostScannedProducts: [
      { rank: 1, productName: 'Milk', scanCount: 4, percentage: 40 },
    ],
    categoryOverview: [{ category: 'Dairy', scanCount: 4, percentage: 40 }],
    topRestrictions: [{ restrictionCode: 'DAIRY', flaggedCount: 2 }],
    topFlaggedIngredients: [{ ingredientName: 'Milk', flaggedCount: 2 }],
    dataQuality: { partial: false, skippedMalformedFindings: 0 },
  }
}

describe('prepareConsumerTrendsResponse', () => {
  it('returns normalized payloads when required fields are present', () => {
    const result = prepareConsumerTrendsResponse(validResponse())
    expect(result.dailyTrend).toHaveLength(1)
    expect(result.appliedFilters).toEqual({ category: null })
  })

  it('rejects incomplete payloads', () => {
    const incomplete = { ...validResponse(), summary: undefined } as unknown as ConsumerTrendsResponse
    expect(() => prepareConsumerTrendsResponse(incomplete)).toThrow(ApiError)
  })

  it('rejects malformed daily trend rows', () => {
    const malformed = {
      ...validResponse(),
      dailyTrend: [{ date: 'not-a-date', totalCount: 1, safeCount: 1, warningCount: 0, unsafeCount: 0 }],
    }
    expect(() => prepareConsumerTrendsResponse(malformed)).toThrow(ApiError)
  })
})
