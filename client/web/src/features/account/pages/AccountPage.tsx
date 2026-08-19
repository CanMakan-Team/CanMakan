import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { authService } from '../../auth/api/authService'
import { useSession } from '../../auth/useSession'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import type { CurrentUserResponse, FamilyMe } from '../../../shared/api/types'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { Modal } from '../../../shared/ui/Modal'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import {
  FAMILY_CIRCLE_PATH,
  FAMILY_MEMBERS_PATH,
  ME_SETUP_PROFILE_PATH,
} from '../../../app/userPortalPaths'
import {
  familyApiService,
  type FamilyProfileSummary,
} from '../../family/api/familyApiService'

interface AccountDetails {
  account: CurrentUserResponse
  family: FamilyMe | null
  selfProfile: FamilyProfileSummary | null
}

/** Server-authoritative identity, optional family, and SELF-profile details. */
export function AccountPage() {
  const { session, logout } = useSession()
  const [details, setDetails] = useState<AccountDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deletingAccount, setDeletingAccount] = useState(false)
  const [deleteAccountError, setDeleteAccountError] = useState('')
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const deleteTriggerRef = useRef<HTMLButtonElement>(null)

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

  // Kept stable so the dialog does not re-run its focus effect on every
  // keystroke in the confirmation field.
  const closeDeleteDialog = useCallback(() => {
    if (deletingAccount) return
    setDeleteDialogOpen(false)
    setDeleteConfirmation('')
    setDeleteAccountError('')
  }, [deletingAccount])

  const deleteOwnAccount = async () => {
    if (deletingAccount) return
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
  const displayName =
    selfProfile?.profileName || session?.displayName || account.email
  const avatarInitial = (
    selfProfile?.initials ||
    session?.displayName ||
    account.email ||
    'C'
  ).slice(0, 1)
  const isPrimaryAdmin = family?.memberRole === 'PRIMARY_ADMIN'
  // Deleting is only enabled once the caller retypes their own email, so a
  // stray click on the danger button cannot remove the account on its own.
  const deleteConfirmed =
    deleteConfirmation.trim().toLowerCase() === account.email.toLowerCase()
  let familyCircleAction: ReactNode = (
    <Link className="button button--secondary button--small" to={FAMILY_CIRCLE_PATH}>
      Create family circle
    </Link>
  )
  if (family) {
    familyCircleAction = null
    if (isPrimaryAdmin) {
      familyCircleAction = (
        <Link className="button button--secondary button--small" to={FAMILY_MEMBERS_PATH}>
          Manage family
        </Link>
      )
    }
  }

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">User Portal</p>
          <h1>Account Settings</h1>
          <p>Review the account linked to your sign-in.</p>
        </div>
      </header>

      <section className="panel account-panel">
        <div className="account-identity">
          <span className="avatar avatar--large" aria-hidden="true">
            {avatarInitial}
          </span>
          <div className="account-identity__text">
            <h2>{displayName}</h2>
            <p>{account.email}</p>
          </div>
          <StatusBadge status={account.active ? 'ACTIVE' : 'SUSPENDED'} />
        </div>

        <ul className="account-rows">
          <li className="account-row">
            <div>
              <p className="account-row__label">Dietary profile</p>
              <p className="account-row__value">
                {selfProfile?.profileName ?? 'Not available'}
              </p>
              {selfProfile ? null : (
                <p className="account-row__meta">Not set up yet</p>
              )}
            </div>
            <Link className="button button--secondary button--small" to={ME_SETUP_PROFILE_PATH}>
              Edit profile
            </Link>
          </li>
          <li className="account-row">
            <div>
              <p className="account-row__label">Family circle</p>
              <p className="account-row__value">{family?.familyName ?? 'None'}</p>
              {family ? null : (
                <p className="account-row__meta">Not in a family</p>
              )}
            </div>
            {familyCircleAction}
          </li>
        </ul>
      </section>

      <section className="panel account-danger">
        <h2>Danger zone</h2>
        <p>
          Deleting removes the signed-in account ({account.email}). Ensure you have
          removed all family member profiles before deleting your account.
        </p>
        <div>
          <button
            ref={deleteTriggerRef}
            className="button button--danger button--small"
            type="button"
            disabled={deletingAccount}
            onClick={() => {
              setDeleteAccountError('')
              setDeleteConfirmation('')
              setDeleteDialogOpen(true)
            }}
          >
            Delete My Account
          </button>
        </div>
      </section>

      {deleteDialogOpen ? (
        <Modal
          title="Delete your account"
          description="This cannot be undone. You will no longer be able to sign in."
          labelledBy="delete-account-title"
          returnFocusRef={deleteTriggerRef}
          onClose={closeDeleteDialog}
        >
          <div className="field-group">
            <label htmlFor="delete-account-confirmation">
              Type {account.email} to confirm
            </label>
            <input
              id="delete-account-confirmation"
              value={deleteConfirmation}
              autoComplete="off"
              disabled={deletingAccount}
              onChange={(event) => {
                setDeleteConfirmation(event.target.value)
                setDeleteAccountError('')
              }}
            />
          </div>

          {deleteAccountError ? (
            <p className="form-message form-message--error" role="alert">
              {deleteAccountError}
            </p>
          ) : null}

          <div className="modal__actions">
            <button
              className="button button--secondary"
              type="button"
              disabled={deletingAccount}
              onClick={closeDeleteDialog}
            >
              Cancel
            </button>
            <button
              className="button button--danger"
              type="button"
              disabled={!deleteConfirmed || deletingAccount}
              onClick={() => void deleteOwnAccount()}
            >
              {deletingAccount ? 'Deleting…' : 'Delete account permanently'}
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  )
}

/** @deprecated Use AccountPage. Kept for existing test imports during the move. */
export { AccountPage as FamilyAccountPage }
