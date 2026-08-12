import { Link } from 'react-router-dom'
import { useSession } from './useSession'

/** Access denied page
 * 
 * @author Amelia
 */

export function AccessDenied() {
  const { session } = useSession()
  const returnPath =
    session?.roles.includes('ROLE_SYSTEM_ADMIN') ? '/system' : '/family'

  return (
    <main className="centered-page">
      <section className="login-card notice-card">
        <span className="brand-mark" aria-hidden="true">
          CM
        </span>
        <p className="eyebrow">Access denied</p>
        <h1>This portal is not available to your role.</h1>
        <p>
          Family and system administration use separate permissions. Sign in
          with an account that is authorised for this portal.
        </p>
        {session ? (
          <Link className="button button--primary" to={returnPath}>
            Return to my portal
          </Link>
        ) : (
          <Link className="button button--primary" to="/family-login">
            Go to Family Login
          </Link>
        )}
      </section>
    </main>
  )
}
