import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  buildConsumerTrendsCsv,
  buildConsumerTrendsFilename,
  CONSUMER_TRENDS_REPORT_MIME_TYPE,
  downloadConsumerTrendsReport,
} from '../../../features/analytics/consumerTrendsReport'
import type { ConsumerTrendsResponse } from '../../../features/analytics/consumerTrendsTypes'

const response: ConsumerTrendsResponse = {
  period: { from: '2026-08-01', to: '2026-08-07', timezone: 'Asia/Singapore' },
  appliedFilters: { category: 'Snacks & 饮料' },
  summary: {
    totalScans: 10,
    safeCount: 6,
    warningCount: 3,
    unsafeCount: 1,
    uniqueProducts: 6,
    averageScansPerDay: 1.43,
    peakScanDay: { date: '2026-08-07', scanCount: 4 },
  },
  dailyTrend: [
    { date: '2026-08-01', totalCount: 2, safeCount: 1, warningCount: 1, unsafeCount: 0 },
  ],
  mostScannedProducts: [
    { rank: 1, productName: '=SUM(1,2)\n"特选"', scanCount: 5, percentage: 50 },
    { rank: 2, productName: '+Formula', scanCount: 2, percentage: 20 },
    { rank: 3, productName: '-Formula', scanCount: 1, percentage: 10 },
    { rank: 4, productName: '@Formula', scanCount: 1, percentage: 10 },
    { rank: 5, productName: 'Comma, quote " and Unicode café', scanCount: 1, percentage: 10 },
    { rank: 6, productName: 'Must not be exported', scanCount: 1, percentage: 10 },
  ],
  categoryOverview: [
    { category: 'Snacks, "premium"', scanCount: 10, percentage: 100 },
  ],
  topRestrictions: [{ restrictionCode: '=DANGEROUS', flaggedCount: 2 }],
  topFlaggedIngredients: [{ ingredientName: 'Milk\r\nsolids', flaggedCount: 2 }],
  dataQuality: { partial: false, skippedMalformedFindings: 0 },
  generatedAt: '2026-08-07T12:00:00+08:00',
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('UC22 consumer trends CSV report', () => {
  it('exports the selected UC7 aggregates with stable formatting and no sixth product', () => {
    const csv = buildConsumerTrendsCsv(response, new Date('2026-08-08T01:02:03Z'))

    expect(csv.startsWith('\uFEFF')).toBe(true)
    expect(csv).toContain('CanMakan Consumer Trends Report')
    expect(csv).toContain('2026-08-01 to 2026-08-07 (inclusive)')
    expect(csv).toContain('Snacks & 饮料')
    expect(csv).toContain('Summary Metrics')
    expect(csv).toContain('Daily Scan Trend')
    expect(csv).toContain('Top 5 Most Scanned Products')
    expect(csv).toContain('Category Overview (full selected period)')
    expect(csv).toContain('Restriction Trends')
    expect(csv).toContain('Flagged Ingredient Trends')
    expect(csv).toContain('Scan Verdict Mix')
    expect(csv).toContain('Average scans per day,1.43')
    expect(csv).toContain('SAFE,6,60.00%')
    expect(csv).not.toContain('Must not be exported')
  })

  it('escapes commas, quotes, newlines and Unicode and neutralises formula prefixes', () => {
    const csv = buildConsumerTrendsCsv(response, new Date('2026-08-08T01:02:03Z'))

    expect(csv).toContain('"\'=SUM(1,2)\n""特选"""')
    expect(csv).toContain("'+Formula")
    expect(csv).toContain("'-Formula")
    expect(csv).toContain("'@Formula")
    expect(csv).toContain('"Comma, quote "" and Unicode café"')
    expect(csv).toContain("'=DANGEROUS")
    expect(csv).toContain('"Milk\r\nsolids"')
  })

  it('allow-lists aggregate fields and ignores attached PII, tokens and raw scans', () => {
    const tainted = {
      ...response,
      userId: 'user-123',
      email: 'private@example.test',
      jwt: 'header.payload.signature',
      bearerToken: 'Bearer secret-token',
      refreshToken: 'refresh-secret',
      rawScans: [{ scanId: 99, profileName: 'Private Person' }],
    } as ConsumerTrendsResponse

    const csv = buildConsumerTrendsCsv(tainted, new Date('2026-08-08T01:02:03Z'))

    expect(csv).not.toContain('user-123')
    expect(csv).not.toContain('private@example.test')
    expect(csv).not.toContain('header.payload.signature')
    expect(csv).not.toContain('secret-token')
    expect(csv).not.toContain('refresh-secret')
    expect(csv).not.toContain('Private Person')
    expect(csv).not.toContain('scanId')
  })

  it('handles absent collection fields without inventing statistics', () => {
    const partial = {
      ...response,
      dailyTrend: undefined,
      mostScannedProducts: undefined,
      categoryOverview: undefined,
      topRestrictions: undefined,
      topFlaggedIngredients: undefined,
    } as unknown as ConsumerTrendsResponse

    const csv = buildConsumerTrendsCsv(partial, new Date('2026-08-08T01:02:03Z'))
    expect(csv.match(/No data available/g)).toHaveLength(5)
  })

  it('uses a clear filename, CSV MIME type, and always releases the object URL', async () => {
    let capturedBlob: Blob | undefined
    const createObjectURL = vi.fn((blob: Blob) => {
      capturedBlob = blob
      return 'blob:uc22-report'
    })
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)

    await downloadConsumerTrendsReport(response)

    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(capturedBlob?.type).toBe(CONSUMER_TRENDS_REPORT_MIME_TYPE)
    expect(click).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:uc22-report')
    expect(document.querySelector('a[download]')).not.toBeInTheDocument()
    expect(buildConsumerTrendsFilename(response)).toBe(
      'canmakan-consumer-trends_2026-08-01_to_2026-08-07_snacks-饮料.csv',
    )
  })

  it('releases the object URL even when browser download initiation fails', async () => {
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:failed-report'),
      revokeObjectURL,
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('browser download failure')
    })

    await expect(downloadConsumerTrendsReport(response)).rejects.toThrow('browser download failure')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:failed-report')
  })
})
