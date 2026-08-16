import { useCallback, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { FAMILY_DASHBOARD_PATH, ME_PATH } from '../../../app/userPortalPaths'
import { getErrorMessage } from '../../../shared/api/apiErrors'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { familyApiService } from '../api/familyApiService'
import { isPrimaryAdminRole } from '../lib/familyRoles'

/** Routes PRIMARY_ADMIN to the family dashboard; everyone else to /me. */
export function UserLandingPage() {
  const [destination, setDestination] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  const resolveDestination = useCallback(() => {
    setError('')
    return familyApiService.getMyFamilyOrNull().then(
      (family) =>
        setDestination(
          isPrimaryAdminRole(family?.memberRole)
            ? FAMILY_DASHBOARD_PATH
            : ME_PATH,
        ),
      (caughtError: unknown) => {
        setError(getErrorMessage(caughtError))
      },
    )
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void resolveDestination(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [resolveDestination, attempt])

  if (destination) return <Navigate to={destination} replace />
  if (error) {
    return (
      <ErrorState
        message={error}
        onRetry={() => setAttempt((current) => current + 1)}
      />
    )
  }
  return <LoadingState label="Opening your CanMakan space…" />
}
