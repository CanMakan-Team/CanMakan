import { useCallback, useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { FAMILY_DASHBOARD_PATH, ME_PATH } from '../../../app/userPortalPaths'
import { ApiError, getErrorMessage } from '../../../shared/api/apiErrors'
import type { FamilyMe } from '../../../shared/api/types'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { familyApiService } from '../api/familyApiService'
import { isPrimaryAdminRole } from '../lib/familyRoles'
import { CreateFamilyCirclePage } from './CreateFamilyCirclePage'

/** Explicit entry for optional family-circle creation. Existing members are redirected. */
export function FamilyCirclePage() {
  const navigate = useNavigate()
  const [family, setFamily] = useState<FamilyMe | null>(null)
  const [loading, setLoading] = useState(true)
  const [missing, setMissing] = useState(false)
  const [error, setError] = useState('')

  const loadFamily = useCallback(async () => {
    setLoading(true)
    setError('')
    setMissing(false)
    try {
      setFamily(await familyApiService.getMyFamily())
    } catch (caughtError) {
      setFamily(null)
      if (caughtError instanceof ApiError && caughtError.status === 404) {
        setMissing(true)
      } else {
        setError(getErrorMessage(caughtError))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadFamily(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadFamily])

  if (loading) return <LoadingState label="Loading Family Circle status…" />
  if (error) return <ErrorState message={error} onRetry={loadFamily} />
  if (missing) {
    return (
      <CreateFamilyCirclePage
        onCreated={() => navigate(FAMILY_DASHBOARD_PATH, { replace: true })}
      />
    )
  }
  if (!family) {
    return (
      <ErrorState message="Family Circle status is unavailable." onRetry={loadFamily} />
    )
  }

  return (
    <Navigate
      to={isPrimaryAdminRole(family.memberRole) ? FAMILY_DASHBOARD_PATH : ME_PATH}
      replace
    />
  )
}
