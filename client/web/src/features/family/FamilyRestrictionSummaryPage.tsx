import { useCallback, useEffect, useState } from 'react'
import { getErrorMessage } from '../../api/apiErrors'
import { familyService } from '../../api/familyService'
import type { FamilyMember } from '../../api/types'
import { EmptyState, ErrorState, LoadingState } from '../../components/PageState'
import { summaryRestrictions } from './profileOptions'

export function FamilyRestrictionSummaryPage() {
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadSummary = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setMembers(await familyService.getRestrictionSummary())
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

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">At-a-glance comparison</p>
          <h1>Family Allergy & Dietary Requirement Summary</h1>
          <p>
            This grid is derived from each returned profile’s common requirements
            and individual restriction codes.
          </p>
        </div>
      </header>

      {loading ? (
        <LoadingState label="Building family restriction summary…" />
      ) : error ? (
        <ErrorState message={error} onRetry={loadSummary} />
      ) : members.length === 0 ? (
        <EmptyState
          title="No summary available"
          description="Add a family profile to build the restriction grid."
        />
      ) : (
        <section className="panel">
          <div className="matrix-legend" aria-label="Restriction grid legend">
            <span><i className="matrix-cell matrix-cell--common">C</i> Common requirement</span>
            <span><i className="matrix-cell matrix-cell--individual">✓</i> Individual restriction</span>
            <span><i className="matrix-cell">—</i> Not recorded</span>
          </div>
          <div className="responsive-table">
            <table className="restriction-matrix">
              <caption>Family restrictions by member</caption>
              <thead>
                <tr>
                  <th scope="col">Family member</th>
                  {summaryRestrictions.map((restriction) => (
                    <th scope="col" key={restriction.value}>
                      {restriction.shortLabel}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {members.map((member) => (
                  <tr key={member.memberId}>
                    <th scope="row">
                      {member.profileName}
                      <span>{member.relationship.toLowerCase()}</span>
                    </th>
                    {summaryRestrictions.map((restriction) => {
                      const isCommon = member.commonRequirements.includes(
                        restriction.value,
                      )
                      const isIndividual = member.restrictions.includes(
                        restriction.value,
                      )
                      const label = isCommon
                        ? 'Common requirement'
                        : isIndividual
                          ? 'Individual restriction'
                          : 'Not recorded'
                      return (
                        <td key={restriction.value}>
                          <span
                            className={`matrix-cell ${
                              isCommon
                                ? 'matrix-cell--common'
                                : isIndividual
                                  ? 'matrix-cell--individual'
                                  : ''
                            }`}
                            aria-label={`${restriction.shortLabel}: ${label}`}
                          >
                            {isCommon ? 'C' : isIndividual ? '✓' : '—'}
                          </span>
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
