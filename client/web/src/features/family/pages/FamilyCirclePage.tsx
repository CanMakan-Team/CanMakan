import { Navigate, useNavigate } from 'react-router-dom'
import { FAMILY_DASHBOARD_PATH, ME_PATH } from '../../../app/userPortalPaths'
import { ErrorState, LoadingState } from '../../../shared/ui/PageState'
import { useFamilyMe } from '../useFamilyMe'
import { CreateFamilyCirclePage } from './CreateFamilyCirclePage'

/** Explicit entry for optional family-circle creation. Existing members are redirected. */
export function FamilyCirclePage() {
  const navigate = useNavigate()
  const { hasFamily, isPrimaryAdmin, loading, error, reload } = useFamilyMe()

  if (loading) return <LoadingState label="Loading Family Circle status…" />
  if (error) return <ErrorState message={error} onRetry={() => void reload()} />
  if (!hasFamily) {
    return (
      <CreateFamilyCirclePage
        onCreated={async () => {
          await reload()
          navigate(FAMILY_DASHBOARD_PATH, { replace: true })
        }}
      />
    )
  }

  return (
    <Navigate to={isPrimaryAdmin ? FAMILY_DASHBOARD_PATH : ME_PATH} replace />
  )
}
