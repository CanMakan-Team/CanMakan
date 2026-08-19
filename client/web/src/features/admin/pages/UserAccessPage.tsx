import { useCallback, useEffect, useMemo, useState, type SyntheticEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { adminService } from '../api/adminService'
import type { AdminUser, AdminUserFilters, AdminUserRole } from '../api/models'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { useLatestRequest } from '../../../shared/lib/useLatestRequest'
import { useResetPage } from '../../../shared/lib/useResetPage'
import { useSession } from '../../auth/useSession'
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { PortalIcon } from '../../../shared/ui/PortalIcon'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { SEARCH_DEBOUNCE_MS } from '../lib/adminListHelpers'
import { UserAccessStatusModal } from '../components/UserAccessStatusModal'

type RoleFilter = 'ALL' | AdminUserRole
type StatusFilter = 'ALL' | 'ACTIVE' | 'SUSPENDED'

const PAGE_SIZE_OPTIONS = [10, 25, 50] as const
const NOTICE_DISMISS_MS = 5000

type PageSize = (typeof PAGE_SIZE_OPTIONS)[number]

type PageNotice = {
  message: string
  tone: 'success' | 'neutral'
}

function filtersEqual(left: AdminUserFilters, right: AdminUserFilters): boolean {
  return left.query === right.query
    && left.role === right.role
    && left.active === right.active
}

function toFilters(
  query: string,
  role: RoleFilter,
  status: StatusFilter,
): AdminUserFilters {
  const filters: AdminUserFilters = {}
  const trimmedQuery = query.trim()
  if (trimmedQuery) filters.query = trimmedQuery
  if (role !== 'ALL') filters.role = role
  if (status !== 'ALL') filters.active = status === 'ACTIVE'
  return filters
}

function parseStatusFilter(value: string | null): StatusFilter {
  if (value === 'ACTIVE' || value === 'SUSPENDED') return value
  return 'ALL'
}

export function UserAccessPage() {
  const { session } = useSession()
  const [searchParams] = useSearchParams()
  const initialStatus = parseStatusFilter(searchParams.get('status'))
  const [users, setUsers] = useState<AdminUser[]>([])
  const [query, setQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>(initialStatus)
  const [filters, setFilters] = useState<AdminUserFilters>(() =>
    toFilters('', 'ALL', initialStatus),
  )
  const [selected, setSelected] = useState<AdminUser | null>(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [notice, setNotice] = useState<PageNotice | null>(null)
  const [actionError, setActionError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [pageSize, setPageSize] = useState<PageSize>(10)
  const paginationResetKey = `${filters.query ?? ''}|${filters.role ?? ''}|${String(filters.active)}|${pageSize}`
  const [page, setPage] = useResetPage(paginationResetKey)

  const { nextRequestId, isLatestRequest } = useLatestRequest()

  const load = useCallback(async () => {
    const requestId = nextRequestId()
    setLoading(true)
    setError('')
    try {
      const result = await adminService.getUsers(filters)
      if (!isLatestRequest(requestId)) return
      setUsers(result)
    } catch (caughtError) {
      if (!isLatestRequest(requestId)) return
      setError(getErrorMessage(caughtError))
    } finally {
      if (isLatestRequest(requestId)) setLoading(false)
    }
  }, [filters, isLatestRequest, nextRequestId])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      const next = toFilters(query, roleFilter, statusFilter)
      setFilters((current) => (filtersEqual(current, next) ? current : next))
    }, SEARCH_DEBOUNCE_MS)
    return () => window.clearTimeout(timeoutId)
  }, [query, roleFilter, statusFilter])

  useEffect(() => {
    if (!notice) {
      return
    }
    const timeoutId = window.setTimeout(() => setNotice(null), NOTICE_DISMISS_MS)
    return () => window.clearTimeout(timeoutId)
  }, [notice])

  const clearFilters = () => {
    setQuery('')
    setRoleFilter('ALL')
    setStatusFilter('ALL')
    setNotice(null)
    setActionError('')
    setFilters({})
  }

  const manageableUsers = useMemo(
    () => users.filter((user) => user.userId !== session?.userId),
    [users, session?.userId],
  )

  const hasActiveFilters = Boolean(
    query.trim()
      || roleFilter !== 'ALL'
      || statusFilter !== 'ALL'
      || filters.query
      || filters.role
      || filters.active !== undefined,
  )

  const totalPages = Math.max(1, Math.ceil(manageableUsers.length / pageSize))
  const safePage = Math.min(page, totalPages - 1)
  const pageIndexes = useMemo(
    () => Array.from({ length: totalPages }, (_, index) => index),
    [totalPages],
  )
  const visibleUsers = useMemo(() => {
    const start = safePage * pageSize
    return manageableUsers.slice(start, start + pageSize)
  }, [manageableUsers, safePage, pageSize])

  const openStatusModal = (user: AdminUser) => {
    setSelected(user)
    setReason('')
    setReasonError('')
    setActionError('')
  }

  const closeStatusModal = useCallback(() => {
    if (busyUserId === null) {
      setSelected(null)
      setActionError('')
    }
  }, [busyUserId])

  const updateStatus = async (event: SyntheticEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selected) return

    const trimmedReason = reason.trim()
    if (!trimmedReason) {
      setReasonError('Reason is required for an account status change.')
      return
    }
    if (trimmedReason.length > 500) {
      setReasonError('Reason must be 500 characters or fewer.')
      return
    }

    const nextActive = !selected.active
    setBusyUserId(selected.userId)
    setNotice(null)
    setActionError('')
    setReasonError('')
    try {
      const response = await adminService.updateAccountStatus(selected.userId, {
        active: nextActive,
        reason: trimmedReason,
      })
      setNotice(
        response.changed
          ? {
              message: nextActive
                ? 'Account reactivated successfully.'
                : 'Account suspended successfully.',
              tone: 'success',
            }
          : {
              message: 'Account status was already up to date. No changes were required.',
              tone: 'neutral',
            },
      )
      setSelected(null)
      await load()
    } catch (caughtError) {
      setActionError(getErrorMessage(caughtError))
    } finally {
      setBusyUserId(null)
    }
  }

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">System Administration</p>
          <h1>User Accounts & Access</h1>
          <p>
            Search existing accounts and manage Active or Suspended status.
            System roles are shown for reference and cannot be changed here.
          </p>
        </div>
      </header>

      <section className="filter-bar filter-bar--system" aria-label="User account filters">
        <div className="field-group field-group--search">
          <label htmlFor="user-search">Email search</label>
          <input
            id="user-search"
            type="search"
            placeholder="Email contains"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <div className="field-group">
          <label htmlFor="role-filter">Role</label>
          <select
            id="role-filter"
            value={roleFilter}
            onChange={(event) => setRoleFilter(event.target.value as RoleFilter)}
          >
            <option value="ALL">All Roles</option>
            <option value="USER">USER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="status-filter">Status</label>
          <select
            id="status-filter"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
          >
            <option value="ALL">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="SUSPENDED">Suspended</option>
          </select>
        </div>
        <div className="filter-bar__actions">
          <button
            className="button button--secondary"
            type="button"
            disabled={!hasActiveFilters}
            onClick={clearFilters}
          >
            Clear filters
          </button>
        </div>
      </section>

      {notice ? (
        <output className={`notice notice--${notice.tone} user-access-notice`}>
          <span>{notice.message}</span>
          <button
            type="button"
            className="user-access-notice__dismiss"
            onClick={() => setNotice(null)}
          >
            Dismiss
          </button>
        </output>
      ) : null}

      {loading && <LoadingState label="Loading user accounts…" />}
      {!loading && error && <ErrorState message={error} onRetry={load} />}
      {!loading && !error && manageableUsers.length === 0 && (
        <EmptyState
          title="No accounts match"
          description="Change the email, role or status filters and try again."
          showMascot={false}
          icon={
            <span className="page-state__icon" aria-hidden="true">
              <PortalIcon name="person" />
            </span>
          }
          action={
            hasActiveFilters ? (
              <button
                className="button button--secondary"
                type="button"
                onClick={clearFilters}
              >
                Clear filters
              </button>
            ) : null
          }
        />
      )}
      {!loading && !error && manageableUsers.length > 0 && (
        <section className="panel panel--table">
          <div className="responsive-table">
            <table className="data-table user-table">
              <caption>Existing user accounts and current status</caption>
              <thead>
                <tr>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Updated</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {visibleUsers.map((user) => {
                  const action = user.active ? 'Suspend' : 'Reactivate'
                  return (
                    <tr key={user.userId}>
                      <th scope="row">{user.email}</th>
                      <td>{user.role}</td>
                      <td>
                        <StatusBadge
                          status={user.active ? 'ACTIVE' : 'SUSPENDED'}
                          label={user.active ? 'Active' : 'Suspended'}
                        />
                      </td>
                      <td>
                        <time dateTime={user.updatedAt}>
                          {new Date(user.updatedAt).toLocaleString('en-SG')}
                        </time>
                      </td>
                      <td>
                        <button
                          className={`button button--small ${user.active ? 'button--danger' : 'button--success'}`}
                          type="button"
                          onClick={() => openStatusModal(user)}
                        >
                          {action}
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="table-footer table-footer--user-access">
            <div className="table-footer__page-size">
              <label htmlFor="user-page-size">Rows per page</label>
              <select
                id="user-page-size"
                value={pageSize}
                onChange={(event) => setPageSize(Number(event.target.value) as PageSize)}
              >
                {PAGE_SIZE_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>
            <p className="table-footer__summary">
              Page {safePage + 1} of {totalPages}
              {' · '}
              {manageableUsers.length.toLocaleString()} accounts
            </p>
            <nav className="table-footer__pager" aria-label="User account pages">
              <div className="pagination-group">
                <button
                  type="button"
                  className="button button--secondary"
                  disabled={safePage === 0}
                  aria-label="Previous page"
                  onClick={() => setPage(safePage - 1)}
                >
                  ‹
                </button>
                {pageIndexes.map((pageIndex) => (
                  <button
                    key={pageIndex}
                    type="button"
                    className={`button button--secondary${pageIndex === safePage ? ' is-active' : ''}`}
                    aria-label={`Page ${pageIndex + 1}`}
                    aria-current={pageIndex === safePage ? 'page' : undefined}
                    onClick={() => setPage(pageIndex)}
                  >
                    {pageIndex + 1}
                  </button>
                ))}
                <button
                  type="button"
                  className="button button--secondary"
                  disabled={safePage >= totalPages - 1}
                  aria-label="Next page"
                  onClick={() => setPage(safePage + 1)}
                >
                  ›
                </button>
              </div>
            </nav>
          </div>
        </section>
      )}

      {selected && (
        <UserAccessStatusModal
          selected={selected}
          reason={reason}
          reasonError={reasonError}
          actionError={actionError}
          busyUserId={busyUserId}
          onReasonChange={(value) => {
            setReason(value)
            setReasonError('')
          }}
          onClose={closeStatusModal}
          onSubmit={updateStatus}
        />
      )}
    </>
  )
}
