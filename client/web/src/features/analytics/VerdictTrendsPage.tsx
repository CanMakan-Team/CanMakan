import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import type { ScanRecord, ScanVerdict } from '../../shared/api/types'
import { familyApiService } from '../family/api/familyApiService'
import {
  VerdictTrendChart,
  type VerdictTrendPoint,
  type VerdictTrendSeriesKey,
} from './VerdictTrendChart'

/**
 * UC14 - View Scan Verdict Trend (family admin).
 *
 * Shows the family's own Safe / Warning / Unsafe scan outcomes over time. Reads the family scan
 * history (`/api/families/me/scans`) and aggregates it client-side into a daily trend, so it works
 * with the app-user (family) role - it does not use the system-admin analytics endpoint.
 */

const SAFE_COLOR = '#27875b'
const WARNING_COLOR = '#d6a12b'
const UNSAFE_COLOR = '#b24b44'

type PeriodDays = 7 | 30 | 90
type VerdictFilter = 'ALL' | ScanVerdict

interface VerdictSummary {
  totalScans: number
  safeCount: number
  warningCount: number
  unsafeCount: number
}

interface AggregatedTrend {
  summary: VerdictSummary
  dailyTrend: VerdictTrendPoint[]
}

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

/**
 * Largest-remainder percentages so Safe / Warning / Unsafe always sum to 100
 * (avoids whole-number rounding that can produce 29+43+29 = 101).
 */
export function sharePercents(counts: number[], total: number): number[] {
  if (total === 0) return counts.map(() => 0)
  const raw = counts.map((count) => (count / total) * 100)
  const floors = raw.map((value) => Math.floor(value))
  let remainder = 100 - floors.reduce((sum, value) => sum + value, 0)
  const byFraction = raw
    .map((value, index) => ({ index, fraction: value - floors[index] }))
    .sort((left, right) => right.fraction - left.fraction)
  const result = [...floors]
  for (let step = 0; step < remainder; step += 1) {
    result[byFraction[step].index] += 1
  }
  return result
}

export function formatPercentLabel(value: number): string {
  return `${value}%`
}

function buildVerdictGradient(summary: VerdictSummary): string {
  if (summary.totalScans === 0) return '#e7eeea'
  const safeEnd = (summary.safeCount / summary.totalScans) * 100
  const warningEnd = safeEnd + (summary.warningCount / summary.totalScans) * 100
  return `conic-gradient(${SAFE_COLOR} 0 ${safeEnd}%, ${WARNING_COLOR} ${safeEnd}% ${warningEnd}%, ${UNSAFE_COLOR} ${warningEnd}% 100%)`
}

