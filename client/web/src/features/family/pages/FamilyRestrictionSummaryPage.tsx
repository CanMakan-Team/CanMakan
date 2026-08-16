import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import { restrictionGroups } from '../lib/profileOptions'
import type { FamilyRestrictionSumRes } from '../../../shared/api/types'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'

// Map each restriction code to the index of the group it belongs to in
// restrictionGroups, which is already ordered Religious requirements ->
// Allergies and intolerances -> Specific diets and health preferences. The
// grid columns below are sorted by this group index, then alphabetically by
// label within a group, so the layout stays consistent regardless of which
// member happens to introduce a code first.
const restrictionGroupIndex = new Map<string, number>()
restrictionGroups.forEach((group, groupIndex) => {
  for (const option of group.options) {
    restrictionGroupIndex.set(option.value, groupIndex)
  }
})

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
    const dairyFamilyCodes = new Set([
      'DAIRY',
      'LACTOSE_INTOLERANT',
      'DAIRY_FREE',
      'LACTOSE',
    ])
    const seen = new Set<string>()
    const result: Array<{ key: string; label: string; matchCodes: Set<string> }> = []

    for (const member of activeMembers) {
      for (const restriction of member.restrictions) {
        const code = restriction.code.trim().toUpperCase()
        if (dairyFamilyCodes.has(code)) {
          if (seen.has('DAIRY_FAMILY')) continue
          seen.add('DAIRY_FAMILY')
          result.push({
            key: 'DAIRY_FAMILY',
            label: 'Lactose Intolerance',
            matchCodes: dairyFamilyCodes,
          })
          continue
        }
        if (seen.has(code)) continue
        seen.add(code)
        result.push({
          key: code,
          label: restriction.displayName,
          matchCodes: new Set([code]),
        })
      }
    }

    // Order columns as Religious requirements, then Allergies and
    // intolerances, then Specific diets and health preferences, following
    // restrictionGroups, and alphabetically by label within each group. The
    // dairy-family alias column groups with DAIRY. Any code absent from
    // restrictionGroups sorts to the end.
    return result.sort((a, b) => {
      const groupKeyA = a.key === 'DAIRY_FAMILY' ? 'DAIRY' : a.key
      const groupKeyB = b.key === 'DAIRY_FAMILY' ? 'DAIRY' : b.key
      const groupIndexA = restrictionGroupIndex.get(groupKeyA) ?? Number.MAX_SAFE_INTEGER
      const groupIndexB = restrictionGroupIndex.get(groupKeyB) ?? Number.MAX_SAFE_INTEGER
      if (groupIndexA !== groupIndexB) return groupIndexA - groupIndexB
      return a.label.localeCompare(b.label)
    })
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
                  {columns.map((column) => (
                    <th scope="col" key={column.key}>
                      {column.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {activeMembers.map((member) => (
                  <tr key={member.profileId ?? member.userId}>
                    <th scope="row">{member.name}</th>
                    {columns.map((column) => {
                      // Any restriction present on the dietary profile counts as
                      // selected for this column; the grid does not grade by
                      // severity, it only reports what the profile has chosen.
                      // Lactose-family codes share one column because
                      // the backend treats those codes as dairy-family aliases.
                      const isSelected = member.restrictions.some((r) =>
                        column.matchCodes.has(r.code.trim().toUpperCase())
                      )

                      return (
                        <td key={column.key}>
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