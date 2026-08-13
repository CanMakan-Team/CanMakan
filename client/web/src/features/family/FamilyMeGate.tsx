import { useCallback, useEffect, useState } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../shared/api/apiErrors'
import type { FamilyMe } from '../../shared/api/types'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'
import { familyApiService } from './api/familyApiService'

/**
 * Loads GET /families/me only for family-scoped pages.
 * A 404 offers Family Circle options without opening the creation form automatically.
 *
 * @author Amelia
 */
export function FamilyMeGate() {
  const [family, setFamily] = useState<FamilyMe | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [needsCreate, setNeedsCreate] = useState(false)

  // load my family
  // This function is used to load the family information from the server.
  // Missing membership leaves personal routes available and requires an explicit family action.
  // If the family information is found, it will set the family information to the state.
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

  // UC8 use effect
  // Deferred so setState is not synchronous in the effect body (eslint react-hooks).
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
          Your personal account and Dietary Profile remain available without one.
          Open Family Circle management only if you want household features.
        </p>
        <div className="page-header__actions">
          <Link className="button button--primary" to="/family/circle">
            Open Family Circle options
          </Link>
          <Link className="button button--secondary" to="/family/personal">
            Return to personal home
          </Link>
        </div>
      </section>
    )
  }

  return <Outlet />
}
