import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import { restrictionGroups } from '../lib/profileOptions'
import type {
  FamilyMeRestrictionDetail,
  FamilyRestrictionSumRes,
} from '../../../shared/api/types'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'

type RestrictionGroupKey = 'religious' | 'allergy' | 'diet' | 'other'
type MatrixFilter = 'all' | RestrictionGroupKey
type SelectionTone = 'severe' | 'caution' | 'preference'

// Map each restriction code to the index of the group it belongs to in
// restrictionGroups, which is already ordered Religious requirements ->
// Allergies and intolerances -> Specific diets and health preferences. The
// grid rows below are sorted by this group index, then alphabetically by
// label within a group, so the layout stays consistent regardless of which
// member happens to introduce a code first.
const restrictionGroupIndex = new Map<string, number>()
const restrictionGroupKey = new Map<string, RestrictionGroupKey>()
restrictionGroups.forEach((group, groupIndex) => {
  const key: RestrictionGroupKey =
    groupIndex === 0 ? 'religious' : groupIndex === 1 ? 'allergy' : 'diet'
  for (const option of group.options) {
    restrictionGroupIndex.set(option.value, groupIndex)
    restrictionGroupKey.set(option.value, key)
  }
})

function groupKeyForCode(code: string): RestrictionGroupKey {
  return restrictionGroupKey.get(code) ?? 'other'
}

/** Maps a profile restriction severity to a badge tone for visual hierarchy. */
function selectionTone(
  severity: string | undefined,
  groupKey: RestrictionGroupKey,
): SelectionTone {
  const normalized = (severity ?? '').trim().toUpperCase()
  if (normalized === 'STRICT_AVOID' || normalized === 'AVOID') return 'severe'
  if (normalized === 'INTOLERANCE') return 'caution'
  if (normalized === 'PREFERENCE') return 'preference'
  if (groupKey === 'allergy') return 'severe'
  if (groupKey === 'religious') return 'caution'
  return 'preference'
}

function matchingRestriction(
  restrictions: FamilyMeRestrictionDetail[],
  matchCodes: Set<string>,
) {
  return restrictions.find((restriction) =>
    matchCodes.has(restriction.code.trim().toUpperCase()),
  )
}

const FILTER_OPTIONS: Array<{ value: MatrixFilter; label: string }> = [
  { value: 'all', label: 'All' },
  { value: 'allergy', label: 'Allergies' },
  { value: 'religious', label: 'Religious' },
  { value: 'diet', label: 'Diets & preferences' },
]

const DAIRY_FAMILY_CODES = new Set([
  'DAIRY',
  'LACTOSE_INTOLERANT',
  'DAIRY_FREE',
  'LACTOSE',
])

const REFERENCE_GROUPS = restrictionGroups.map((group, groupIndex) => ({
  ...group,
  groupKey: (groupIndex === 0
    ? 'religious'
    : groupIndex === 1
      ? 'allergy'
      : 'diet') as RestrictionGroupKey,
}))

function isHouseholdRestriction(
  optionCode: string,
  householdCodes: Set<string>,
): boolean {
  const code = optionCode.trim().toUpperCase()
  if (householdCodes.has(code)) return true
  if (code === 'DAIRY') {
    for (const dairyCode of DAIRY_FAMILY_CODES) {
      if (householdCodes.has(dairyCode)) return true
    }
  }
  return false
}

