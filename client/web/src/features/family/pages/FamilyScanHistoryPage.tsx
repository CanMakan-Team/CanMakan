import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { familyApiService } from '../api/familyApiService'
import type {
  DataCompleteness,
  FamilyMember,
  ScanRecord,
  ScanVerdict,
} from '../../../shared/api/types'
import { Modal } from '../../../shared/ui/Modal'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import {
  displayScanField,
  formatRelativeScanTime,
  hasScanFieldValue,
} from '../lib/scanHistoryDisplay'

type Period = 'ALL' | '7' | '30'

const PAGE_SIZE = 15

export function FamilyScanHistoryPage() {
  const [records, setRecords] = useState<ScanRecord[]>([])
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [memberId, setMemberId] = useState('ALL')
  const [verdict, setVerdict] = useState<'ALL' | ScanVerdict>('ALL')
  const [completeness, setCompleteness] = useState<'ALL' | DataCompleteness>('ALL')
  const [period, setPeriod] = useState<Period>('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<ScanRecord | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  // Snapshot "now" in state so filter/memo work stays pure during render.
  const [nowMs, setNowMs] = useState(() => Date.now())

  const loadHistory = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [loadedRecords, loadedMembers] = await Promise.all([
        familyApiService.getScanHistory(),
        familyApiService.getMembers(),
      ])
      setRecords(loadedRecords)
      setMembers(loadedMembers)
      setNowMs(Date.now())
      setPage(0)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadHistory(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadHistory])

  const filtered = useMemo(() => {
    const needle = searchQuery.trim().toLowerCase()
    return records.filter((record) => {
      const ageInDays = (nowMs - new Date(record.scannedAt).getTime()) / 86_400_000
      const matchesSearch =
        !needle ||
        record.product.toLowerCase().includes(needle) ||
        record.brand.toLowerCase().includes(needle)
      return (
        matchesSearch &&
        (memberId === 'ALL' || record.memberId === Number(memberId)) &&
        (verdict === 'ALL' || record.verdict === verdict) &&
        (completeness === 'ALL' ||
          record.dataCompleteness === completeness) &&
        (period === 'ALL' || ageInDays <= Number(period))
      )
    })
  }, [records, memberId, verdict, completeness, period, searchQuery, nowMs])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages - 1)
  const paged = useMemo(() => {
    const start = safePage * PAGE_SIZE
    return filtered.slice(start, start + PAGE_SIZE)
  }, [filtered, safePage])

  const showDetectedIngredient = useMemo(
    () => filtered.some((record) => hasScanFieldValue(record.detectedIngredient)),
    [filtered],
  )
  const showResolvedIngredient = useMemo(
    () => filtered.some((record) => hasScanFieldValue(record.resolvedIngredient)),
    [filtered],
  )

  const resetFilters = () => {
    setMemberId('ALL')
    setVerdict('ALL')
    setCompleteness('ALL')
    setPeriod('ALL')
    setSearchQuery('')
    setPage(0)
    setNowMs(Date.now())
  }

  const updateFilter = <T,>(setter: (value: T) => void, value: T) => {
    setter(value)
    setPage(0)
  }

  return (
    <>
      <header className="page-header page-header--scan-history">
        <div>
          <p className="eyebrow">Family Circle</p>
          <h1>Family Scan History</h1>
          <p>
            Review past scan verdicts supplied by CanMakan.
          </p>
        </div>
      </header>

      <section className="history-filters" aria-label="Scan history filters">
        <div className="field-group history-filters__search">
          <label htmlFor="history-search">Search</label>
          <input
            id="history-search"
            type="search"
            value={searchQuery}
            placeholder="Search product or brand"
            onChange={(event) => updateFilter(setSearchQuery, event.target.value)}
          />
        </div>
        <div className="filter-bar filter-bar--history">
          <div className="field-group">
            <label htmlFor="history-member">History filter</label>
            <select
              id="history-member"
              value={memberId}
              onChange={(event) => updateFilter(setMemberId, event.target.value)}
            >
              <option value="ALL">All family profiles</option>
              {members.map((member) => (
                <option key={member.memberId} value={member.memberId}>
                  {member.profileName}
                </option>
              ))}
            </select>
          </div>
          <div className="field-group">
            <label htmlFor="history-verdict">Verdict</label>
            <select
              id="history-verdict"
              value={verdict}
              onChange={(event) =>
                updateFilter(setVerdict, event.target.value as 'ALL' | ScanVerdict)
              }
            >
              <option value="ALL">All verdicts</option>
              <option value="SAFE">Safe</option>
              <option value="WARNING">Warning</option>
              <option value="UNSAFE">Unsafe</option>
            </select>
          </div>
          <div className="field-group">
            <label htmlFor="history-period">Date period</label>
            <select
              id="history-period"
              value={period}
              onChange={(event) => {
                updateFilter(setPeriod, event.target.value as Period)
                setNowMs(Date.now())
              }}
            >
              <option value="ALL">All dates</option>
              <option value="7">Last 7 days</option>
              <option value="30">Last 30 days</option>
            </select>
          </div>
          <div className="field-group">
            <label htmlFor="history-completeness">Data completeness</label>
            <select
              id="history-completeness"
              value={completeness}
              onChange={(event) =>
                updateFilter(
                  setCompleteness,
                  event.target.value as 'ALL' | DataCompleteness,
                )
              }
            >
              <option value="ALL">All records</option>
              <option value="COMPLETE">Complete</option>
              <option value="PARTIAL">Partial</option>
              <option value="PRODUCT_NOT_FOUND">Product not found</option>
            </select>
          </div>
        </div>
      </section>

      {loading ? (
        <LoadingState label="Loading supplied scan assessments…" />
      ) : error ? (
        <ErrorState message={error} onRetry={loadHistory} />
      ) : records.length === 0 ? (
        <EmptyState
          title="No scan records"
          description="Assessment history will appear here when supplied by the service."
        />
      ) : filtered.length === 0 ? (
        <div className="page-state">
          <strong>No records match these filters.</strong>
          <p>Adjust or clear the history filters to see other assessments.</p>
          <button className="button button--secondary" type="button" onClick={resetFilters}>
            Clear filters
          </button>
        </div>
      ) : (
        <section className="panel panel--table">
          <div className="responsive-table">
            <table className="data-table data-table--history">
              <caption>Family scan assessment history</caption>
              <thead>
                <tr>
                  <th scope="col">Product</th>
                  <th scope="col">Brand</th>
                  <th scope="col">Evaluated profile</th>
                  <th scope="col">Verdict</th>
                  {showDetectedIngredient ? (
                    <th scope="col">Notable ingredient</th>
                  ) : null}
                  {showResolvedIngredient ? (
                    <th scope="col">Resolved name / rule</th>
                  ) : null}
                  <th scope="col">Data completeness</th>
                  <th scope="col">Scan date & time</th>
                </tr>
              </thead>
              <tbody>
                {paged.map((record) => {
                  const scanned = formatRelativeScanTime(record.scannedAt, nowMs)
                  return (
                    <tr
                      key={record.scanId}
                      className="clickable-row"
                      onClick={() => setSelected(record)}
                    >
                      <th scope="row">
                        <button type="button" onClick={() => setSelected(record)}>
                          {record.product}
                        </button>
                      </th>
                      <td>{record.brand}</td>
                      <td>{record.evaluatedProfile}</td>
                      <td>
                        <StatusBadge status={record.verdict} />
                      </td>
                      {showDetectedIngredient ? (
                        <td className={!hasScanFieldValue(record.detectedIngredient) ? 'table-empty' : undefined}>
                          {displayScanField(record.detectedIngredient)}
                        </td>
                      ) : null}
                      {showResolvedIngredient ? (
                        <td className={!hasScanFieldValue(record.resolvedIngredient) ? 'table-empty' : undefined}>
                          {displayScanField(record.resolvedIngredient)}
                        </td>
                      ) : null}
                      <td>
                        <StatusBadge status={record.dataCompleteness} />
                      </td>
                      <td>
                        <time dateTime={record.scannedAt} title={scanned.absolute}>
                          {scanned.label}
                        </time>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <nav className="analytics-pagination" aria-label="Scan history pages">
            <button
              type="button"
              className="button button--secondary"
              disabled={safePage === 0}
              onClick={() => setPage(safePage - 1)}
            >
              Previous
            </button>
            <span>
              Page {safePage + 1} of {totalPages}
              {' · '}
              {filtered.length.toLocaleString()} records
              {filtered.length > PAGE_SIZE
                ? ` · ${PAGE_SIZE} per page`
                : null}
            </span>
            <button
              type="button"
              className="button button--secondary"
              disabled={safePage >= totalPages - 1}
              onClick={() => setPage(safePage + 1)}
            >
              Next
            </button>
          </nav>
        </section>
      )}

      {selected && (
        <Modal
          title="Assessment details"
          description="Backend-supplied result for the evaluated profile."
          onClose={() => setSelected(null)}
          wide
        >
          <div className="assessment-heading">
            <div>
              <p className="eyebrow">{selected.brand}</p>
              <h3>{selected.product}</h3>
              <span>Evaluated for {selected.evaluatedProfile}</span>
            </div>
            <StatusBadge status={selected.verdict} />
          </div>
          {selected.dataCompleteness !== 'COMPLETE' && (
            <div className="notice notice--warning">
              <strong>Data limitation: {selected.dataCompleteness.replaceAll('_', ' ')}</strong>
              <p>This limitation has not been converted into a Safe verdict.</p>
            </div>
          )}
          <dl className="detail-grid">
            <div>
              <dt>Final verdict</dt>
              <dd>{selected.verdict}</dd>
            </div>
            <div>
              <dt>Detected ingredient</dt>
              <dd>{displayScanField(selected.detectedIngredient, 'None flagged')}</dd>
            </div>
            <div>
              <dt>Resolved ingredient</dt>
              <dd>{displayScanField(selected.resolvedIngredient, 'N/A')}</dd>
            </div>
            <div>
              <dt>Matched restriction</dt>
              <dd>{displayScanField(selected.matchedRestriction, 'N/A')}</dd>
            </div>
            <div className="detail-grid__wide">
              <dt>Explanation</dt>
              <dd>{selected.explanation}</dd>
            </div>
            <div className="detail-grid__wide">
              <dt>Data source / status</dt>
              <dd>
                {selected.dataSource} · {selected.dataCompleteness}
              </dd>
            </div>
            <div className="detail-grid__wide">
              <dt>Scan date & time</dt>
              <dd>{formatRelativeScanTime(selected.scannedAt, nowMs).absolute}</dd>
            </div>
            {selected.suggestedAlternative && (
              <div className="detail-grid__wide">
                <dt>Suggested alternative</dt>
                <dd>{selected.suggestedAlternative}</dd>
              </div>
            )}
          </dl>
          <div className="safety-disclaimer">
            <strong>Safety disclaimer</strong>
            <p>
              This prototype displays supplied assessment data and does not
              provide medical advice or guarantee food safety. Check the product
              label and seek professional advice when needed.
            </p>
          </div>
        </Modal>
      )}
    </>
  )
}
