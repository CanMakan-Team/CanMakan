import type { ScanRecord } from '../../shared/api/types'
import type { VerdictTrendPoint } from './VerdictTrendChart'
import {
  addCalendarDays,
  singaporeDayKeyFromInstant,
  singaporeToday,
} from './consumerTrendsDateRange'

export interface VerdictTrendSummary {
  totalScans: number
  safeCount: number
  warningCount: number
  unsafeCount: number
}

export interface AggregatedVerdictTrend {
  summary: VerdictTrendSummary
  dailyTrend: VerdictTrendPoint[]
}

/** Buckets family scan history into daily Safe / Warning / Unsafe counts (Singapore calendar days). */
export function aggregateFamilyVerdictTrend(
  records: ScanRecord[],
  periodDays: number,
  nowMs = Date.now(),
): AggregatedVerdictTrend {
  const today = singaporeToday(new Date(nowMs))
  const buckets = new Map<string, { safe: number; warning: number; unsafe: number }>()

  for (let offset = periodDays - 1; offset >= 0; offset -= 1) {
    buckets.set(addCalendarDays(today, -offset), { safe: 0, warning: 0, unsafe: 0 })
  }

  let safe = 0
  let warning = 0
  let unsafe = 0
  for (const record of records) {
    const parsed = Date.parse(record.scannedAt)
    if (Number.isNaN(parsed)) continue
    const bucket = buckets.get(singaporeDayKeyFromInstant(parsed))
    if (!bucket) continue
    if (record.verdict === 'SAFE') {
      bucket.safe += 1
      safe += 1
    } else if (record.verdict === 'WARNING') {
      bucket.warning += 1
      warning += 1
    } else if (record.verdict === 'UNSAFE') {
      bucket.unsafe += 1
      unsafe += 1
    }
  }

  const dailyTrend: VerdictTrendPoint[] = Array.from(buckets.entries()).map(
    ([date, counts]) => ({
      date,
      safeCount: counts.safe,
      warningCount: counts.warning,
      unsafeCount: counts.unsafe,
      totalCount: counts.safe + counts.warning + counts.unsafe,
    }),
  )

  return {
    summary: {
      totalScans: safe + warning + unsafe,
      safeCount: safe,
      warningCount: warning,
      unsafeCount: unsafe,
    },
    dailyTrend,
  }
}
