import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import { restrictionGroups } from '../lib/profileOptions'
import type { FamilyRestrictionSumRes } from '../../../shared/api/types'
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
        <LoadingState label="Building family restriction summary..." />
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
            <span><StatusBadge status="SELECTED" /> Option selected in dietary profile</span>
            <span><i className="matrix-cell"> </i> Not selected</span>
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
                  <tr key={member.profileId ?? member.userId}>
                    <th scope="row">{member.name}</th>
                    {columns.map((colName) => {
                      // Any restriction present on the dietary profile counts as
                      // selected for this column; the grid does not grade by
                      // severity, it only reports what the profile has chosen.
                      const isSelected = member.restrictions.some(
                        (r) => r.displayName === colName
                      )

                      return (
                        <td key={colName}>
                          {isSelected ? (
                            <StatusBadge status="SELECTED" />
                          ) : (
                            <span className="matrix-cell" aria-label="Not selected"> </span>
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

      <section className="panel">
        <h2>Dietary restriction reference</h2>
        <p>What each dietary restriction option means.</p>
        {restrictionGroups.map((group) => (
          <div className="restriction-reference-group" key={group.label}>
            <h3>{group.label}</h3>
            <dl className="detail-grid">
              {[...group.options]
                .sort((a, b) => a.label.localeCompare(b.label))
                .map((option) => (
                  <div key={option.value}>
                    <dt>{option.label}</dt>
                    <dd>{option.description}</dd>
                  </div>
                ))}
            </dl>
          </div>
        ))}
      </section>
    </>
  )
}