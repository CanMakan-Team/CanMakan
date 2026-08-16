import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, Outlet } from 'react-router-dom'
import { FAMILY_CIRCLE_PATH, ME_PATH } from '../../app/userPortalPaths'
import { ApiError, getErrorMessage } from '../../shared/api/apiErrors'
import type { FamilyMe } from '../../shared/api/types'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'
import { familyApiService } from './api/familyApiService'
import { isPrimaryAdminRole } from './lib/familyRoles'

/**
 * Loads GET /families/me only for family-scoped pages.
 * Members are sent to /me. A 404 offers Family Circle options without opening create.
 */
export function FamilyMeGate() {
  const [family, setFamily] = useState<FamilyMe | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [needsCreate, setNeedsCreate] = useState(false)

  const loadMe = useCallback(async () => {
    setLoading(true)
    setError('')
    setNeedsCreate(false)
    try {
      const me = await familyApiService.getMyFamily()
      setFamily(me)
    } catch (caughtError) {
      if (caughtError instanceof ApiError && caughtError.status === 404) {
        setFamily(null)
        setNeedsCreate(true)
      } else {
        setError(getErrorMessage(caughtError))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadMe(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadMe])

  if (loading) {
    return <LoadingState label="Loading your family circle…" />
  }
  if (error) {
    return <ErrorState message={error} onRetry={loadMe} />
  }
  if (needsCreate || !family) {
    return (
      <section className="panel" aria-labelledby="family-required-heading">
        <p className="eyebrow">Optional Family Circle</p>
        <h1 id="family-required-heading">This feature uses a Family Circle</h1>
        <p>
          Your personal account remains available without one. Household
          management is for the family admin after a circle exists.
        </p>
        <div className="page-header__actions">
          <Link className="button button--primary" to={FAMILY_CIRCLE_PATH}>
            Open Family Circle options
          </Link>
          <Link className="button button--secondary" to={ME_PATH}>
            Return to personal home
          </Link>
        </div>
      </section>
    )
  }

  if (!isPrimaryAdminRole(family.memberRole)) {
    return <Navigate to={ME_PATH} replace />
  }

  return <Outlet />
}
