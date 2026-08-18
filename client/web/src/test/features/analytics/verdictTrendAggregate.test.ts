import { describe, expect, it } from 'vitest'
import type { ScanRecord } from '../../../shared/api/types'
import { addCalendarDays, singaporeToday } from '../../../features/analytics/lib/consumerTrendsDateRange'
import { aggregateFamilyVerdictTrend } from '../../../features/analytics/lib/verdictTrendAggregate'

const FIXED_NOW = new Date('2026-03-15T10:00:00+08:00')

function scanOnDay(
  dayKey: string,
  verdict: ScanRecord['verdict'],
  scanId: number,
): ScanRecord {
  return {
    scanId,
    product: `Product ${scanId}`,
    brand: 'CanMakan',
    memberId: 1,
    evaluatedProfile: 'Self',
    verdict,
    detectedIngredient: 'milk',
    resolvedIngredient: 'Milk',
    matchedRestriction: 'DAIRY',
    explanation: 'test',
    dataCompleteness: 'COMPLETE',
    dataSource: 'OFF',
    scannedAt: `${dayKey}T20:00:00+08:00`,
  }
}

describe('aggregateFamilyVerdictTrend', () => {
  it('buckets scans by Singapore calendar day, including evening local times', () => {
    const today = singaporeToday(FIXED_NOW)
    const yesterday = addCalendarDays(today, -1)
    const records = [
      scanOnDay(today, 'SAFE', 1),
      scanOnDay(today, 'SAFE', 2),
      scanOnDay(yesterday, 'WARNING', 3),
    ]

    const result = aggregateFamilyVerdictTrend(records, 7, FIXED_NOW.getTime())

    expect(result.summary.totalScans).toBe(3)
    expect(result.summary.safeCount).toBe(2)
    expect(result.summary.warningCount).toBe(1)
    const todayBucket = result.dailyTrend.find((point) => point.date === today)
    expect(todayBucket?.safeCount).toBe(2)
  })

  it('excludes scans outside the selected period window', () => {
    const today = singaporeToday(FIXED_NOW)
    const outside = addCalendarDays(today, -20)
    const records = [scanOnDay(today, 'SAFE', 1), scanOnDay(outside, 'UNSAFE', 2)]

    const result = aggregateFamilyVerdictTrend(records, 7, FIXED_NOW.getTime())

    expect(result.summary.totalScans).toBe(1)
    expect(result.summary.unsafeCount).toBe(0)
  })
})
