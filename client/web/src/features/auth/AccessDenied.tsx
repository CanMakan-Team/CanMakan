import { Link } from 'react-router-dom'
import { FAMILY_ROOT_PATH, USER_LOGIN_PATH } from '../../app/userPortalPaths'
import { useSession } from './useSession'

/** Access denied page
 *
 * @author Amelia
 */

export function AccessDenied() {
  const { session } = useSession()
  const returnPath =
    session?.roles.includes('ROLE_SYSTEM_ADMIN') ? '/system' : FAMILY_ROOT_PATH

  return (
    <main className="centered-page">
      <section className="login-card notice-card">
        <span className="brand-mark" aria-hidden="true">
          CM
        </span>
        <p className="eyebrow">Access denied</p>
        <h1>This portal is not available to your role.</h1>
        <p>
          The User Portal and System Administration use separate permissions. Sign in
          with an account that is authorised for this portal.
        </p>
        {session ? (
          <Link className="button button--primary" to={returnPath}>
            Return to my portal
          </Link>
        ) : (
          <Link className="button button--primary" to={USER_LOGIN_PATH}>
            Go to User Portal login
          </Link>
        )}
      </section>
    </main>
  )
}
