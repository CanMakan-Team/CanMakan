import { Navigate, Outlet } from 'react-router-dom'
import { ME_PATH } from '../../app/userPortalPaths'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'
import { isPrimaryAdminRole } from './lib/familyRoles'
import { useFamilyMe } from './useFamilyMe'

/**
 * Household admin routes require PRIMARY_ADMIN membership. Members and users
 * without a Family Circle are sent to personal home; they do not see admin UI.
 */
export function FamilyMeGate() {
  const { family, loading, error, reload } = useFamilyMe()

  if (loading) {
    return <LoadingState label="Loading your family circle…" />
  }
  if (error) {
    return <ErrorState message={error} onRetry={reload} />
  }
  if (!isPrimaryAdminRole(family?.memberRole)) {
    return <Navigate to={ME_PATH} replace />
  }

  return <Outlet />
}
