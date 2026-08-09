import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type { FamilyRestrictionSumRes, Verdict } from '../../../shared/api/types'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'

export function FamilyRestrictionSummaryPage() {
  const [data, setData] = useState<FamilyRestrictionSumRes | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadSummary = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setData(await familyApiService.getRestrictionSummary())
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadSummary(), 0)
    window.addEventListener('canmakan:family-data-changed', loadSummary)
    return () => {
      window.clearTimeout(timeoutId)
      window.removeEventListener('canmakan:family-data-changed', loadSummary)
    }
  }, [loadSummary])

  // Dynamically extract active members and unique columns
  const activeMembers = useMemo(() => {
    return data?.familyMembers.filter((m) => m.isActive) || []
  }, [data])

  const columns = useMemo(() => {
    const allRestrictions = activeMembers.flatMap((m) =>
      m.restrictions.map((r) => r.displayName)
    )
    return Array.from(new Set(allRestrictions))
  }, [activeMembers])

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">At-a-glance comparison</p>
          <h1>Family Allergy & Dietary Requirement Summary</h1>
          <p>
            This grid dynamically aggregates all active restrictions and allergies
            across your household.
          </p>
        </div>
      </header>

      {loading ? (
        <LoadingState label="Building family restriction summary " />
      ) : error ? (
        <ErrorState message={error} onRetry={loadSummary} />
      ) : activeMembers.length === 0 ? (
        <EmptyState
          title="No summary available"
          description="Add an active family profile to build the restriction grid."
        />
      ) : (
        <section className="panel">
          <div className="matrix-legend" aria-label="Restriction grid legend">
            <span><StatusBadge status="AVOID" /> Strict Avoid / Unsafe</span>
            <span><StatusBadge status="WARNING" /> Intolerance / Warning</span>
            <span><i className="matrix-cell"> </i> Not recorded</span>
          </div>

          <div className="responsive-table">
            <table className="restriction-matrix">
              <caption>Family restrictions by member</caption>
              <thead>
                <tr>
                  <th scope="col">Family member</th>
                  {columns.map((colName) => (
                    <th scope="col" key={colName}>
                      {colName}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {activeMembers.map((member) => (
                  <tr key={member.userId}>
                    <th scope="row">{member.name}</th>
                    {columns.map((colName) => {
                      const restriction = member.restrictions.find(
                        (r) => r.displayName === colName
                      )
                      
                      let status: Verdict | undefined
                      if (restriction) {
                        const sev = restriction.severity.toUpperCase()
                        status = ['STRICT_AVOID', 'STRICT', 'HIGH', 'UNSAFE'].includes(sev)
                          ? 'AVOID'
                          : ['INTOLERANCE', 'WARNING', 'MEDIUM', 'MODERATE'].includes(sev)
                          ? 'WARNING'
                          : 'SAFE'
                      }

                      return (
                        <td key={colName}>
                          {status ? (
                            <StatusBadge status={status} />
                          ) : (
                            <span className="matrix-cell" aria-label="Not recorded"> </span>
                          )}
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <div className="notice notice--neutral">
        <strong>Profile summary only</strong>
        <p>
          This grid records configured requirements; it does not rate medical
          risk or make a food-safety assessment.
        </p>
      </div>
    </>
  )
}