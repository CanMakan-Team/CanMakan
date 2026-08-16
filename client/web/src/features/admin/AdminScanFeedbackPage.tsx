import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { adminService } from './adminService'
import type { AdminScanFeedbackFilters, AdminScanFeedbackItem, AdminScanFeedbackListResponse } from './models'
import {
  selfProfileApiService,
  type DietaryRestrictionOption,
} from '../family/api/selfProfileApiService'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { Modal } from '../../shared/ui/Modal'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import { ThumbDownIcon, ThumbUpIcon } from '../../shared/ui/ThumbIcons'

type FeedbackTypeFilter = 'ALL' | 'POSITIVE' | 'NEGATIVE'
type ResolvedFilter = 'ALL' | 'RESOLVED' | 'UNRESOLVED'

const PERIOD_OPTIONS = [
  { label: 'Past week', days: 7 },
  { label: 'Past 2 weeks', days: 14 },
  { label: 'Past month', days: 30 },
  { label: 'Past 6 months', days: 180 },
] as const

const DEFAULT_PERIOD_DAYS = 30
const COMMENT_PREVIEW_LENGTH = 60
const FEEDBACK_ICON_SIZE = 16
const PAGE_SIZE = 30

function toFilters(
  keyword: string,
  restrictionCode: string,
  periodDays: number,
  typeFilter: FeedbackTypeFilter,
  resolvedFilter: ResolvedFilter,
): AdminScanFeedbackFilters {
  const filters: AdminScanFeedbackFilters = { periodDays }
  const trimmedKeyword = keyword.trim()
  if (trimmedKeyword) filters.keyword = trimmedKeyword
  if (restrictionCode) filters.restrictionCode = restrictionCode
  if (typeFilter !== 'ALL') filters.isPositive = typeFilter === 'POSITIVE'
  if (resolvedFilter !== 'ALL') filters.resolved = resolvedFilter === 'RESOLVED'
  return filters
}

function formatCreatedAt(value: string): string {
  return new Date(value).toLocaleString('en-SG')
}

function previewComment(comment: string): string {
  const trimmed = comment.trim()
  return trimmed.length > COMMENT_PREVIEW_LENGTH
    ? `${trimmed.slice(0, COMMENT_PREVIEW_LENGTH).trimEnd()}…`
    : trimmed
}

