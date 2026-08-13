import { useCallback, useEffect, useState } from 'react'
import { authService } from '../../auth/authService'
import { useSession } from '../../auth/useSession'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import type { CurrentUserResponse, FamilyMe } from '../../../shared/api/types'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import {
  familyApiService,
  type FamilyProfileSummary,
} from '../api/familyApiService'

interface AccountDetails {
  account: CurrentUserResponse
  family: FamilyMe
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

/** Server-authoritative identity, account, family and profile details. */
export function FamilyAccountPage() {
  const { session } = useSession()
  const [details, setDetails] = useState<AccountDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [account, family, profiles] = await Promise.all([
        authService.getCurrentUser(),
        familyApiService.getMyFamily(),
        familyApiService.getAccountProfiles(),
      ])
      setDetails({
        account,
        family,
        selfProfile:
          profiles.find((profile) => profile.id === family.selfProfileId) ?? null,
      })
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
          <p>Review the account and family information linked to your sign-in.</p>
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
            <dd>{family.familyName}</dd>
          </div>
          <div>
            <dt>Family role</dt>
            <dd>{readableValue(family.memberRole)}</dd>
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
    </>
  )
}
