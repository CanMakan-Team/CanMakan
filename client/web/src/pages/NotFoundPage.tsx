import { Link } from 'react-router-dom'
import { ME_PATH, USER_LOGIN_PATH } from '../app/userPortalPaths'
import { useSession } from '../features/auth/useSession'

export function NotFoundPage() {
  const { session } = useSession()
  const destination = session?.roles.includes('ROLE_SYSTEM_ADMIN')
    ? '/system'
    : session
      ? ME_PATH
      : USER_LOGIN_PATH

  return (
    <main className="centered-page">
      <section className="login-card notice-card">
        <span className="brand-mark" aria-hidden="true">CM</span>
        <p className="eyebrow">Page not found</p>
        <h1>This page is not available.</h1>
        <p>Check the address or return to CanMakan.</p>
        <Link className="button button--primary" to={destination}>
          Return to CanMakan
        </Link>
      </section>
    </main>
  )
}
