import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, getErrorMessage } from '../../../shared/api/apiErrors'
import type { FamilyMe } from '../../../shared/api/types'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { familyApiService } from '../api/familyApiService'
import { CreateFamilyCirclePage } from './CreateFamilyCirclePage'

/** Explicit entry point for optional family-circle creation and management. */
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
        onCreated={() => navigate('/family/dashboard', { replace: true })}
      />
    )
  }
  if (!family) return <ErrorState message="Family Circle status is unavailable." onRetry={loadFamily} />

  return (
    <section className="panel" aria-labelledby="family-circle-heading">
      <p className="eyebrow">Family Circle</p>
      <h1 id="family-circle-heading">{family.familyName}</h1>
      <p>
        Your family membership is active. Family role: {family.memberRole.replaceAll('_', ' ')}.
      </p>
      <div className="page-header__actions">
        <Link className="button button--primary" to="/family/dashboard">
          Open family dashboard
        </Link>
        <Link className="button button--secondary" to="/family/personal">
          Return to personal home
        </Link>
      </div>
    </section>
  )
}
