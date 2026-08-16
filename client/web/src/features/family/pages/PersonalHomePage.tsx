import { Link, useLocation } from 'react-router-dom'
import { useSession } from '../../auth/useSession'

type PersonalHomeState = {
  profileSetup?: 'created' | 'updated' | 'deferred'
}

/** Family-independent landing surface for an authenticated platform USER. */
export function PersonalHomePage() {
  const { session } = useSession()
  const location = useLocation()
  const navigationState = location.state as PersonalHomeState | null
  const profileMessage =
    navigationState?.profileSetup === 'created'
      ? 'Your personal Dietary Profile was created successfully.'
      : navigationState?.profileSetup === 'updated'
        ? 'Your personal Dietary Profile was updated successfully.'
        : navigationState?.profileSetup === 'deferred'
          ? 'Dietary Profile setup was skipped. No empty profile was created.'
          : 'Your personal Dietary Profile is optional and can be set up independently.'

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Personal CanMakan</p>
          <h1>Your personal dietary space</h1>
          <p>
            Your account and personal Dietary Profile work without a Family Circle.
          </p>
        </div>
      </header>

      <section className="summary-grid" aria-label="Personal account summary">
        <article className="summary-card">
          <div>
            <span>Account</span>
            <strong>{session?.active ? 'Active' : 'Unavailable'}</strong>
            <small>{session?.email}</small>
          </div>
        </article>
        <article className="summary-card">
          <div>
            <span>Dietary Profile</span>
            <strong>Personal and optional</strong>
            <small>{profileMessage}</small>
          </div>
        </article>
        <article className="summary-card">
          <div>
            <span>Family Circle</span>
            <strong>Optional</strong>
            <small>Create or join one only when you choose family features.</small>
          </div>
        </article>
      </section>

      <section className="panel quick-actions" aria-labelledby="personal-actions-title">
        <p className="eyebrow">Personal actions</p>
        <h2 id="personal-actions-title">Continue with CanMakan</h2>
        <Link to="/family/setup-profile">
          <span aria-hidden="true">◇</span>
          <div>
            <strong>Set up personal Dietary Profile</strong>
            <span>Choose your Profile Name and dietary restrictions.</span>
          </div>
        </Link>
        <Link to="/family/circle">
          <span aria-hidden="true">♙</span>
          <div>
            <strong>Family Circle</strong>
            <span>Optionally create, join, or open your household workspace.</span>
          </div>
        </Link>
      </section>
    </>
  )
}
