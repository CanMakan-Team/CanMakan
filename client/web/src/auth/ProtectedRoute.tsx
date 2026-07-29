import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { useSession } from './useSession'

export function ProtectedRoute({ requiredRole }: { requiredRole: Role }) {
  const { session } = useSession()
  const location = useLocation()
  const loginPath =
    requiredRole === 'ROLE_SYSTEM_ADMIN'
      ? '/system-admin-login'
      : '/family-login'

  if (!session) {
    return <Navigate to={loginPath} replace state={{ from: location.pathname }} />
  }
  if (!session.roles.includes(requiredRole)) {
    return <Navigate to="/access-denied" replace />
  }

  // This client guard improves navigation only. Spring Security must enforce
  // the same role rules for every production API endpoint.
  return <Outlet />
}
