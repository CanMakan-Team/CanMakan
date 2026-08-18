import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { adminService } from './adminService'
import type { AdminScanFeedbackFilters, AdminScanFeedbackItem, AdminScanFeedbackListResponse } from './models'
import {
  selfProfileApiService,
  type DietaryRestrictionOption,
} from '../family/api/selfProfileApiService'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { useLatestRequest } from '../../shared/lib/useLatestRequest'
import { useResetPage } from '../../shared/lib/useResetPage'
import { HoverTip } from '../../shared/ui/HoverTip'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import { ThumbDownIcon, ThumbUpIcon } from '../../shared/ui/ThumbIcons'
import { formatExactCreatedAt, formatRelativeCreatedAt } from './feedbackTimestamps'
import { SEARCH_DEBOUNCE_MS } from './adminListHelpers'
import { AdminScanFeedbackCommentModal } from './AdminScanFeedbackCommentModal'

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

function filtersEqual(left: AdminScanFeedbackFilters, right: AdminScanFeedbackFilters): boolean {
  return left.keyword === right.keyword
    && left.restrictionCode === right.restrictionCode
    && left.periodDays === right.periodDays
    && left.isPositive === right.isPositive
    && left.resolved === right.resolved
}

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

function parseResolvedFilter(value: string | null): ResolvedFilter {
  if (value === 'RESOLVED' || value === 'UNRESOLVED') return value
  return 'ALL'
}

function previewComment(comment: string): string {
  const trimmed = comment.trim()
  return trimmed.length > COMMENT_PREVIEW_LENGTH
    ? `${trimmed.slice(0, COMMENT_PREVIEW_LENGTH).trimEnd()}…`
    : trimmed
}

