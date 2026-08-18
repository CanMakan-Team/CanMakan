import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { useMockApi } from '../../../shared/api/apiClient'
import { familyApiService } from '../api/familyApiService'
import {
  MATRIX_FILTER_OPTIONS,
  REFERENCE_GROUPS,
  buildHouseholdCodes,
  buildRestrictionMatrixRows,
  defaultOpenReferenceGroups,
  filterMatrixRows,
  isHouseholdRestriction,
  matchingRestriction,
  selectionTone,
  type MatrixFilter,
} from '../lib/restrictionMatrix'
import type { FamilyRestrictionSumRes } from '../../../shared/api/types'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'

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
    if (!useMockApi) {
      return () => window.clearTimeout(timeoutId)
    }
    window.addEventListener('canmakan:family-data-changed', loadSummary)
    return () => {
      window.clearTimeout(timeoutId)
      window.removeEventListener('canmakan:family-data-changed', loadSummary)
    }
  }, [loadSummary])

  const activeMembers = useMemo(
    () => data?.familyMembers.filter((member) => member.isActive) || [],
    [data],
  )

  const restrictionRows = useMemo(
    () => buildRestrictionMatrixRows(activeMembers),
    [activeMembers],
  )

  const householdCodes = useMemo(
    () => buildHouseholdCodes(activeMembers),
    [activeMembers],
  )

  const defaultOpenReferenceGroupsValue = useMemo(
    () => defaultOpenReferenceGroups(householdCodes),
    [householdCodes],
  )

  const openGroups = openReferenceGroups ?? defaultOpenReferenceGroupsValue

  const toggleReferenceGroup = (label: string) => {
    setOpenReferenceGroups((current) => {
      const next = new Set(current ?? defaultOpenReferenceGroupsValue)
      if (next.has(label)) next.delete(label)
      else next.add(label)
      return next
    })
  }

  const visibleRows = useMemo(
    () => filterMatrixRows(restrictionRows, matrixFilter),
    [matrixFilter, restrictionRows],
  )

  return (
    <>
      <header className="page-header page-header--restriction-summary">
        <div>
          <p className="eyebrow">Family Circle</p>
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
          <div className="notice notice--warning">
            <strong>Profile summary only</strong>
            <p>
              This grid records configured requirements; it does not rate medical
              risk or make a food-safety assessment.
            </p>
          </div>

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
                  {MATRIX_FILTER_OPTIONS.map((option) => (
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
