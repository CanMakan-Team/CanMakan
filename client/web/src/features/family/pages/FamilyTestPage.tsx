import { Link } from 'react-router-dom'
import { useSession } from '../../auth/useSession'

export function FamilyTestPage() {
  const { session } = useSession()
  return (
    <main className="family-test-page">
      <header className="test-header">
        <div className="landing-brand">
          <span className="brand-mark" aria-hidden="true">CM</span>
          <strong>CanMakan Family Portal — Test Access</strong>
        </div>
        <Link className="button button--dark" to="/system">Return to System Portal</Link>
      </header>
      <section className="test-content">
        <p className="eyebrow">Read-only prototype preview</p>
        <h1>Family portal test access</h1>
        <p>
          This route lets a System Administrator inspect the family experience
          without becoming or impersonating a Family Admin.
        </p>
        <div className="notice notice--success">
          <strong>Role preserved</strong>
          <p>
            Current session: {session?.displayName} · {session?.roles.join(', ')}
          </p>
        </div>
        <div className="summary-grid">
          <article className="summary-card"><div><span>Preview family members</span><strong>3</strong></div></article>
          <article className="summary-card"><div><span>Preview active profile</span><strong>Alicia</strong></div></article>
          <article className="summary-card"><div><span>Preview scans</span><strong>5</strong></div></article>
        </div>
        <div className="safety-disclaimer">
          <strong>Test boundary</strong>
          <p>
            No family actions are exposed here. Production backend RBAC must
            separately authorise any administrative test capability.
          </p>
        </div>
      </section>
    </main>
  )
}