export function AdminScanFeedbackPage() {
  const [searchParams] = useSearchParams()
  const initialResolved = parseResolvedFilter(searchParams.get('resolved'))
  const [keyword, setKeyword] = useState('')
  const [restrictionCode, setRestrictionCode] = useState('')
  const [periodDays, setPeriodDays] = useState(DEFAULT_PERIOD_DAYS)
  const [typeFilter, setTypeFilter] = useState<FeedbackTypeFilter>('ALL')
  const [resolvedFilter, setResolvedFilter] = useState<ResolvedFilter>(initialResolved)
  const [filters, setFilters] = useState<AdminScanFeedbackFilters>(() =>
    toFilters('', '', DEFAULT_PERIOD_DAYS, 'ALL', initialResolved),
  )

  const [data, setData] = useState<AdminScanFeedbackListResponse | null>(null)
  const [restrictions, setRestrictions] = useState<DietaryRestrictionOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [selectedComment, setSelectedComment] = useState<AdminScanFeedbackItem | null>(null)
  const [savingId, setSavingId] = useState<number | null>(null)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const paginationResetKey = [
    filters.keyword ?? '',
    filters.restrictionCode ?? '',
    String(filters.periodDays),
    String(filters.isPositive),
    String(filters.resolved),
  ].join('|')
  const [page, setPage] = useResetPage(paginationResetKey)

  const { nextRequestId, isLatestRequest } = useLatestRequest()

  useEffect(() => {
    let active = true
    selfProfileApiService.getCatalog().then(
      (restrictionCatalog) => {
        if (active) setRestrictions(restrictionCatalog)
      },
      () => undefined,
    )
    return () => {
      active = false
    }
  }, [])

  const load = useCallback(async () => {
    const requestId = nextRequestId()
    setLoading(true)
    setError('')
    try {
      const feedbackResponse = await adminService.getScanFeedback({
        ...filters,
        page,
        pageSize: PAGE_SIZE,
      })

      const lastValidPage = Math.max(0, feedbackResponse.pageInfo.totalPages - 1)
      if (page > lastValidPage) {
        if (isLatestRequest(requestId)) setPage(lastValidPage)
        return
      }

      if (!isLatestRequest(requestId)) return
      setData(feedbackResponse)
    } catch (caughtError) {
      if (!isLatestRequest(requestId)) return
      setError(getErrorMessage(caughtError))
    } finally {
      if (isLatestRequest(requestId)) setLoading(false)
    }
  }, [filters, page, isLatestRequest, nextRequestId, setPage])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      const next = toFilters(keyword, restrictionCode, periodDays, typeFilter, resolvedFilter)
      setFilters((current) => (filtersEqual(current, next) ? current : next))
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timeoutId)
  }, [keyword, restrictionCode, periodDays, typeFilter, resolvedFilter])

  const resetFilters = () => {
    setKeyword('')
    setRestrictionCode('')
    setPeriodDays(DEFAULT_PERIOD_DAYS)
    setTypeFilter('ALL')
    setResolvedFilter('ALL')
    setNotice('')
    setActionError('')
    setFilters(toFilters('', '', DEFAULT_PERIOD_DAYS, 'ALL', 'ALL'))
  }

  const hasActiveFilters = Boolean(
    keyword.trim()
      || restrictionCode
      || periodDays !== DEFAULT_PERIOD_DAYS
      || typeFilter !== 'ALL'
      || resolvedFilter !== 'ALL'
      || filters.keyword
      || filters.restrictionCode
      || filters.periodDays !== DEFAULT_PERIOD_DAYS
      || filters.isPositive !== undefined
      || filters.resolved !== undefined,
  )

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
  const pageInfo = data?.pageInfo

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Scan Verdict Feedback</p>
          <h1>Handle User Feedback</h1>
          <p>
            Review thumbs up/down feedback reported against scan verdicts, and
            mark reports as resolved once they have been reviewed.
          </p>
        </div>
      </header>

      <section className="summary-grid summary-grid--feedback" aria-label="Feedback summary">
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

      <section className="filter-bar filter-bar--feedback" aria-label="Scan feedback filters">
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
        <div className="filter-bar__actions">
          <button
            className="button button--secondary"
            type="button"
            disabled={!hasActiveFilters}
            onClick={resetFilters}
          >
            Clear filters
          </button>
        </div>
      </section>

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
          showMascot={false}
        />
      ) : (
        <section className="panel panel--table">
          <div className="responsive-table">
            <table className="data-table feedback-table">
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
                {data.items.map((item) => (
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
                        <HoverTip text={item.userComments.trim()} className="hover-tip--block" interactiveChild>
                          <button
                            className="text-button feedback-comment-preview"
                            type="button"
                            onClick={() => setSelectedComment(item)}
                          >
                            {previewComment(item.userComments)}
                          </button>
                        </HoverTip>
                      ) : (
                        <span className="feedback-comment--empty">No comment</span>
                      )}
                    </td>
                    <td>
                      <HoverTip text={formatExactCreatedAt(item.createdAt)}>
                        <time dateTime={item.createdAt}>
                          {formatRelativeCreatedAt(item.createdAt)}
                        </time>
                      </HoverTip>
                    </td>
                    <td>
                      <button
                        type="button"
                        className={`button button--small ${item.resolved ? 'button--warning' : 'button--success'}`}
                        aria-label={
                          item.resolved
                            ? `Mark ${item.userEmail ?? 'this'} feedback as not resolved`
                            : `Mark ${item.userEmail ?? 'this'} feedback as resolved`
                        }
                        disabled={savingId === item.id}
                        onClick={() => void changeResolved(item, !item.resolved)}
                      >
                        {savingId === item.id
                          ? 'Saving…'
                          : item.resolved
                            ? 'Unresolve'
                            : 'Resolve'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {pageInfo && pageInfo.totalPages > 1 && (
            <nav className="analytics-pagination" aria-label="Feedback pages">
              <button
                type="button"
                className="button button--secondary"
                disabled={pageInfo.page === 0}
                onClick={() => setPage(pageInfo.page - 1)}
              >
                Previous
              </button>
              <span>Page {pageInfo.page + 1} of {pageInfo.totalPages}</span>
              <button
                type="button"
                className="button button--secondary"
                disabled={pageInfo.page >= pageInfo.totalPages - 1}
                onClick={() => setPage(pageInfo.page + 1)}
              >
                Next
              </button>
            </nav>
          )}
        </section>
      )}

      {selectedComment && selectedComment.userComments && (
        <AdminScanFeedbackCommentModal
          item={selectedComment}
          onClose={() => setSelectedComment(null)}
        />
      )}
    </>
  )
}