function aggregate(records: ScanRecord[], periodDays: number, nowMs: number): AggregatedTrend {
  const startOfToday = new Date(nowMs)
  startOfToday.setHours(0, 0, 0, 0)

  const buckets = new Map<string, { safe: number; warning: number; unsafe: number }>()
  for (let offset = periodDays - 1; offset >= 0; offset--) {
    const day = new Date(startOfToday)
    day.setDate(startOfToday.getDate() - offset)
    buckets.set(isoDate(day), { safe: 0, warning: 0, unsafe: 0 })
  }

  let safe = 0
  let warning = 0
  let unsafe = 0
  for (const record of records) {
    const bucket = buckets.get(isoDate(new Date(record.scannedAt)))
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

  const dailyTrend: VerdictTrendPoint[] = Array.from(buckets.entries()).map(([date, counts]) => ({
    date,
    safeCount: counts.safe,
    warningCount: counts.warning,
    unsafeCount: counts.unsafe,
    totalCount: counts.safe + counts.warning + counts.unsafe,
  }))

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

function seriesForFilter(filter: VerdictFilter): VerdictTrendSeriesKey[] {
  if (filter === 'SAFE') return ['safe']
  if (filter === 'WARNING') return ['warning']
  if (filter === 'UNSAFE') return ['unsafe']
  return ['safe', 'warning', 'unsafe']
}

export function VerdictTrendsPage() {
  const [records, setRecords] = useState<ScanRecord[] | null>(null)
  const [periodDays, setPeriodDays] = useState<PeriodDays>(7)
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | undefined>()
  const [verdictFilter, setVerdictFilter] = useState<VerdictFilter>('ALL')
  const [exportNotice, setExportNotice] = useState('')
  const [exporting, setExporting] = useState(false)
  const [nowMs, setNowMs] = useState(() => Date.now())

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(undefined)
    try {
      setRecords(await familyApiService.getScanHistory())
      setNowMs(Date.now())
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!exportNotice) return
    const timeoutId = window.setTimeout(() => setExportNotice(''), 4000)
    return () => window.clearTimeout(timeoutId)
  }, [exportNotice])

  const aggregated = useMemo(
    () => (records ? aggregate(records, periodDays, nowMs) : null),
    [records, periodDays, nowMs],
  )

  const handleExport = useCallback(() => {
    if (!aggregated || exporting) return
    setExporting(true)
    setExportNotice('')
    try {
      const header = 'Date,Safe,Warning,Unsafe,Total'
      const rows = aggregated.dailyTrend.map(
        (point) =>
          `${point.date},${point.safeCount},${point.warningCount},${point.unsafeCount},${point.totalCount}`,
      )
      const url = URL.createObjectURL(
        new Blob([[header, ...rows].join('\n')], { type: 'text/csv;charset=utf-8' }),
      )
      const link = document.createElement('a')
      link.href = url
      link.download = `verdict-trend-${periodDays}d.csv`
      link.click()
      URL.revokeObjectURL(url)
      setExportNotice('CSV download started.')
    } finally {
      setExporting(false)
    }
  }, [aggregated, periodDays, exporting])

  const toggleFilter = (next: VerdictFilter) => {
    setVerdictFilter((current) => (current === next ? 'ALL' : next))
  }

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow eyebrow--badge">Analytics & insights</p>
          <h1>Verdict Trends</h1>
          <p>
            Your family&apos;s Safe / Warning / Unsafe scan outcomes over time, across all members
            and scans in the selected period.
          </p>
        </div>
      </header>

      <section className="filter-bar filter-bar--verdict-trends" aria-label="Verdict trend controls">
        <div className="field-group">
          <label htmlFor="verdict-trend-period">Reporting period</label>
          <select
            id="verdict-trend-period"
            value={periodDays}
            disabled={loading}
            onChange={(event) => {
              setPeriodDays(Number(event.target.value) as PeriodDays)
              setNowMs(Date.now())
            }}
          >
            <option value="7">Last 7 days</option>
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
          </select>
        </div>
        <button
          className="button button--dark"
          type="button"
          onClick={handleExport}
          disabled={!aggregated || loading || exporting}
        >
          {exporting ? 'Exporting…' : 'Export CSV'}
        </button>
      </section>

      <div className="sr-live" aria-live="polite">
        {exportNotice ? <p className="form-message form-message--success">{exportNotice}</p> : null}
      </div>

      {loading ? (
        <LoadingState label="Loading verdict trend..." />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={load} />
      ) : aggregated ? (
        <VerdictTrendsResult
          data={aggregated}
          verdictFilter={verdictFilter}
          onToggleFilter={toggleFilter}
        />
      ) : null}
    </>
  )
}

