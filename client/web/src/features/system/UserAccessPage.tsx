import { useCallback, useEffect, useMemo, useState } from 'react'
import { adminService } from '../../api/adminService'
import { getErrorMessage } from '../../api/apiErrors'
import type {
  AccountStatus,
  AuditEntry,
  Role,
  UserAccessSummary,
} from '../../api/types'
import { useSession } from '../../auth/useSession'
import { Modal } from '../../components/Modal'
import { EmptyState, ErrorState, LoadingState } from '../../components/PageState'
import { StatusBadge } from '../../components/StatusBadge'

const roles: Role[] = ['ROLE_APP_USER', 'ROLE_FAMILY_ADMIN', 'ROLE_SYSTEM_ADMIN']

export function UserAccessPage() {
  const { session } = useSession()
  const [users, setUsers] = useState<UserAccessSummary[]>([])
  const [audit, setAudit] = useState<AuditEntry[]>([])
  const [roleFilter, setRoleFilter] = useState<'ALL' | Role>('ALL')
  const [statusFilter, setStatusFilter] = useState<'ALL' | AccountStatus>('ALL')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<UserAccessSummary | null>(null)
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [notice, setNotice] = useState('')
  const [actionError, setActionError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [loadedUsers, loadedAudit] = await Promise.all([
        adminService.getUsers(),
        adminService.getAuditEntries(),
      ])
      setUsers(loadedUsers)
      setAudit(loadedAudit)
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  const filtered = useMemo(
    () =>
      users.filter((user) => {
        const normalisedQuery = query.trim().toLowerCase()
        return (
          (roleFilter === 'ALL' || user.roles.includes(roleFilter)) &&
          (statusFilter === 'ALL' || user.accountStatus === statusFilter) &&
          (!normalisedQuery ||
            String(user.userId).includes(normalisedQuery) ||
            user.displayName.toLowerCase().includes(normalisedQuery) ||
            user.maskedEmail.toLowerCase().includes(normalisedQuery))
        )
      }),
    [users, roleFilter, statusFilter, query],
  )

  const updateAccess = async (
    user: UserAccessSummary,
    update: { accountStatus?: AccountStatus; roles?: Role[] },
    description: string,
  ) => {
    if (!window.confirm(`${description} for ${user.displayName}? This mock action is audited.`)) {
      setSelected({ ...user })
      return
    }
    setBusyUserId(user.userId)
    setNotice('')
    setActionError('')
    try {
      await adminService.updateUserAccess(
        user.userId,
        update,
        session?.displayName ?? 'System Administrator',
      )
      setNotice(`${description} completed for ${user.displayName}.`)
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
          <p className="eyebrow">Feature 12 · System Admin only</p>
          <h1>User Accounts & Access</h1>
          <p>
            Manage mock access records using safe identifiers. Every change
            requires confirmation and creates a prototype audit entry.
          </p>
        </div>
      </header>

      <section className="filter-bar filter-bar--system" aria-label="User account filters">
        <div className="field-group field-group--search">
          <label htmlFor="user-search">Safe identifier search</label>
          <input
            id="user-search"
            type="search"
            placeholder="User ID, display name or masked email"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <div className="field-group">
          <label htmlFor="role-filter">Role</label>
          <select id="role-filter" value={roleFilter} onChange={(event) => setRoleFilter(event.target.value as 'ALL' | Role)}>
            <option value="ALL">All roles</option>
            {roles.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="status-filter">Account status</label>
          <select id="status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as 'ALL' | AccountStatus)}>
            <option value="ALL">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="SUSPENDED">Suspended</option>
            <option value="PENDING">Pending</option>
            <option value="DISABLED">Disabled</option>
          </select>
        </div>
      </section>

      <div className="sr-live" aria-live="polite">{notice}</div>
      {actionError && <p className="form-message form-message--error" role="alert">{actionError}</p>}

      {loading ? (
        <LoadingState label="Loading account access records…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : filtered.length === 0 ? (
        <EmptyState title="No accounts match" description="Change the search or filters to view other safe account records." />
      ) : (
        <section className="panel panel--table">
          <div className="responsive-table">
            <table className="data-table user-table">
              <caption>User accounts and assigned access rights</caption>
              <thead>
                <tr>
                  <th>User identifier</th>
                  <th>Display name</th>
                  <th>Masked email</th>
                  <th>Assigned roles</th>
                  <th>Account status</th>
                  <th>Family membership</th>
                  <th>Last active</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((user) => (
                  <tr key={user.userId}>
                    <th scope="row">USR-{user.userId}</th>
                    <td>{user.displayName}</td>
                    <td>{user.maskedEmail}</td>
                    <td>
                      <div className="role-list">
                        {user.roles.map((role) => <span key={role}>{role.replace('ROLE_', '')}</span>)}
                      </div>
                    </td>
                    <td><StatusBadge status={user.accountStatus} /></td>
                    <td>{user.familyMembershipStatus ?? 'NONE'}</td>
                    <td>{user.lastActiveAt ? new Date(user.lastActiveAt).toLocaleString('en-SG') : 'Not supplied'}</td>
                    <td>
                      <button className="text-button" type="button" onClick={() => setSelected(user)}>
                        Manage
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section className="panel audit-panel" aria-labelledby="audit-title">
        <div className="panel__header">
          <div><p className="eyebrow">Prototype audit</p><h2 id="audit-title">Recent access changes</h2></div>
        </div>
        {audit.length ? (
          <ul>
            {audit.map((entry) => (
              <li key={entry.auditId}>
                <span>{entry.action} · USR-{entry.targetUserId}</span>
                <small>{entry.actor} · {new Date(entry.createdAt).toLocaleString('en-SG')}</small>
              </li>
            ))}
          </ul>
        ) : (
          <p>No mock access changes have been recorded in this browser.</p>
        )}
      </section>

      {selected && (
        <Modal title={`Manage ${selected.displayName}`} description={`Safe identifier USR-${selected.userId}`} onClose={() => setSelected(null)}>
          <dl className="detail-grid">
            <div><dt>Masked email</dt><dd>{selected.maskedEmail}</dd></div>
            <div><dt>Current status</dt><dd>{selected.accountStatus}</dd></div>
            <div className="detail-grid__wide"><dt>Assigned roles</dt><dd>{selected.roles.join(', ')}</dd></div>
          </dl>
          {selected.userId === session?.userId && (
            <div className="notice notice--warning">
              <strong>Protected current administrator</strong>
              <p>Mock mode prevents removal of this session’s required administrative access.</p>
            </div>
          )}
          <div className="access-actions">
            <div className="field-group">
              <label htmlFor="manage-role">Set primary role</label>
              <select
                id="manage-role"
                value={selected.roles[selected.roles.length - 1]}
                disabled={busyUserId === selected.userId || selected.userId === session?.userId}
                onChange={(event) =>
                  void updateAccess(
                    selected,
                    { roles: [event.target.value as Role] },
                    `Update role to ${event.target.value}`,
                  )
                }
              >
                {roles.map((role) => <option key={role} value={role}>{role}</option>)}
              </select>
            </div>
            <div className="button-row">
              {selected.accountStatus === 'ACTIVE' ? (
                <button
                  className="button button--danger"
                  type="button"
                  disabled={busyUserId === selected.userId || selected.userId === session?.userId}
                  onClick={() => void updateAccess(selected, { accountStatus: 'SUSPENDED' }, 'Suspend account')}
                >
                  Suspend account
                </button>
              ) : (
                <button
                  className="button button--primary"
                  type="button"
                  disabled={busyUserId === selected.userId || selected.userId === session?.userId}
                  onClick={() => void updateAccess(selected, { accountStatus: 'ACTIVE' }, 'Activate account')}
                >
                  Activate account
                </button>
              )}
              {selected.accountStatus === 'PENDING' && (
                <button
                  className="button button--secondary"
                  type="button"
                  disabled={busyUserId === selected.userId}
                  onClick={() => void updateAccess(selected, { accountStatus: 'ACTIVE' }, 'Reset pending access state')}
                >
                  Reset pending state
                </button>
              )}
            </div>
          </div>
        </Modal>
      )}
    </>
  )
}
