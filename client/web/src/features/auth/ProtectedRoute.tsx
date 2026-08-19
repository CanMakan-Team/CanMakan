import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { USER_LOGIN_PATH } from '../../app/userPortalPaths'
import type { Role } from '../../shared/api/types'
import { ErrorState, LoadingState } from '../../shared/ui/PageState'
import { useSession } from './useSession'

/** Protected route
 * This client guard improves navigation only
 * 
 */

export function ProtectedRoute({ requiredRole }: Readonly<{ requiredRole: Role }>) {
  const {
    session,
    restoring,
    restorationError,
    retryRestoration,
  } = useSession()
  const location = useLocation()
  const loginPath =
    requiredRole === 'ROLE_SYSTEM_ADMIN'
      ? '/system-admin-login'
      : USER_LOGIN_PATH

  if (restoring) {
    return <LoadingState label="Restoring your secure session…" />
  }

  if (restorationError) {
    return (
      <main className="centered-page">
        <ErrorState
          message={restorationError}
          onRetry={retryRestoration}
        />
      </main>
    )
  }

  // if no session, redirect to login path
  if (!session) {
    return <Navigate to={loginPath} replace state={{ from: location.pathname }} />
  }

  // if session does not have required role, redirect to access denied page
  if (!session.roles.includes(requiredRole)) {
    return <Navigate to="/access-denied" replace />
  }
 
  return <Outlet />
}
