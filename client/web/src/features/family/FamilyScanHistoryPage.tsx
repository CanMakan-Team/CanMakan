import { useCallback, useEffect, useMemo, useState } from 'react'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { familyService } from './familyService'
import type {
  DataCompleteness,
  FamilyMember,
  ScanRecord,
  Verdict,
} from '../../shared/api/types'
import { Modal } from '../../shared/ui/Modal'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import { StatusBadge } from '../../shared/ui/StatusBadge'

type Period = 'ALL' | '7' | '30'

export function FamilyScanHistoryPage() {
  const [records, setRecords] = useState<ScanRecord[]>([])
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [memberId, setMemberId] = useState('ALL')
  const [verdict, setVerdict] = useState<'ALL' | Verdict>('ALL')
  const [completeness, setCompleteness] = useState<'ALL' | DataCompleteness>('ALL')
  const [period, setPeriod] = useState<Period>('ALL')
  const [selected, setSelected] = useState<ScanRecord | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadHistory = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [loadedRecords, loadedMembers] = await Promise.all([
        familyService.getScanHistory(),
        familyService.getMembers(),
      ])
      setRecords(loadedRecords)
      setMembers(loadedMembers)
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
    const now = new Date('2026-07-29T23:59:59+08:00').getTime()
    return records.filter((record) => {
      const ageInDays = (now - new Date(record.scannedAt).getTime()) / 86_400_000
      return (
        (memberId === 'ALL' || record.memberId === Number(memberId)) &&
        (verdict === 'ALL' || record.verdict === verdict) &&
        (completeness === 'ALL' ||
          record.dataCompleteness === completeness) &&
        (period === 'ALL' || ageInDays <= Number(period))
      )
    })
  }, [records, memberId, verdict, completeness, period])

  const resetFilters = () => {
    setMemberId('ALL')
    setVerdict('ALL')
    setCompleteness('ALL')
    setPeriod('ALL')
  }

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Feature 4</p>
          <h1>Family Scan History</h1>
          <p>
            Review final verdicts exactly as supplied by the assessment service.
            React does not infer or override them.
          </p>
        </div>
      </header>

      <section className="filter-bar" aria-label="Scan history filters">
        <div className="field-group">
          <label htmlFor="history-member">History filter</label>
          <select
            id="history-member"
            value={memberId}
            onChange={(event) => setMemberId(event.target.value)}
          >
            <option value="ALL">All family profiles</option>
            {members.map((member) => (
              <option key={member.memberId} value={member.memberId}>
                {member.profileName}
              </option>
            ))}
          </select>
          <span className="field-hint">Reporting only; not the active assessment profile.</span>
        </div>
        <div className="field-group">
          <label htmlFor="history-verdict">Verdict</label>
          <select
            id="history-verdict"
            value={verdict}
            onChange={(event) => setVerdict(event.target.value as 'ALL' | Verdict)}
          >
            <option value="ALL">All verdicts</option>
            <option value="SAFE">Safe</option>
            <option value="WARNING">Warning</option>
            <option value="AVOID">Avoid</option>
            <option value="INCOMPLETE">Incomplete</option>
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="history-period">Date period</label>
          <select
            id="history-period"
            value={period}
            onChange={(event) => setPeriod(event.target.value as Period)}
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
              setCompleteness(event.target.value as 'ALL' | DataCompleteness)
            }
          >
            <option value="ALL">All records</option>
            <option value="COMPLETE">Complete</option>
            <option value="PARTIAL">Partial</option>
            <option value="PRODUCT_NOT_FOUND">Product not found</option>
          </select>
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
            <table className="data-table">
              <caption>Family scan assessment history</caption>
              <thead>
                <tr>
                  <th scope="col">Product</th>
                  <th scope="col">Brand</th>
                  <th scope="col">Evaluated profile</th>
                  <th scope="col">Verdict</th>
                  <th scope="col">Notable ingredient</th>
                  <th scope="col">Resolved name / rule</th>
                  <th scope="col">Data completeness</th>
                  <th scope="col">Scan date & time</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((record) => (
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
                    <td><StatusBadge status={record.verdict} /></td>
                    <td>{record.detectedIngredient}</td>
                    <td>{record.resolvedIngredient}</td>
                    <td><StatusBadge status={record.dataCompleteness} /></td>
                    <td>{new Date(record.scannedAt).toLocaleString('en-SG')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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
            <div><dt>Final verdict</dt><dd>{selected.verdict}</dd></div>
            <div><dt>Detected ingredient</dt><dd>{selected.detectedIngredient}</dd></div>
            <div><dt>Resolved ingredient</dt><dd>{selected.resolvedIngredient}</dd></div>
            <div><dt>Matched restriction</dt><dd>{selected.matchedRestriction}</dd></div>
            <div className="detail-grid__wide">
              <dt>Explanation</dt><dd>{selected.explanation}</dd>
            </div>
            <div className="detail-grid__wide">
              <dt>Data source / status</dt>
              <dd>{selected.dataSource} · {selected.dataCompleteness}</dd>
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
