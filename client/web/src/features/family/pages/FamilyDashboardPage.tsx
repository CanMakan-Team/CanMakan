import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { ActiveProfile, FamilyMember, ScanRecord } from '../../../shared/api/types'
import { useFamilyMe } from '../useFamilyMe'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { PortalIcon } from '../../../shared/ui/PortalIcon'
import { StatusBadge } from '../../../shared/ui/StatusBadge'

export function FamilyDashboardPage() {
  const { family } = useFamilyMe()
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [scans, setScans] = useState<ScanRecord[]>([])
  const [active, setActive] = useState<ActiveProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [loadedMembers, loadedActive] = await Promise.all([
        familyApiService.getMembers(),
        familyApiService.getActiveProfile(),
      ])
      setMembers(loadedMembers)
      setActive(loadedActive)

      // Family scan history is PRIMARY_ADMIN-only (UC4); members still see the rest of the dashboard.
      try {
        setScans(await familyApiService.getScanHistory())
      } catch {
        setScans([])
      }
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadDashboard(), 0)
    window.addEventListener('canmakan:family-data-changed', loadDashboard)
    return () => {
      window.clearTimeout(timeoutId)
      window.removeEventListener('canmakan:family-data-changed', loadDashboard)
    }
  }, [loadDashboard])

  if (loading) return <LoadingState label="Loading your family dashboard…" />
  if (error) return <ErrorState message={error} onRetry={loadDashboard} />

  const commonCodes = members.reduce<string[]>((codes, member) => {
    member.commonRequirements.forEach((code) => {
      if (!codes.includes(code)) codes.push(code)
    })
    return codes
  }, [])
  const verdictCounts = scans.reduce<Record<string, number>>((counts, scan) => {
    counts[scan.verdict] = (counts[scan.verdict] ?? 0) + 1
    return counts
  }, {})

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Family overview</p>
          <h1>Manage {family?.familyName ?? 'your family circle'}</h1>
          <p>
            A practical snapshot of profiles and supplied assessment history.
            Counts are not medical-risk trends.
          </p>
        </div>
        <Link className="button button--primary" to="/family/members">
          Manage family
        </Link>
      </header>

      <section className="summary-grid" aria-label="Family summary">
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">
            <PortalIcon name="people" />
          </span>
          <div>
            <span>Family members</span>
            <strong>{members.length}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">
            <PortalIcon name="person" />
          </span>
          <div>
            <span>Active profile</span>
            <strong>{active?.profileName ?? 'Not selected'}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">
            <PortalIcon name="restrictions" />
          </span>
          <div>
            <span>Common requirements</span>
            <strong>{commonCodes.length}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">
            <PortalIcon name="history" />
          </span>
          <div>
            <span>Recent scans</span>
            <strong>{scans.length}</strong>
          </div>
        </article>
      </section>

      <div className="dashboard-grid">
        <section className="panel" aria-labelledby="recent-assessments-title">
          <div className="panel__header">
            <div>
              <p className="eyebrow">Supplied verdicts</p>
              <h2 id="recent-assessments-title">Recent assessments</h2>
            </div>
            <Link to="/family/history">View history</Link>
          </div>
          <div className="verdict-summary">
            {(['SAFE', 'WARNING', 'UNSAFE'] as const).map((verdict) => (
              <div key={verdict}>
                <StatusBadge status={verdict} />
                <strong>{verdictCounts[verdict] ?? 0}</strong>
              </div>
            ))}
          </div>
          <div className="recent-list">
            {scans.slice(0, 3).map((scan) => (
              <article key={scan.scanId}>
                <div>
                  <strong>{scan.product}</strong>
                  <span>{scan.evaluatedProfile} · {scan.brand}</span>
                </div>
                <StatusBadge status={scan.verdict} />
              </article>
            ))}
          </div>
        </section>

        <aside className="panel quick-actions" aria-labelledby="quick-actions-title">
          <p className="eyebrow">Shortcuts</p>
          <h2 id="quick-actions-title">Quick actions</h2>
          <Link to="/family/members">
            <span aria-hidden="true">＋</span>
            <div>
              <strong>Add or create a member</strong>
              <span>Keep registered-user linking separate from profiles.</span>
            </div>
          </Link>
          <Link to="/family/restrictions">
            <span aria-hidden="true">
              <PortalIcon name="restrictions" />
            </span>
            <div>
              <strong>Review restriction summary</strong>
              <span>Compare dynamically returned restriction codes.</span>
            </div>
          </Link>
        </aside>
      </div>

      <div className="safety-disclaimer">
        <strong>Safety reminder</strong>
        <p>
          CanMakan displays assessment results supplied by backend services. It
          does not provide medical advice or guarantee that a product is safe.
          Always check the package label.
        </p>
      </div>
    </>
  )
}
