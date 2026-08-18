import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { adminService } from './adminService'
import { consumerTrendsApiService } from '../analytics/consumerTrendsApiService'
import type {
  ConsumerTrendsResponse,
  DailyTrendPoint,
} from '../analytics/consumerTrendsTypes'
import { getErrorMessage } from '../../shared/api/apiErrors'
import type { AdminUser } from './models'
import {
  systemHealthApiService,
  type SystemHealth,
} from './systemHealthApiService'
import { usageStatisticsApiService } from '../analytics/usageStatisticsApiService'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'
import { StatusBadge } from '../../shared/ui/StatusBadge'

/** Compact SVG sparkline from daily scan totals. */
function DailyTrendSparkline({ points }: { points: DailyTrendPoint[] }) {
  if (points.length < 2) return null

  const values = points.map((point) => point.totalCount)
  const max = Math.max(...values, 1)
  const width = 640
  const height = 96
  const pad = 8
  const coords = values.map((value, index) => ({
    x: (index / (values.length - 1)) * width,
    y: height - (value / max) * (height - pad * 2) - pad,
  }))

  const path = coords.reduce((d, point, index) => {
    if (index === 0) return `M ${point.x} ${point.y}`
    const previous = coords[index - 1]
    const before = coords[index - 2] ?? previous
    const next = coords[index + 1] ?? point
    const control1X = previous.x + (point.x - before.x) / 6
    const control1Y = previous.y + (point.y - before.y) / 6
    const control2X = point.x - (next.x - previous.x) / 6
    const control2Y = point.y - (next.y - previous.y) / 6
    return `${d} C ${control1X} ${control1Y}, ${control2X} ${control2Y}, ${point.x} ${point.y}`
  }, '')

  return (
    <svg
      className="dashboard-sparkline"
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      role="img"
      aria-label="Daily scan volume over the current trends period"
    >
      <path
        d={path}
        fill="none"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinejoin="round"
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  )
}

export function SystemDashboardPage() {
  const [trends, setTrends] = useState<ConsumerTrendsResponse | null>(null)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [health, setHealth] = useState<SystemHealth | null>(null)
  const [dailyActiveUsers, setDailyActiveUsers] = useState(0)
  const [openFeedbackCount, setOpenFeedbackCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [trendData, userData, openFeedback, healthData, usageData] =
        await Promise.all([
          consumerTrendsApiService.getConsumerTrends(),
          adminService.getUsers(),
          adminService.getScanFeedback({
            resolved: false,
            periodDays: 30,
            page: 0,
            pageSize: 1,
          }),
          systemHealthApiService.getSystemHealth(24),
          usageStatisticsApiService.getUsageStatistics(7),
        ])
      setTrends(trendData)
      setUsers(userData)
      setOpenFeedbackCount(openFeedback.pageInfo.totalItems)
      setHealth(healthData)
      setDailyActiveUsers(usageData.kpis.dailyActiveUsers)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  if (loading) return <LoadingState label="Loading system dashboard…" />
  if (error) return <ErrorState message={error} onRetry={load} />

  const summary = trends?.summary
  const suspendedCount = users.filter((user) => !user.active).length
  const activeCount = users.length - suspendedCount
  const adminCount = users.filter((user) => user.role === 'ADMIN').length
  const appUserCount = users.length - adminCount
  const topIngredient = trends?.topFlaggedIngredients[0]
  const dailyTrend = trends?.dailyTrend ?? []
  const overallStatus = health?.overallStatus ?? 'Unknown'
  const statusIsUp = overallStatus.toUpperCase() === 'UP'

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">System overview</p>
          <h1>Administration Dashboard</h1>
          <p>
            Exception queues, platform health, usage, and anonymised consumer trends.
          </p>
        </div>
      </header>

      <section className="summary-grid" aria-label="System summary">
        <Link
          className={`summary-card summary-card--link${statusIsUp ? '' : ' summary-card--alert'}`}
          to="/system/health"
        >
          <span className="summary-card__icon" aria-hidden="true">●</span>
          <div>
            <span>System status</span>
            <strong>{overallStatus}</strong>
          </div>
        </Link>
        <Link className="summary-card summary-card--link" to="/system/usage">
          <span className="summary-card__icon" aria-hidden="true">↗</span>
          <div>
            <span>Daily active users</span>
            <strong>{dailyActiveUsers.toLocaleString()}</strong>
          </div>
        </Link>
        <Link
          className="summary-card summary-card--link"
          to="/system/users?status=SUSPENDED"
        >
          <span className="summary-card__icon" aria-hidden="true">!</span>
          <div>
            <span>Suspended accounts</span>
            <strong>{suspendedCount}</strong>
          </div>
        </Link>
        <Link
          className="summary-card summary-card--link"
          to="/system/feedback?resolved=UNRESOLVED"
        >
          <span className="summary-card__icon" aria-hidden="true">◫</span>
          <div>
            <span>Open scan feedback</span>
            <strong>{openFeedbackCount.toLocaleString()}</strong>
          </div>
        </Link>
      </section>

      <div className="dashboard-grid">
        <section className="panel" aria-labelledby="consumer-trends-title">
          <div className="panel__header">
            <div>
              <p className="eyebrow">Insights</p>
              <h2 id="consumer-trends-title">Consumer Trends</h2>
            </div>
            <Link to="/system/trends">View trends</Link>
          </div>
          <p>
            Explore anonymised, aggregate verdict and ingredient information.
            No named family or individual dietary data is included.
          </p>
          <div className="dashboard-trends-visual">
            <DailyTrendSparkline points={dailyTrend} />
            <div className="verdict-summary verdict-summary--compact">
              <div>
                <strong>{summary?.safeCount ?? 0}</strong>
                <StatusBadge status="SAFE" />
              </div>
              <div>
                <strong>{summary?.warningCount ?? 0}</strong>
                <StatusBadge status="WARNING" />
              </div>
              <div>
                <strong>{summary?.unsafeCount ?? 0}</strong>
                <StatusBadge status="UNSAFE" />
              </div>
            </div>
          </div>
          {topIngredient ? (
            <p className="dashboard-preview-note">
              Top flagged ingredient:{' '}
              <strong>{topIngredient.ingredientName}</strong>
              {' '}({topIngredient.flaggedCount.toLocaleString()})
            </p>
          ) : (
            <p className="empty-inline" role="status">
              No flagged ingredient trends in the current period.
            </p>
          )}
        </section>

        <section className="panel" aria-labelledby="user-access-title">
          <div className="panel__header">
            <div>
              <p className="eyebrow">Account management</p>
              <h2 id="user-access-title">User Accounts & Access</h2>
            </div>
            <Link to="/system/users">Manage access</Link>
          </div>
          <p>
            Search existing accounts and manage Active or Suspended status.
          </p>
          <div className="dashboard-preview-stats" aria-label="Account status preview">
            <div>
              <strong>{activeCount}</strong>
              <span>Active</span>
            </div>
            <div>
              <strong>{users.length}</strong>
              <span>Total accounts</span>
            </div>
            <div>
              <strong>{appUserCount}</strong>
              <span>USER</span>
            </div>
            <div>
              <strong>{adminCount}</strong>
              <span>ADMIN</span>
            </div>
          </div>
        </section>
      </div>
    </>
  )
}
