import { Navigate } from 'react-router-dom'
import { ME_PATH } from '../../../app/userPortalPaths'

/** `/family` bookmarks land on personal home for every USER, including PRIMARY_ADMIN. */
export function UserLandingPage() {
  return <Navigate to={ME_PATH} replace />
}
