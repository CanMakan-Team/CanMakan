import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { adminService } from './adminService'
import { getErrorMessage } from '../../shared/api/apiErrors'
import type { ConsumerTrendResponse } from '../../shared/api/types'
import type { AdminUser } from './models'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'

export function SystemDashboardPage() {
  const [trends, setTrends] = useState<ConsumerTrendResponse | null>(null)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [trendData, userData] = await Promise.all([
        adminService.getConsumerTrends(),
        adminService.getUsers(),
      ])
      setTrends(trendData)
      setUsers(userData)
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

  const totalAssessments =
    trends?.verdictDistribution.reduce((sum, item) => sum + item.count, 0) ?? 0

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">System overview</p>
          <h1>Administration Dashboard</h1>
          <p>
            Sprint 1 oversight for anonymised consumer trends and account access.
          </p>
        </div>
      </header>

      <section className="summary-grid" aria-label="System summary">
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">↗</span>
          <div><span>Aggregate assessments</span><strong>{totalAssessments.toLocaleString()}</strong></div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">♙</span>
          <div><span>User accounts</span><strong>{users.length}</strong></div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">!</span>
          <div>
            <span>Suspended accounts</span>
            <strong>{users.filter((user) => !user.active).length}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">◫</span>
          <div><span>Sprint features</span><strong>2</strong></div>
        </article>
      </section>

      <div className="dashboard-grid">
        <section className="panel">
          <p className="eyebrow">Selected Feature 7</p>
          <h2>Consumer Trends</h2>
          <p>
            Explore anonymised, aggregate verdict and ingredient information.
            No named family or individual dietary data is included.
          </p>
          <Link className="button button--dark" to="/system/trends">
            Open consumer trends
          </Link>
        </section>
        <section className="panel">
          <p className="eyebrow">Selected UC13</p>
          <h2>User Accounts & Access</h2>
          <p>
            Search existing accounts and manage Active or Suspended status.
          </p>
          <Link className="button button--dark" to="/system/users">
            Manage user access
          </Link>
        </section>
      </div>

      <div className="notice notice--neutral">
        <strong>Scope is intentionally focused</strong>
        <p>
          Review queues, product data issues, aliases, system logs, health and AI
          reasoning review remain labelled as future prototype features.
        </p>
      </div>
    </>
  )
}
