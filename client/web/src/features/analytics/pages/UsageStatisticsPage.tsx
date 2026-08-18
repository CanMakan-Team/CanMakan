import { useCallback, useEffect, useState } from 'react'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { downloadTextFile } from '../../../shared/lib/downloadTextFile'
import { useLatestRequest } from '../../../shared/lib/useLatestRequest'
import {
  usageStatisticsApiService,
  type UsagePeriodDays,
  type UsageStatistics,
} from '../api/usageStatisticsApiService'
import { UsageStatisticsResult } from '../components/UsageStatisticsResult'

/**
 * UC15 - View Application Usage Statistics (system admin).
 *
 * One page, four colour-coded sections: acquisition and conversion (blue), activity and stickiness
 * (green), retention and churn (amber/red), and engagement and sessions (blue with a warm heatmap).
 * Mirrors the Consumer Trends page structure (page header, period filter, panels). Data currently
 * comes from a mock service; see usageStatisticsApiService for the intended backend contract.
 */

const PERIOD_OPTIONS = [7, 30, 90] as const

export function UsageStatisticsPage() {
  const [periodDays, setPeriodDays] = useState<UsagePeriodDays>(7)
  const [data, setData] = useState<UsageStatistics | null>(null)
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | undefined>()
  const { nextRequestId, isLatestRequest } = useLatestRequest()

  const load = useCallback(async () => {
    const requestId = nextRequestId()
    setLoading(true)
    setErrorMessage(undefined)
    try {
      const result = await usageStatisticsApiService.getUsageStatistics(periodDays)
      if (!isLatestRequest(requestId)) return
      setData(result)
    } catch {
      if (!isLatestRequest(requestId)) return
      setErrorMessage('The usage statistics could not be loaded. Please try again.')
    } finally {
      if (isLatestRequest(requestId)) setLoading(false)
    }
  }, [periodDays, isLatestRequest, nextRequestId])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  const handleExport = useCallback(() => {
    if (!data) return
    const rows = [
      'Metric,Value',
      `New sign-ups,${data.kpis.newSignups}`,
      `Daily active users,${data.kpis.dailyActiveUsers}`,
      `Stickiness (%),${data.kpis.stickinessPct}`,
      `Average session (seconds),${data.kpis.averageSessionSeconds}`,
      `D1 retention (%),${data.retention.day1Pct}`,
      `D7 retention (%),${data.retention.day7Pct}`,
      `D30 retention (%),${data.retention.day30Pct}`,
      `Churn (%),${data.retention.churnPct}`,
      `Inactive 30 days,${data.retention.inactive30d}`,
    ]
    downloadTextFile(
      `usage-statistics-${periodDays}d.csv`,
      'text/csv;charset=utf-8',
      rows.join('\n'),
    )
  }, [data, periodDays])

  return (
    <div className="analytics-page usage-statistics-page">
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">System Administration</p>
          <h1>Usage Statistics</h1>
          <p>
            How app users engage with CanMakan: acquisition, activity, retention and sessions across
            the selected reporting period.
          </p>
        </div>
      </header>

      <section
        className="filter-bar filter-bar--system filter-bar--analytics usage-toolbar"
        aria-label="Usage statistics controls"
      >
        <div className="field-group">
          <label htmlFor="usage-period">Reporting period</label>
          <select
            id="usage-period"
            value={periodDays}
            disabled={loading}
            onChange={(event) => setPeriodDays(Number(event.target.value) as UsagePeriodDays)}
          >
            {PERIOD_OPTIONS.map((option) => (
              <option key={option} value={option}>
                Last {option} days
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          className="button button--primary"
          onClick={handleExport}
          disabled={!data || loading}
        >
          Export CSV
        </button>
      </section>

      {loading ? (
        <LoadingState label="Loading usage statistics…" />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={load} />
      ) : data ? (
        <UsageStatisticsResult data={data} />
      ) : (
        <EmptyState
          title="No usage data"
          description="No usage statistics are available yet."
          showMascot={false}
        />
      )}
    </div>
  )
}
