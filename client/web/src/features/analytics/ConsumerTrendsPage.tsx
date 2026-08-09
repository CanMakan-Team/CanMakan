import { useCallback, useEffect, useState } from 'react'
import { ApiError, getErrorMessage } from '../../shared/api/apiErrors'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import { consumerTrendsApiService } from './consumerTrendsApiService'
import type {
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
  TrendSummary,
} from './consumerTrendsTypes'

const SINGAPORE_TIMEZONE = 'Asia/Singapore'
const SINGAPORE_DATE_FORMATTER = new Intl.DateTimeFormat('en-CA', {
  timeZone: SINGAPORE_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})
const SINGAPORE_DATETIME_FORMATTER = new Intl.DateTimeFormat('en-SG', {
  timeZone: SINGAPORE_TIMEZONE,
  dateStyle: 'medium',
  timeStyle: 'short',
})

type PeriodDays = 7 | 30 | 90

interface PageError {
  kind: 'validation' | 'service' | 'unexpected'
  message: string
}

function currentSingaporeDate(now = new Date()): string {
  let year = ''
  let month = ''
  let day = ''
  for (const part of SINGAPORE_DATE_FORMATTER.formatToParts(now)) {
    if (part.type === 'year') year = part.value
    if (part.type === 'month') month = part.value
    if (part.type === 'day') day = part.value
  }
  return `${year}-${month}-${day}`
}

function subtractCalendarDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day - days))
  return date.toISOString().slice(0, 10)
}

function buildPeriodQuery(days: PeriodDays): ConsumerTrendsQuery {
  const to = currentSingaporeDate()
  return {
    from: subtractCalendarDays(to, days - 1),
    to,
  }
}

function percentage(count: number, total: number): number {
  return total === 0 ? 0 : Math.round((count / total) * 100)
}

function buildVerdictGradient(summary: TrendSummary): string {
  if (summary.totalScans === 0) return '#e7eeea'
  const safeEnd = (summary.safeCount / summary.totalScans) * 100
  const warningEnd =
    safeEnd + (summary.warningCount / summary.totalScans) * 100
  return `conic-gradient(
    #27875b 0 ${safeEnd}%,
    #d6a12b ${safeEnd}% ${warningEnd}%,
    #b24b44 ${warningEnd}% 100%
  )`
}

function formatGeneratedAt(value: string): string {
  try {
    return SINGAPORE_DATETIME_FORMATTER.format(new Date(value))
  } catch {
    return value
  }
}

function classifyPageError(error: unknown): PageError {
  if (error instanceof ApiError) {
    return {
      kind: error.status === 400 ? 'validation' : 'service',
      message: getErrorMessage(error),
    }
  }
  return { kind: 'unexpected', message: getErrorMessage(error) }
}

export function ConsumerTrendsPage() {
  const [data, setData] = useState<ConsumerTrendsResponse | null>(null)
  const [periodDays, setPeriodDays] = useState<PeriodDays>(30)
  const [query, setQuery] = useState<ConsumerTrendsQuery>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<PageError | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(
        await (query
          ? consumerTrendsApiService.getConsumerTrends(query)
          : consumerTrendsApiService.getConsumerTrends()),
      )
    } catch (caughtError) {
      setError(classifyPageError(caughtError))
    } finally {
      setLoading(false)
    }
  }, [query])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  const changePeriod = (value: string) => {
    const days = Number(value)
    if (days !== 7 && days !== 30 && days !== 90) return
    setPeriodDays(days)
    setQuery(buildPeriodQuery(days))
  }

  const errorMessage =
    error?.kind === 'validation'
      ? `The selected reporting period could not be used. ${error.message}`
      : error?.kind === 'service'
        ? `The analytics service could not complete the request. ${error.message}`
        : error?.kind === 'unexpected'
          ? `An unexpected error occurred. ${error.message}`
          : undefined

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Feature 7 - anonymised aggregate data</p>
          <h1>Consumer Trends</h1>
          <p>
            Aggregated scan trends and flagged ingredients only. Named users,
            families and individual dietary profiles are excluded.
          </p>
        </div>
      </header>

      <section
        className="filter-bar filter-bar--system filter-bar--analytics"
        aria-label="Trend controls"
      >
        <div className="field-group">
          <label htmlFor="trend-period">Reporting period</label>
          <select
            id="trend-period"
            value={periodDays}
            disabled={loading}
            onChange={(event) => changePeriod(event.target.value)}
          >
            <option value="7">Last 7 days</option>
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
          </select>
        </div>
      </section>

      {loading ? (
        <LoadingState label="Generating anonymised consumer trends..." />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={load} />
      ) : data ? (
        <ConsumerTrendsResult data={data} />
      ) : null}
    </>
  )
}