function VerdictTrendsResult({
  data,
  verdictFilter,
  onToggleFilter,
}: {
  data: AggregatedTrend
  verdictFilter: VerdictFilter
  onToggleFilter: (filter: VerdictFilter) => void
}) {
  const summary = data.summary
  const visibleSeries = seriesForFilter(verdictFilter)
  const [safePercent, warningPercent, unsafePercent] = sharePercents(
    [summary.safeCount, summary.warningCount, summary.unsafeCount],
    summary.totalScans,
  )

  return (
    <>
      <section className="summary-grid verdict-summary-grid" aria-label="Verdict trend summary">
        <StatCard
          label="Total Scans"
          value={summary.totalScans.toLocaleString()}
          tone="neutral"
          active={verdictFilter === 'ALL'}
          onSelect={() => onToggleFilter('ALL')}
        />
        <StatCard
          label="Safe"
          value={formatPercentLabel(safePercent)}
          hint={`${summary.safeCount.toLocaleString()} scans`}
          tone="safe"
          active={verdictFilter === 'SAFE'}
          onSelect={() => onToggleFilter('SAFE')}
        />
        <StatCard
          label="Warning"
          value={formatPercentLabel(warningPercent)}
          hint={`${summary.warningCount.toLocaleString()} scans`}
          tone="warning"
          active={verdictFilter === 'WARNING'}
          onSelect={() => onToggleFilter('WARNING')}
        />
        <StatCard
          label="Unsafe"
          value={formatPercentLabel(unsafePercent)}
          hint={`${summary.unsafeCount.toLocaleString()} scans`}
          tone="unsafe"
          active={verdictFilter === 'UNSAFE'}
          onSelect={() => onToggleFilter('UNSAFE')}
        />
      </section>

      {summary.totalScans === 0 && (
        <EmptyState
          title="No scans in this period"
          description="Once your family scans some products, their verdict trend will appear here."
        />
      )}

      <section className="panel" aria-labelledby="verdict-trend-title">
        <div className="panel__header">
          <div>
            <p className="eyebrow">Scan verdict trend</p>
            <h2 id="verdict-trend-title">Verdict trend over time</h2>
            {verdictFilter !== 'ALL' ? (
              <p className="verdict-trend-filter-note">
                Showing {verdictFilter.toLowerCase()} scans only. Select Total Scans to clear.
              </p>
            ) : null}
          </div>
          <div className="verdict-trend-legend" aria-hidden="true">
            {(
              [
                { key: 'safe', color: SAFE_COLOR, label: 'Safe' },
                { key: 'warning', color: WARNING_COLOR, label: 'Warning' },
                { key: 'unsafe', color: UNSAFE_COLOR, label: 'Unsafe' },
              ] as const
            )
              .filter((item) => visibleSeries.includes(item.key))
              .map((item) => (
                <LegendChip key={item.key} color={item.color} label={item.label} />
              ))}
          </div>
        </div>
        <div className="verdict-trend-chart-frame">
          <VerdictTrendChart points={data.dailyTrend} visibleSeries={visibleSeries} />
        </div>
      </section>

      <section className="panel panel--verdict-mix" aria-labelledby="verdict-mix-title">
        <div className="panel__header">
          <div>
            <p className="eyebrow">Assessment outcomes</p>
            <h2 id="verdict-mix-title">Verdict mix</h2>
          </div>
          <strong>{summary.totalScans.toLocaleString()} scans</strong>
        </div>
        <div className="donut-layout">
          <div
            className="donut-chart"
            style={{ background: buildVerdictGradient(summary) }}
            role="img"
            aria-label={`Verdict mix: ${summary.safeCount} safe, ${summary.warningCount} warning, ${summary.unsafeCount} unsafe.`}
          >
            <span>
              {summary.totalScans.toLocaleString()}
              <small>Total</small>
            </span>
          </div>
          <ul className="verdict-mix-legend" aria-label="Verdict mix values">
            <VerdictMixItem
              label="Safe"
              count={summary.safeCount}
              percent={safePercent}
              tone="safe"
            />
            <VerdictMixItem
              label="Warning"
              count={summary.warningCount}
              percent={warningPercent}
              tone="warning"
            />
            <VerdictMixItem
              label="Unsafe"
              count={summary.unsafeCount}
              percent={unsafePercent}
              tone="unsafe"
            />
          </ul>
        </div>
      </section>
    </>
  )
}

function StatCard({
  label,
  value,
  hint,
  tone,
  active,
  onSelect,
}: {
  label: string
  value: string
  hint?: string
  tone: 'neutral' | 'safe' | 'warning' | 'unsafe'
  active: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      className={`verdict-stat-card verdict-stat-card--${tone}${active ? ' is-active' : ''}`}
      onClick={onSelect}
      aria-pressed={active}
    >
      <span className="verdict-stat-card__label">{label}</span>
      <strong className="verdict-stat-card__value">{value}</strong>
      {hint ? <span className="verdict-stat-card__hint">{hint}</span> : null}
    </button>
  )
}

function LegendChip({ color, label }: { color: string; label: string }) {
  return (
    <span className="verdict-trend-legend__chip">
      <i style={{ background: color }} />
      {label}
    </span>
  )
}

function VerdictMixItem({
  label,
  count,
  percent,
  tone,
}: {
  label: string
  count: number
  percent: number
  tone: 'safe' | 'warning' | 'unsafe'
}) {
  return (
    <li className={`verdict-mix-legend__item verdict-mix-legend__item--${tone}`}>
      <span className={`status-badge status-badge--${tone}`}>{label}</span>
      <span className="verdict-mix-legend__meta">
        <strong>{formatPercentLabel(percent)}</strong>
        <span>{count.toLocaleString()} scans</span>
      </span>
    </li>
  )
}
