import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../../shared/api/types'
import { useSession } from './useSession'

/** Protected route
 * This client guard improves navigation only
 * 
 * @author Amelia
 */

export function ProtectedRoute({ requiredRole }: { requiredRole: Role }) {
  const { session } = useSession()
  const location = useLocation()
  const loginPath =
    requiredRole === 'ROLE_SYSTEM_ADMIN'
      ? '/system-admin-login'
      : '/family-login'

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
