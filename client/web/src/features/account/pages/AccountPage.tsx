import { useCallback, useEffect, useState } from 'react'
import { authService } from '../../auth/authService'
import { useSession } from '../../auth/useSession'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import type { CurrentUserResponse, FamilyMe } from '../../../shared/api/types'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import {
  familyApiService,
  type FamilyProfileSummary,
} from '../../family/api/familyApiService'

interface AccountDetails {
  account: CurrentUserResponse
  family: FamilyMe | null
  selfProfile: FamilyProfileSummary | null
}

function readableValue(value: string | null | undefined) {
  if (!value?.trim()) return 'Not available'
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

/** Server-authoritative identity, optional family, and SELF-profile details. */
export function AccountPage() {
  const { session, logout } = useSession()
  const [details, setDetails] = useState<AccountDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deletingAccount, setDeletingAccount] = useState(false)
  const [deleteAccountError, setDeleteAccountError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const account = await authService.getCurrentUser()
      const family = await familyApiService.getMyFamilyOrNull()
      let selfProfile: FamilyProfileSummary | null = null
      if (family) {
        try {
          const profiles = await familyApiService.getAccountProfiles()
          selfProfile =
            profiles.find((profile) => profile.id === family.selfProfileId) ?? null
        } catch {
          selfProfile = null
        }
      }
      setDetails({ account, family, selfProfile })
    } catch (caughtError) {
      setDetails(null)
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load])

  const deleteOwnAccount = async () => {
    const confirmed = window.confirm(
      'Delete your account? This cannot be undone. You will no longer be able to sign in.',
    )
    if (!confirmed) return
    setDeletingAccount(true)
    setDeleteAccountError('')
    try {
      await authService.deleteOwnAccount()
      await logout()
    } catch (caughtError) {
      setDeleteAccountError(getErrorMessage(caughtError))
      setDeletingAccount(false)
    }
  }

  if (loading) return <LoadingState label="Loading account settings…" />
  if (error) return <ErrorState message={error} onRetry={load} />
  if (!details) {
    return (
      <ErrorState
        message="Your account information is not available."
        onRetry={load}
      />
    )
  }

  const { account, family, selfProfile } = details

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Account</p>
          <h1>Account Settings</h1>
          <p>Review the account linked to your sign-in.</p>
        </div>
      </header>

      <section className="panel account-panel">
        <span className="avatar avatar--large" aria-hidden="true">
          {(selfProfile?.initials || session?.displayName || 'C').slice(0, 1)}
        </span>
        <dl className="detail-grid">
          <div>
            <dt>Email</dt>
            <dd>{account.email}</dd>
          </div>
          <div>
            <dt>Account status</dt>
            <dd>{account.active ? 'Active' : 'Suspended'}</dd>
          </div>
          <div>
            <dt>System role</dt>
            <dd>{account.role === 'ADMIN' ? 'System administrator' : 'User'}</dd>
          </div>
          <div>
            <dt>User ID</dt>
            <dd>{account.userId}</dd>
          </div>
          <div>
            <dt>Family circle</dt>
            <dd>{family?.familyName ?? 'None'}</dd>
          </div>
          <div>
            <dt>Family role</dt>
            <dd>{family ? readableValue(family.memberRole) : 'Not in a family'}</dd>
          </div>
          <div>
            <dt>Profile</dt>
            <dd>{selfProfile?.profileName ?? 'Not available'} · Self</dd>
          </div>
          <div>
            <dt>Profile status</dt>
            <dd>
              {selfProfile ? (selfProfile.active ? 'Active' : 'Inactive') : 'Not available'}
            </dd>
          </div>
        </dl>
      </section>

      <section className="panel account-danger">
        <h2>Delete your account</h2>
        <p>
          This deletes the signed-in account ({account.email}). Ensure you have removed all family members profiles before deleting your account.
        </p>
        <button
          className="button button--danger"
          type="button"
          disabled={deletingAccount}
          onClick={() => void deleteOwnAccount()}
        >
          {deletingAccount ? 'Deleting…' : 'Delete My Account'}
        </button>
        {deleteAccountError ? (
            <p className="form-message form-message--error" role="alert">
              {deleteAccountError}
            </p>
          ) : null}
      </section>
    </>
  )
}

/** @deprecated Use AccountPage. Kept for existing test imports during the move. */
export { AccountPage as FamilyAccountPage }
