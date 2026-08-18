import { useCallback, useEffect, useState } from 'react'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { useLatestRequest } from '../../../shared/lib/useLatestRequest'
import {
  systemHealthApiService,
  type HealthWindowHours,
  type SystemHealth,
} from '../api/systemHealthApiService'
import { SystemHealthResult } from '../components/SystemHealthResult'

/**
 * UC16 - View System Health Logs (system admin).
 *
 * Four colour-coded sections over data that already exists: component health (green, from Actuator),
 * AI execution monitoring (blue, from ai_execution_logs), admin activity audit (amber, from
 * admin_audit_logs), and scan data quality (red, from scans). Mirrors the other admin analytics
 * pages (page header, period filter, panels).
 */

const WINDOW_OPTIONS: { hours: HealthWindowHours; label: string }[] = [
  { hours: 24, label: 'Last 24 hours' },
  { hours: 168, label: 'Last 7 days' },
  { hours: 720, label: 'Last 30 days' },
]

export function SystemHealthPage() {
  const [hours, setHours] = useState<HealthWindowHours>(24)
  const [data, setData] = useState<SystemHealth | null>(null)
  const [loading, setLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | undefined>()
  const { nextRequestId, isLatestRequest } = useLatestRequest()

  const load = useCallback(async () => {
    const requestId = nextRequestId()
    setLoading(true)
    setErrorMessage(undefined)
    try {
      const result = await systemHealthApiService.getSystemHealth(hours)
      if (!isLatestRequest(requestId)) return
      setData(result)
    } catch {
      if (!isLatestRequest(requestId)) return
      setErrorMessage('The system health snapshot could not be loaded. Please try again.')
    } finally {
      if (isLatestRequest(requestId)) setLoading(false)
    }
  }, [hours, isLatestRequest, nextRequestId])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Feature 16 - system admin</p>
          <h1>System Health</h1>
          <p>
            Component status, AI execution monitoring, the admin activity trail, and scan data
            quality - assembled from Actuator and existing logs.
          </p>
        </div>
      </header>

      <section
        className="filter-bar filter-bar--system"
        aria-label="System health controls"
      >
        <div className="field-group">
          <label htmlFor="health-window">Reporting window</label>
          <select
            id="health-window"
            value={hours}
            disabled={loading}
            onChange={(event) => setHours(Number(event.target.value) as HealthWindowHours)}
          >
            {WINDOW_OPTIONS.map((option) => (
              <option key={option.hours} value={option.hours}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="filter-bar__actions">
          <button
            type="button"
            className="button button--dark"
            onClick={() => void load()}
            disabled={loading}
          >
            Refresh
          </button>
        </div>
      </section>

      {loading ? (
        <LoadingState label="Loading system health…" />
      ) : errorMessage ? (
        <ErrorState message={errorMessage} onRetry={() => void load()} />
      ) : data ? (
        <SystemHealthResult data={data} hours={hours} />
      ) : (
        <EmptyState title="No health data" description="No system health data is available yet." />
      )}
    </>
  )
}