export function AdminScanFeedbackPage() {
  const [keyword, setKeyword] = useState('')
  const [restrictionCode, setRestrictionCode] = useState('')
  const [periodDays, setPeriodDays] = useState(DEFAULT_PERIOD_DAYS)
  const [typeFilter, setTypeFilter] = useState<FeedbackTypeFilter>('ALL')
  const [resolvedFilter, setResolvedFilter] = useState<ResolvedFilter>('ALL')
  const [filters, setFilters] = useState<AdminScanFeedbackFilters>({
    periodDays: DEFAULT_PERIOD_DAYS,
  })

  const [data, setData] = useState<AdminScanFeedbackListResponse | null>(null)
  const [restrictions, setRestrictions] = useState<DietaryRestrictionOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [selectedComment, setSelectedComment] = useState<AdminScanFeedbackItem | null>(null)
  const [savingId, setSavingId] = useState<number | null>(null)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const [page, setPage] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [feedbackResponse, restrictionCatalog] = await Promise.all([
        adminService.getScanFeedback(filters),
        selfProfileApiService.getCatalog(),
      ])
      setData(feedbackResponse)
      setRestrictions(restrictionCatalog)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setNotice('')
    setActionError('')
    setPage(0)
    setFilters(toFilters(keyword, restrictionCode, periodDays, typeFilter, resolvedFilter))
  }

  const changeResolved = async (item: AdminScanFeedbackItem, resolved: boolean) => {
    setNotice('')
    setActionError('')
    setSavingId(item.id)
    try {
      await adminService.updateScanFeedbackResolved(item.id, resolved)
      setNotice(`Feedback marked as ${resolved ? 'Resolved' : 'Not resolved'}.`)
      await load()
    } catch (caughtError) {
      setActionError(getErrorMessage(caughtError))
    } finally {
      setSavingId(null)
    }
  }

  const summary = data?.summary
  const totalPages = Math.max(1, Math.ceil((data?.items.length ?? 0) / PAGE_SIZE))
  const safePage = Math.min(page, totalPages - 1)
  const visibleItems = data?.items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE) ?? []

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">System administrators only</p>
          <h1>Handle User Feedback</h1>
          <p>
            Review thumbs up/down feedback reported against scan verdicts, and
            mark reports as resolved once they have been reviewed.
          </p>
        </div>
      </header>

      <section className="summary-grid" aria-label="Feedback summary">
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">Σ</span>
          <div>
            <span>Total feedback</span>
            <strong>{summary ? summary.totalFeedback.toLocaleString() : '—'}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">%</span>
          <div>
            <span>Percentage negative feedback</span>
            <strong>{summary ? `${summary.negativePercentage.toFixed(1)}%` : '—'}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">◷</span>
          <div>
            <span>Feedback per day</span>
            <strong>{summary ? summary.feedbackPerDay.toFixed(2) : '—'}</strong>
          </div>
        </article>
        <article className="summary-card">
          <span className="summary-card__icon" aria-hidden="true">!</span>
          <div>
            <span>Negative feedback per day</span>
            <strong>{summary ? summary.negativeFeedbackPerDay.toFixed(2) : '—'}</strong>
          </div>
        </article>
      </section>

      <form
        className="filter-bar filter-bar--feedback"
        aria-label="Scan feedback filters"
        onSubmit={applyFilters}
      >
        <div className="field-group field-group--search">
          <label htmlFor="feedback-keyword">Keyword</label>
          <input
            id="feedback-keyword"
            type="search"
            placeholder="Product or user email contains"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>
        <div className="field-group">
          <label htmlFor="feedback-restriction">Dietary restriction</label>
          <select
            id="feedback-restriction"
            value={restrictionCode}
            onChange={(event) => setRestrictionCode(event.target.value)}
          >
            <option value="">All Restrictions</option>
            {restrictions.map((restriction) => (
              <option key={restriction.code} value={restriction.code}>
                {restriction.displayName}
              </option>
            ))}
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="feedback-period">Date period</label>
          <select
            id="feedback-period"
            value={periodDays}
            onChange={(event) => setPeriodDays(Number(event.target.value))}
          >
            {PERIOD_OPTIONS.map((option) => (
              <option key={option.days} value={option.days}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="feedback-type">Feedback type</label>
          <select
            id="feedback-type"
            value={typeFilter}
            onChange={(event) => setTypeFilter(event.target.value as FeedbackTypeFilter)}
          >
            <option value="ALL">All Types</option>
            <option value="POSITIVE">Positive</option>
            <option value="NEGATIVE">Negative</option>
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="feedback-resolved">Resolved</label>
          <select
            id="feedback-resolved"
            value={resolvedFilter}
            onChange={(event) => setResolvedFilter(event.target.value as ResolvedFilter)}
          >
            <option value="ALL">All</option>
            <option value="RESOLVED">Resolved</option>
            <option value="UNRESOLVED">Not resolved</option>
          </select>
        </div>
        <button className="button button--dark" type="submit">
          Apply filters
        </button>
      </form>

      <div className="sr-live" aria-live="polite">{notice}</div>
      {actionError && (
        <p className="form-message form-message--error" role="alert">
          {actionError}
        </p>
      )}

      {loading ? (
        <LoadingState label="Loading user feedback…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          title="No feedback matches"
          description="Change the keyword, restriction, period, type or resolved filters and try again."
        />
      ) : (
        <section className="panel panel--table">
          <div className="responsive-table">
            <table className="data-table">
              <caption>User feedback reported against scan verdicts</caption>
              <thead>
                <tr>
                  <th>User Email</th>
                  <th>Product Scanned</th>
                  <th className="feedback-type-column">
                    <span className="feedback-type-header" aria-hidden="true">
                      <ThumbUpIcon size={FEEDBACK_ICON_SIZE} />
                      <ThumbDownIcon size={FEEDBACK_ICON_SIZE} />
                    </span>
                    <span className="sr-only">Feedback Type</span>
                  </th>
                  <th>User Feedback</th>
                  <th>Created At</th>
                  <th>Resolved</th>
                </tr>
              </thead>
              <tbody>
                {visibleItems.map((item) => (
                  <tr key={item.id}>
                    <th scope="row">{item.userEmail ?? '—'}</th>
                    <td>{item.productName}</td>
                    <td className="feedback-type-column">
                      <span aria-label={item.isPositive ? 'Positive feedback' : 'Negative feedback'}>
                        {item.isPositive ? (
                          <ThumbUpIcon size={FEEDBACK_ICON_SIZE} />
                        ) : (
                          <ThumbDownIcon size={FEEDBACK_ICON_SIZE} />
                        )}
                      </span>
                    </td>
                    <td>
                      {item.userComments ? (
                        <button
                          className="text-button"
                          type="button"
                          onClick={() => setSelectedComment(item)}
                        >
                          {previewComment(item.userComments)}
                        </button>
                      ) : (
                        <span>—</span>
                      )}
                    </td>
                    <td>
                      <time dateTime={item.createdAt}>{formatCreatedAt(item.createdAt)}</time>
                    </td>
                    <td>
                      <select
                        className="table-select"
                        aria-label={`Resolved status for ${item.userEmail ?? 'this'} feedback`}
                        value={item.resolved ? 'RESOLVED' : 'UNRESOLVED'}
                        disabled={savingId === item.id}
                        onChange={(event) => void changeResolved(item, event.target.value === 'RESOLVED')}
                      >
                        <option value="RESOLVED">Resolved</option>
                        <option value="UNRESOLVED">Not resolved</option>
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <nav className="analytics-pagination" aria-label="Feedback pages">
              <button
                type="button"
                className="button button--secondary"
                disabled={safePage === 0}
                onClick={() => setPage(safePage - 1)}
              >
                Previous
              </button>
              <span>Page {safePage + 1} of {totalPages}</span>
              <button
                type="button"
                className="button button--secondary"
                disabled={safePage >= totalPages - 1}
                onClick={() => setPage(safePage + 1)}
              >
                Next
              </button>
            </nav>
          )}
        </section>
      )}

      {selectedComment && selectedComment.userComments && (
        <Modal
          title="User Feedback"
          description={`${selectedComment.userEmail ?? 'Unknown user'} · ${selectedComment.productName}`}
          onClose={() => setSelectedComment(null)}
        >
          <p>{selectedComment.userComments}</p>
        </Modal>
      )}
    </>
  )
}