function ConsumerTrendsResult({ data }: { data: ConsumerTrendsResponse }) {
  const verdicts = [
    { label: 'SAFE', count: data.summary.safeCount },
    { label: 'WARNING', count: data.summary.warningCount },
    { label: 'UNSAFE', count: data.summary.unsafeCount },
  ] as const
  const maxIngredientCount = Math.max(
    ...data.topFlaggedIngredients.map((item) => item.flaggedCount),
    1,
  )

  return (
    <>
      <p className="trend-result-meta">
        Reporting period: <time dateTime={data.period.from}>{data.period.from}</time>
        {' to '}
        <time dateTime={data.period.to}>{data.period.to}</time>
        {` - ${data.period.timezone} - Generated `}
        <time dateTime={data.generatedAt}>{formatGeneratedAt(data.generatedAt)}</time>
      </p>

      {data.dataQuality.partial && (
        <div className="notice notice--warning">
          <strong>Partial ingredient-ranking data</strong>
          <p>
            Some ingredient-ranking data could not be processed (
            {data.dataQuality.skippedMalformedFindings.toLocaleString()} skipped
            {data.dataQuality.skippedMalformedFindings === 1 ? ' record' : ' records'}).
            {' '}Aggregate scan trends remain available.
          </p>
        </div>
      )}

      <section className="summary-grid" aria-label="Consumer trend summary">
        <SummaryCard label="Total Scans" value={data.summary.totalScans} icon="#" />
        <SummaryCard label="Safe" value={data.summary.safeCount} icon="S" />
        <SummaryCard label="Warning" value={data.summary.warningCount} icon="!" />
        <SummaryCard label="Unsafe" value={data.summary.unsafeCount} icon="U" />
      </section>

      {data.summary.totalScans === 0 && (
        <EmptyState
          title="No eligible scans for this period"
          description="The reporting period is valid, but no scans matched the analytics criteria."
        />
      )}

      <div className="trend-grid">
        <section className="panel" aria-labelledby="verdict-chart-title">
          <div className="panel__header">
            <div>
              <p className="eyebrow">Assessment outcomes</p>
              <h2 id="verdict-chart-title">Verdict distribution</h2>
            </div>
            <strong>{data.summary.totalScans.toLocaleString()} scans</strong>
          </div>
          <div className="donut-layout">
            <div
              className="donut-chart"
              style={{ background: buildVerdictGradient(data.summary) }}
              role="img"
              aria-label={`Verdict distribution: ${data.summary.safeCount} safe, ${data.summary.warningCount} warning, ${data.summary.unsafeCount} unsafe.`}
            >
              <span>
                {data.summary.totalScans.toLocaleString()}
                <small>Total</small>
              </span>
            </div>
            <table className="compact-table">
              <caption>Accessible verdict distribution values</caption>
              <thead>
                <tr><th>Verdict</th><th>Count</th><th>Percent</th></tr>
              </thead>
              <tbody>
                {verdicts.map((item) => (
                  <tr key={item.label}>
                    <td>
                      <span className={`status-badge status-badge--${item.label.toLowerCase()}`}>
                        {item.label}
                      </span>
                    </td>
                    <td>{item.count.toLocaleString()}</td>
                    <td>{percentage(item.count, data.summary.totalScans)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel" aria-labelledby="ingredient-chart-title">
          <p className="eyebrow">Distinct scans</p>
          <h2 id="ingredient-chart-title">Most commonly flagged ingredients</h2>
          {data.topFlaggedIngredients.length === 0 ? (
            <p>No flagged ingredients were available for this period.</p>
          ) : (
            <>
              <div className="bar-chart" aria-hidden="true">
                {data.topFlaggedIngredients.map((item) => (
                  <div key={item.ingredientName}>
                    <span>{item.ingredientName}</span>
                    <i style={{ width: `${(item.flaggedCount / maxIngredientCount) * 100}%` }} />
                    <strong>{item.flaggedCount}</strong>
                  </div>
                ))}
              </div>
              <table className="compact-table accessible-equivalent">
                <caption>Accessible flagged ingredient values</caption>
                <thead>
                  <tr><th>Ingredient</th><th>Flagged scans</th></tr>
                </thead>
                <tbody>
                  {data.topFlaggedIngredients.map((item) => (
                    <tr key={item.ingredientName}>
                      <th scope="row">{item.ingredientName}</th>
                      <td>{item.flaggedCount.toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </section>
      </div>

      <section className="panel panel--table" aria-labelledby="daily-trend-title">
        <div className="panel__header">
          <div>
            <p className="eyebrow">Singapore calendar dates</p>
            <h2 id="daily-trend-title">Daily scan trend</h2>
          </div>
        </div>
        <div className="responsive-table">
          <table className="data-table data-table--analytics">
            <caption>Daily scan verdict counts</caption>
            <thead>
              <tr>
                <th>Date</th><th>Total</th><th>Safe</th><th>Warning</th><th>Unsafe</th>
              </tr>
            </thead>
            <tbody>
              {data.dailyTrend.map((point) => (
                <tr key={point.date}>
                  <th scope="row"><time dateTime={point.date}>{point.date}</time></th>
                  <td>{point.totalCount.toLocaleString()}</td>
                  <td>{point.safeCount.toLocaleString()}</td>
                  <td>{point.warningCount.toLocaleString()}</td>
                  <td>{point.unsafeCount.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}

function SummaryCard({
  label,
  value,
  icon,
}: {
  label: string
  value: number
  icon: string
}) {
  return (
    <article className="summary-card">
      <span className="summary-card__icon" aria-hidden="true">{icon}</span>
      <div>
        <span>{label}</span>
        <strong>{value.toLocaleString()}</strong>
      </div>
    </article>
  )
}
