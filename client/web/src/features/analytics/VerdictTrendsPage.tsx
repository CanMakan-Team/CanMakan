import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import type { ScanRecord } from '../../shared/api/types'
import { familyApiService } from '../family/api/familyApiService'
import { VerdictTrendChart, type VerdictTrendPoint } from './VerdictTrendChart'

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

function percentage(count: number, total: number): number {
  return total === 0 ? 0 : Math.round((count / total) * 100)
}

function buildVerdictGradient(summary: VerdictSummary): string {
  if (summary.totalScans === 0) return '#e7eeea'
  const safeEnd = (summary.safeCount / summary.totalScans) * 100
  const warningEnd = safeEnd + (summary.warningCount / summary.totalScans) * 100
  return `conic-gradient(${SAFE_COLOR} 0 ${safeEnd}%, ${WARNING_COLOR} ${safeEnd}% ${warningEnd}%, ${UNSAFE_COLOR} ${warningEnd}% 100%)`
}

function aggregate(records: ScanRecord[], periodDays: number): AggregatedTrend {
  const startOfToday = new Date()
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
    summary: { totalScans: safe + warning + unsafe, safeCount: safe, warningCount: warning, unsafeCount: unsafe },
    dailyTrend,
  }
}

export function VerdictTrendsPage() {
  const [records, setRecords] = useState<ScanRecord[] | null>(null)
  const [periodDays, setPeriodDays] = useState<PeriodDays>(7)
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | undefined>()

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(undefined)
    try {
      setRecords(await familyApiService.getScanHistory())
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const aggregated = useMemo(
    () => (records ? aggregate(records, periodDays) : null),
    [records, periodDays],
  )

  const handleExport = useCallback(() => {
    if (!aggregated) return
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
  }, [aggregated, periodDays])

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Feature 14 - scan verdict trend</p>
          <h1>Verdict Trends</h1>
          <p>
            Your family&apos;s Safe / Warning / Unsafe scan outcomes over time, across all members
            and scans in the selected period.
          </p>
        </div>
      </header>

      <section
        className="filter-bar"
        aria-label="Verdict trend controls"
        style={{ display: 'flex', alignItems: 'flex-end', gap: '1rem', flexWrap: 'wrap' }}
      >
        <div className="field-group">
          <label htmlFor="verdict-trend-period">Reporting period</label>
          <select
            id="verdict-trend-period"
            value={periodDays}
            disabled={loading}
            onChange={(event) => setPeriodDays(Number(event.target.value) as PeriodDays)}
          >
            <option value="7">Last 7 days</option>
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
          </select>
        </div>
        <button
          type="button"
          onClick={handleExport}
          disabled={!aggregated || loading}
          style={{
            marginLeft: 'auto',
            background: '#16202e',
            color: '#fff',
            border: 'none',
            borderRadius: 9,
            padding: '10px 18px',
            fontWeight: 600,
            fontSize: '0.85rem',
            cursor: !aggregated || loading ? 'not-allowed' : 'pointer',
            opacity: !aggregated || loading ? 0.5 : 1,
          }}
        >
          Export CSV
        </button>
      </section>

      {loading ? (
        <LoadingState label="Loading verdict trend..." />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={load} />
      ) : aggregated ? (
        <VerdictTrendsResult data={aggregated} />
      ) : null}
    </>
  )
}

function VerdictTrendsResult({ data }: { data: AggregatedTrend }) {
  const summary = data.summary
  return (
    <>
      <section className="summary-grid" aria-label="Verdict trend summary">
        <StatCard label="Total Scans" value={summary.totalScans.toLocaleString()} color="#153a2a" />
        <StatCard
          label="Safe"
          value={`${percentage(summary.safeCount, summary.totalScans)}%`}
          hint={`${summary.safeCount.toLocaleString()} scans`}
          color={SAFE_COLOR}
        />
        <StatCard
          label="Warning"
          value={`${percentage(summary.warningCount, summary.totalScans)}%`}
          hint={`${summary.warningCount.toLocaleString()} scans`}
          color={WARNING_COLOR}
        />
        <StatCard
          label="Unsafe"
          value={`${percentage(summary.unsafeCount, summary.totalScans)}%`}
          hint={`${summary.unsafeCount.toLocaleString()} scans`}
          color={UNSAFE_COLOR}
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
          </div>
          <div
            style={{ display: 'flex', gap: '0.9rem', fontSize: '0.8rem', color: '#6b7772' }}
            aria-hidden="true"
          >
            <LegendChip color={SAFE_COLOR} label="Safe" />
            <LegendChip color={WARNING_COLOR} label="Warning" />
            <LegendChip color={UNSAFE_COLOR} label="Unsafe" />
          </div>
        </div>
        <VerdictTrendChart points={data.dailyTrend} />
      </section>

      <section className="panel" aria-labelledby="verdict-mix-title">
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
          <table className="compact-table">
            <caption>Accessible verdict mix values</caption>
            <thead>
              <tr>
                <th>Verdict</th>
                <th>Count</th>
                <th>Percent</th>
              </tr>
            </thead>
            <tbody>
              <VerdictRow label="Safe" count={summary.safeCount} total={summary.totalScans} />
              <VerdictRow label="Warning" count={summary.warningCount} total={summary.totalScans} />
              <VerdictRow label="Unsafe" count={summary.unsafeCount} total={summary.totalScans} />
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}

function StatCard({
  label,
  value,
  hint,
  color,
}: {
  label: string
  value: string
  hint?: string
  color: string
}) {
  return (
    <article
      style={{
        background: '#ffffff',
        border: '1px solid #e7e6df',
        borderRadius: 14,
        padding: '16px 18px',
      }}
    >
      <div style={{ fontSize: '0.8rem', color: '#6b7772' }}>{label}</div>
      <div style={{ fontSize: '1.9rem', fontWeight: 800, color, marginTop: 4 }}>{value}</div>
      {hint && <div style={{ fontSize: '0.8rem', color: '#6b7772', marginTop: 2 }}>{hint}</div>}
    </article>
  )
}

function LegendChip({ color, label }: { color: string; label: string }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
      <i style={{ width: 10, height: 10, borderRadius: 2, background: color, display: 'inline-block' }} />
      {label}
    </span>
  )
}

function VerdictRow({ label, count, total }: { label: string; count: number; total: number }) {
  return (
    <tr>
      <td>
        <span className={`status-badge status-badge--${label.toLowerCase()}`}>{label}</span>
      </td>
      <td>{count.toLocaleString()}</td>
      <td>{percentage(count, total)}%</td>
    </tr>
  )
}
