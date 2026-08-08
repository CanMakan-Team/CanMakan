import { useCallback, useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../shared/api/apiErrors'
import type { FamilyMe } from '../../shared/api/types'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'
import { CreateFamilyCirclePage } from './CreateFamilyCirclePage'
import { familyService } from './familyService'

/**
 * Loads GET /families/me before family portal pages.
 * 404 → UC8 create-circle empty state; 200 → child routes.
 * 
 * @author Amelia
 */
export function FamilyMeGate() {
  const [family, setFamily] = useState<FamilyMe | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [needsCreate, setNeedsCreate] = useState(false)

  // UC8 load my family
  // This function is used to load the family information from the server.
  // If the family information is not found, it will redirect to the create family circle page.
  // If the family information is found, it will set the family information to the state.
  const loadMe = useCallback(async () => {
    setLoading(true)
    setError('')
    setNeedsCreate(false)
    try {
      const me = await familyService.getMyFamily()
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
  // This function is used to load the family information from the server.
  // If the family information is not found, it will redirect to the create family circle page.
  // If the family information is found, it will set the family information to the state.
  useEffect(() => {
    const timeoutId = window.setTimeout(() => void loadMe(), 0)
    window.addEventListener('canmakan:family-me-changed', loadMe)
    return () => {
      window.clearTimeout(timeoutId)
      window.removeEventListener('canmakan:family-me-changed', loadMe)
    }
  }, [loadMe])

  if (loading) {
    return <LoadingState label="Loading your family circle…" />
  }
  if (error) {
    return <ErrorState message={error} onRetry={loadMe} />
  }
  if (needsCreate || !family) {
    return <CreateFamilyCirclePage onCreated={() => void loadMe()} />
  }

  return <Outlet context={{ family }} />
}