export function FamilyRestrictionSummaryPage() {
  const [data, setData] = useState<FamilyRestrictionSumRes | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [matrixFilter, setMatrixFilter] = useState<MatrixFilter>('all')
  const [openReferenceGroups, setOpenReferenceGroups] = useState<Set<string> | null>(
    null,
  )

  const loadSummary = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setData(await familyApiService.getRestrictionSummary())
      setOpenReferenceGroups(null)
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

  // Dynamically extract active members and unique restriction rows
  const activeMembers = useMemo(() => {
    return data?.familyMembers.filter((m) => m.isActive) || []
  }, [data])

  const restrictionRows = useMemo(() => {
    const seen = new Set<string>()
    const result: Array<{
      key: string
      label: string
      matchCodes: Set<string>
      groupKey: RestrictionGroupKey
    }> = []

    for (const member of activeMembers) {
      for (const restriction of member.restrictions) {
        const code = restriction.code.trim().toUpperCase()
        if (DAIRY_FAMILY_CODES.has(code)) {
          if (seen.has('DAIRY_FAMILY')) continue
          seen.add('DAIRY_FAMILY')
          result.push({
            key: 'DAIRY_FAMILY',
            label: 'Lactose Intolerance',
            matchCodes: DAIRY_FAMILY_CODES,
            groupKey: 'allergy',
          })
          continue
        }
        if (seen.has(code)) continue
        seen.add(code)
        result.push({
          key: code,
          label: restriction.displayName,
          matchCodes: new Set([code]),
          groupKey: groupKeyForCode(code),
        })
      }
    }

    // Order rows as Religious requirements, then Allergies and
    // intolerances, then Specific diets and health preferences, following
    // restrictionGroups, and alphabetically by label within each group. The
    // dairy-family alias groups with DAIRY. Any code absent from
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

  const householdCodes = useMemo(() => {
    const codes = new Set<string>()
    for (const member of activeMembers) {
      for (const restriction of member.restrictions) {
        codes.add(restriction.code.trim().toUpperCase())
      }
    }
    return codes
  }, [activeMembers])

  const defaultOpenReferenceGroups = useMemo(() => {
    const open = new Set<string>()
    for (const group of REFERENCE_GROUPS) {
      const usedInFamily = group.options.some((option) =>
        isHouseholdRestriction(option.value, householdCodes),
      )
      if (usedInFamily) open.add(group.label)
    }
    if (open.size === 0) {
      open.add('Allergies and intolerances')
    }
    return open
  }, [householdCodes])

  const openGroups = openReferenceGroups ?? defaultOpenReferenceGroups

  const toggleReferenceGroup = (label: string) => {
    setOpenReferenceGroups((current) => {
      const next = new Set(current ?? defaultOpenReferenceGroups)
      if (next.has(label)) next.delete(label)
      else next.add(label)
      return next
    })
  }

  const visibleRows = useMemo(() => {
    if (matrixFilter === 'all') return restrictionRows
    return restrictionRows.filter((row) => row.groupKey === matrixFilter)
  }, [matrixFilter, restrictionRows])

  return (
    <>
      <header className="page-header page-header--restriction-summary">
        <div>
          <p className="eyebrow">At-a-glance comparison</p>
          <h1>Family Allergy & Dietary Restriction Summary</h1>
          <p>
            This grid dynamically displays all active restrictions and allergies
            across your household.
          </p>
        </div>
      </header>

      {loading ? (
        <LoadingState label="Building family restriction summary..." />
      ) : error ? (
        <ErrorState message={error} onRetry={loadSummary} />
      ) : (
        <div className="restriction-summary-stack">

          {activeMembers.length === 0 ? (
            <EmptyState
              title="No summary available"
              description="Add an active family profile to build the restriction grid."
            />
          ) : (
            <>
              <div className="matrix-toolbar">
                <div className="matrix-legend" aria-label="Restriction grid legend">
                  <span>
                    <StatusBadge status="SELECTED" tone="severe" /> Strict avoid
                  </span>
                  <span>
                    <StatusBadge status="SELECTED" tone="caution" /> Intolerance
                  </span>
                  <span>
                    <StatusBadge status="SELECTED" tone="preference" /> Preference
                  </span>
                  <span>
                    <span className="matrix-cell matrix-cell--empty" aria-hidden="true">
                      —
                    </span>{' '}
                    Not selected
                  </span>
                </div>
                <div
                  className="matrix-filters"
                  role="group"
                  aria-label="Filter dietary restriction rows"
                >
                  {FILTER_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={
                        matrixFilter === option.value
                          ? 'matrix-filter matrix-filter--active'
                          : 'matrix-filter'
                      }
                      aria-pressed={matrixFilter === option.value}
                      onClick={() => setMatrixFilter(option.value)}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>

              <section className="panel panel--table panel--restriction-matrix">
                {visibleRows.length === 0 ? (
                  <EmptyState
                    title="No restrictions in this group"
                    description="Choose another filter or add matching dietary options to a profile."
                  />
                ) : (
                  <div className="responsive-table">
                    <table className="restriction-matrix">
                      <caption>Family restrictions by member</caption>
                      <thead>
                        <tr>
                          <th scope="col">Dietary restriction</th>
                          {activeMembers.map((member) => (
                            <th scope="col" key={member.profileId ?? member.userId}>
                              {member.name}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {visibleRows.map((row) => (
                          <tr key={row.key}>
                            <th scope="row">{row.label}</th>
                            {activeMembers.map((member) => {
                              // Lactose-family codes share one row so that any
                              // legacy or alternate spelling of the dairy
                              // restriction still displays together. Badge tone
                              // follows that member's recorded severity.
                              const matched = matchingRestriction(
                                member.restrictions,
                                row.matchCodes,
                              )

                              return (
                                <td key={member.profileId ?? member.userId}>
                                  {matched ? (
                                    <StatusBadge
                                      status="SELECTED"
                                      tone={selectionTone(matched.severity, row.groupKey)}
                                    />
                                  ) : (
                                    <span
                                      className="matrix-cell matrix-cell--empty"
                                      aria-label="Not selected"
                                    >
                                      —
                                    </span>
                                  )}
                                </td>
                              )
                            })}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
        </div>
      )}

      <section className="panel panel--restriction-reference">
        <h2>Dietary restriction reference</h2>
        <p>What each dietary restriction option means. Groups used by your family open by default.</p>
        <div className="restriction-reference">
          {REFERENCE_GROUPS.map((group) => {
            const isOpen = openGroups.has(group.label)
            const panelId = `restriction-reference-${group.groupKey}`
            const inFamilyCount = group.options.filter((option) =>
              isHouseholdRestriction(option.value, householdCodes),
            ).length

            return (
              <div className="restriction-reference-accordion" key={group.label}>
                <button
                  type="button"
                  className="restriction-reference-accordion__trigger"
                  aria-expanded={isOpen}
                  aria-controls={panelId}
                  onClick={() => toggleReferenceGroup(group.label)}
                >
                  <span className="restriction-reference-accordion__title">
                    <span aria-hidden="true">{isOpen ? '▾' : '▸'}</span>
                    {group.label}
                  </span>
                  {inFamilyCount > 0 ? (
                    <span className="restriction-reference-count">
                      {inFamilyCount} in your family
                    </span>
                  ) : (
                    <span className="restriction-reference-count restriction-reference-count--muted">
                      {group.options.length} options
                    </span>
                  )}
                </button>
                {isOpen ? (
                  <div className="restriction-reference-accordion__panel" id={panelId}>
                    <ul className="restriction-reference-list">
                      {[...group.options]
                        .sort((a, b) => a.label.localeCompare(b.label))
                        .map((option) => {
                          const inFamily = isHouseholdRestriction(
                            option.value,
                            householdCodes,
                          )
                          return (
                            <li
                              key={option.value}
                              className={
                                inFamily
                                  ? 'restriction-reference-item restriction-reference-item--in-family'
                                  : 'restriction-reference-item'
                              }
                            >
                              <div className="restriction-reference-item__title">
                                <span>{option.label}</span>
                                {inFamily ? (
                                  <span className="restriction-reference-chip">
                                    In your family
                                  </span>
                                ) : null}
                              </div>
                              <p className="restriction-reference-item__body">
                                {option.description}
                              </p>
                            </li>
                          )
                        })}
                    </ul>
                  </div>
                ) : null}
              </div>
            )
          })}
        </div>
      </section>
    </>
  )
}
