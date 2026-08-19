import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  FAMILY_HISTORY_PATH,
  FAMILY_MEMBERS_PATH,
  FAMILY_RESTRICTIONS_PATH,
  FAMILY_VERDICT_TRENDS_PATH,
} from '../../../app/userPortalPaths'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { useMockApi } from '../../../shared/api/apiClient'
import { familyApiService } from '../api/familyApiService'
import type { FamilyMember, ScanRecord } from '../../../shared/api/types'
import { useFamilyMe } from '../useFamilyMe'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { PortalIcon } from '../../../shared/ui/PortalIcon'
import { StatusBadge } from '../../../shared/ui/StatusBadge'

export function FamilyDashboardPage() {
  const { family } = useFamilyMe()
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [scans, setScans] = useState<ScanRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const loadedMembers = await familyApiService.getMembers()
      setMembers(loadedMembers)

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
    if (!useMockApi) {
      return () => window.clearTimeout(timeoutId)
    }
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
  const latestScanAt = scans.reduce((latest, scan) => {
    const scannedAt = Date.parse(scan.scannedAt)
    if (Number.isNaN(scannedAt)) return latest
    return Math.max(scannedAt, latest)
  }, 0)
  const verdictCounts = scans.reduce<Record<string, number>>((counts, scan) => {
    counts[scan.verdict] = (counts[scan.verdict] ?? 0) + 1
    return counts
  }, {})

  return (
    <>
      <header className="page-header page-header--split">
        <p className="eyebrow">Family Circle</p>
        <div className="page-header__title-row">
          <h1>Manage {family?.familyName ?? 'your family circle'}</h1>
          <Link className="button button--primary" to={FAMILY_MEMBERS_PATH}>
            Manage family
          </Link>
        </div>
        <p>Household profiles and recent scan activity.</p>
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
            <PortalIcon name="restrictions" />
          </span>
          <div>
            <span>Common requirements</span>
            <strong>{commonCodes.length}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">
            <PortalIcon name="clock" />
          </span>
          <div>
            <span>Last scan</span>
            <strong>{latestScanAt ? formatScanRecency(latestScanAt) : 'None yet'}</strong>
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
            <Link to={FAMILY_HISTORY_PATH}>View history</Link>
          </div>
          <div className="verdict-summary">
            {(['SAFE', 'WARNING', 'UNSAFE'] as const).map((verdict) => (
              <div key={verdict}>
                <strong>{verdictCounts[verdict] ?? 0}</strong>
                <StatusBadge status={verdict} />
              </div>
            ))}
          </div>
          {scans.length === 0 ? (
            <output className="empty-inline">
              No scans yet — scan a product in the CanMakan mobile app.
            </output>
          ) : (
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
          )}
        </section>

        <aside className="panel quick-actions" aria-labelledby="quick-actions-title">
          <p className="eyebrow">Shortcuts</p>
          <h2 id="quick-actions-title">Quick actions</h2>
          <Link to={FAMILY_VERDICT_TRENDS_PATH}>
            <span aria-hidden="true">
              <PortalIcon name="trends" />
            </span>
            <div>
              <strong>View verdict trends</strong>
              <span>See how Safe, Warning, and Unsafe outcomes change over time.</span>
            </div>
          </Link>
          <Link to={FAMILY_RESTRICTIONS_PATH}>
            <span aria-hidden="true">
              <PortalIcon name="restrictions" />
            </span>
            <div>
              <strong>Review restriction summary</strong>
              <span>See shared and unique dietary needs across profiles.</span>
            </div>
          </Link>
        </aside>
      </div>

      <div className="safety-disclaimer">
        <span className="safety-disclaimer__icon" aria-hidden="true">
          <PortalIcon name="info" />
        </span>
        <div>
          <strong>Safety reminder</strong>
          <p>
            CanMakan displays assessment results supplied by backend services. It
            does not provide medical advice or guarantee that a product is safe.
            Always check the package label.
          </p>
        </div>
      </div>
    </>
  )
}

function formatScanRecency(timestamp: number): string {
  const dayMs = 86_400_000
  const daysAgo = Math.floor((Date.now() - timestamp) / dayMs)
  if (daysAgo <= 0) return 'Today'
  if (daysAgo === 1) return 'Yesterday'
  if (daysAgo < 7) return `${daysAgo} days ago`
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(timestamp)
}
