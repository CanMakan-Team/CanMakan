import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import { consumerTrendsApiService } from './consumerTrendsApiService'
import type {
  ConsumerTrendsResponse,
  TrendSummary,
} from './consumerTrendsTypes'
import { VerdictTrendChart } from './VerdictTrendChart'

/**
 * UC14 - View Scan Verdict Trend.
 *
 * A dedicated system-admin page for the anonymised, aggregate scan-verdict view: headline verdict
 * mix, the daily Safe / Warning / Unsafe trend, and the restrictions most often behind a flag.
 * Reads the shared analytics endpoint but presents the verdict-focused surface.
 */

const SAFE_COLOR = '#27875b'
const WARNING_COLOR = '#d6a12b'
const UNSAFE_COLOR = '#b24b44'

type PeriodDays = 7 | 30 | 90

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function percentage(count: number, total: number): number {
  return total === 0 ? 0 : Math.round((count / total) * 100)
}

function buildVerdictGradient(summary: TrendSummary): string {
  if (summary.totalScans === 0) return '#e7eeea'
  const safeEnd = (summary.safeCount / summary.totalScans) * 100
  const warningEnd = safeEnd + (summary.warningCount / summary.totalScans) * 100
  return `conic-gradient(${SAFE_COLOR} 0 ${safeEnd}%, ${WARNING_COLOR} ${safeEnd}% ${warningEnd}%, ${UNSAFE_COLOR} ${warningEnd}% 100%)`
}

export function VerdictTrendsPage() {
  const [data, setData] = useState<ConsumerTrendsResponse | null>(null)
  const [periodDays, setPeriodDays] = useState<PeriodDays>(7)
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | undefined>()

  const load = useCallback(async () => {
    setLoading(true)
    setErrorMessage(undefined)
    try {
      const to = new Date()
      const from = new Date()
      from.setDate(to.getDate() - (periodDays - 1))
      setData(
        await consumerTrendsApiService.getConsumerTrends({
          from: isoDate(from),
          to: isoDate(to),
        }),
      )
    } catch (error) {
      setErrorMessage(getErrorMessage(error))
    } finally {
      setLoading(false)
    }
  }, [periodDays])

  useEffect(() => {
    void load()
  }, [load])

  const handleExport = useCallback(() => {
    if (!data) return
    const header = 'Date,Safe,Warning,Unsafe,Total'
    const rows = data.dailyTrend.map(
      (point) =>
        `${point.date},${point.safeCount},${point.warningCount},${point.unsafeCount},${point.totalCount}`,
    )
    const csv = [header, ...rows].join('\n')
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `verdict-trend-${data.period.from}-to-${data.period.to}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }, [data])

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Feature 14 - scan verdict trend</p>
          <h1>Verdict Trends</h1>
          <p>
            Anonymised, aggregate Safe / Warning / Unsafe scan outcomes over time. Named users,
            families and individual dietary profiles are excluded.
          </p>
        </div>
      </header>

      <section
        className="filter-bar filter-bar--system filter-bar--analytics"
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
          disabled={!data || loading}
          style={{
            marginLeft: 'auto',
            background: '#16202e',
            color: '#fff',
            border: 'none',
            borderRadius: 9,
            padding: '10px 18px',
            fontWeight: 600,
            fontSize: '0.85rem',
            cursor: !data || loading ? 'not-allowed' : 'pointer',
            opacity: !data || loading ? 0.5 : 1,
          }}
        >
          Export CSV
        </button>
      </section>

      {loading ? (
        <LoadingState label="Loading verdict trend..." />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={load} />
      ) : data ? (
        <VerdictTrendsResult data={data} />
      ) : null}
    </>
  )
}

function VerdictTrendsResult({ data }: { data: ConsumerTrendsResponse }) {
  const summary = data.summary
  const maxFlagged = Math.max(
    1,
    ...data.topFlaggedIngredients.map((item) => item.flaggedCount),
  )

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
          title="No eligible scans for this period"
          description="The reporting period is valid, but no scans matched the analytics criteria."
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

      <div className="trend-grid">
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

        <section className="panel" aria-labelledby="top-restrictions-title">
          <p className="eyebrow">Behind a flag</p>
          <h2 id="top-restrictions-title">Top triggered restrictions</h2>
          {data.topFlaggedIngredients.length === 0 ? (
            <p>No flagged ingredients were available for this period.</p>
          ) : (
            <div className="bar-chart" aria-hidden="true">
              {data.topFlaggedIngredients.map((item) => (
                <div key={item.ingredientName}>
                  <span>{item.ingredientName}</span>
                  <i style={{ width: `${(item.flaggedCount / maxFlagged) * 100}%` }} />
                  <strong>{item.flaggedCount}</strong>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
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
