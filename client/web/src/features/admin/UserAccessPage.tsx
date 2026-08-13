import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { adminService } from './adminService'
import type { AdminUser, AdminUserFilters, AdminUserRole } from './models'
import { getErrorMessage } from '../../shared/api/apiErrors'
import { useSession } from '../auth/useSession'
import { Modal } from '../../shared/ui/Modal'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/PageState'
import { StatusBadge } from '../../shared/ui/StatusBadge'

type RoleFilter = 'ALL' | AdminUserRole
type StatusFilter = 'ALL' | 'ACTIVE' | 'SUSPENDED'

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

export function UserAccessPage() {
  const { session } = useSession()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [query, setQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [filters, setFilters] = useState<AdminUserFilters>({})
  const [selected, setSelected] = useState<AdminUser | null>(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setUsers(await adminService.getUsers(filters))
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
    setFilters(toFilters(query, roleFilter, statusFilter))
  }

  const openStatusModal = (user: AdminUser) => {
    setSelected(user)
    setReason('')
    setReasonError('')
    setActionError('')
  }

  const closeStatusModal = useCallback(() => {
    if (busyUserId === null) setSelected(null)
  }, [busyUserId])

  const updateStatus = async (event: FormEvent<HTMLFormElement>) => {
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
    setNotice('')
    setActionError('')
    setReasonError('')
    try {
      const response = await adminService.updateAccountStatus(selected.userId, {
        active: nextActive,
        reason: trimmedReason,
      })
      setNotice(
        response.changed
          ? nextActive
            ? 'Account reactivated successfully.'
            : 'Account suspended successfully.'
          : 'Account status was already up to date. No changes were required.',
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
          <p className="eyebrow">System administrators only</p>
          <h1>User Accounts & Access</h1>
          <p>
            Search existing accounts and manage Active or Suspended status.
            System roles are shown for reference and cannot be changed here.
          </p>
        </div>
      </header>

      <form
        className="filter-bar filter-bar--system"
        aria-label="User account filters"
        onSubmit={applyFilters}
      >
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
        <LoadingState label="Loading user accounts…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : users.length === 0 ? (
        <EmptyState
          title="No accounts match"
          description="Change the email, role or status filters and try again."
        />
      ) : (
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
                {users.map((user) => {
                  const currentAdmin = user.userId === session?.userId
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
                          className="text-button"
                          type="button"
                          disabled={currentAdmin}
                          onClick={() => openStatusModal(user)}
                        >
                          {action}
                        </button>
                        {currentAdmin && <small>Current admin</small>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {selected && (
        <Modal
          title={`${selected.active ? 'Suspend' : 'Reactivate'} account`}
          description={selected.email}
          onClose={closeStatusModal}
        >
          <dl className="detail-grid">
            <div><dt>Role</dt><dd>{selected.role}</dd></div>
            <div>
              <dt>Current status</dt>
              <dd>{selected.active ? 'Active' : 'Suspended'}</dd>
            </div>
          </dl>
          <form className="access-actions" onSubmit={updateStatus}>
            <div className="field-group">
              <label htmlFor="status-reason">Reason</label>
              <textarea
                id="status-reason"
                rows={4}
                value={reason}
                aria-describedby="status-reason-count"
                aria-invalid={reasonError ? 'true' : undefined}
                onChange={(event) => {
                  setReason(event.target.value)
                  setReasonError('')
                }}
              />
              <small id="status-reason-count">{reason.length}/500 characters</small>
            </div>
            {reasonError && (
              <p className="form-message form-message--error" role="alert">
                {reasonError}
              </p>
            )}
            <div className="button-row">
              <button
                className={selected.active ? 'button button--danger' : 'button button--primary'}
                type="submit"
                disabled={busyUserId === selected.userId}
              >
                {busyUserId === selected.userId
                  ? 'Saving…'
                  : selected.active
                    ? 'Suspend account'
                    : 'Reactivate account'}
              </button>
              <button
                className="button button--secondary"
                type="button"
                disabled={busyUserId === selected.userId}
                onClick={closeStatusModal}
              >
                Cancel
              </button>
            </div>
          </form>
        </Modal>
      )}
    </>
  )
}
