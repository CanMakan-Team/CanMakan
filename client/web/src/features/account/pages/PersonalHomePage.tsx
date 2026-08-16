import { Link, useLocation } from 'react-router-dom'
import {
  FAMILY_CIRCLE_PATH,
  FAMILY_DASHBOARD_PATH,
  ME_SETUP_PROFILE_PATH,
} from '../../../app/userPortalPaths'
import { useSession } from '../../auth/useSession'
import { useFamilyMe } from '../../family/useFamilyMe'

type PersonalHomeState = {
  profileSetup?: 'created' | 'deferred'
}

/** Thin USER desk: account, optional profile, mobile for daily use. */
export function PersonalHomePage() {
  const { session } = useSession()
  const { hasFamily, isPrimaryAdmin, loading } = useFamilyMe()
  const location = useLocation()
  const navigationState = location.state as PersonalHomeState | null
  const profileMessage =
    navigationState?.profileSetup === 'created'
      ? 'Your personal Dietary Profile was created successfully.'
      : navigationState?.profileSetup === 'deferred'
        ? 'Dietary Profile setup was skipped. No empty profile was created.'
        : 'Your personal Dietary Profile is optional and can be set up independently.'

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">User Portal</p>
          <h1>Your CanMakan account</h1>
          <p>
            Day-to-day scanning is in the CanMakan mobile app. This site is for
            your account
            {isPrimaryAdmin ? ' and family-circle administration.' : '.'}
          </p>
        </div>
      </header>

      <section className="summary-grid" aria-label="Account summary">
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
            <strong>Optional</strong>
            <small>{profileMessage}</small>
          </div>
        </article>
        <article className="summary-card">
          <div>
            <span>Family Circle</span>
            <strong>
              {loading
                ? 'Checking…'
                : isPrimaryAdmin
                  ? 'Admin'
                  : hasFamily
                    ? 'Member'
                    : 'None'}
            </strong>
            <small>
              {isPrimaryAdmin
                ? 'You can manage household profiles on the web.'
                : hasFamily
                  ? 'Household management is for the family admin. Scan in the app.'
                  : 'Optional. Create one only if you need household tools.'}
            </small>
          </div>
        </article>
      </section>

      <section className="panel quick-actions" aria-labelledby="personal-actions-title">
        <p className="eyebrow">Next steps</p>
        <h2 id="personal-actions-title">Continue with CanMakan</h2>
        <Link to={ME_SETUP_PROFILE_PATH}>
          <span aria-hidden="true">◇</span>
          <div>
            <strong>Set up personal Dietary Profile</strong>
            <span>Choose your Profile Name and dietary restrictions.</span>
          </div>
        </Link>
        {isPrimaryAdmin ? (
          <Link to={FAMILY_DASHBOARD_PATH}>
            <span aria-hidden="true">⌂</span>
            <div>
              <strong>Open family workspace</strong>
              <span>Members, invites, restriction summary, and family history.</span>
            </div>
          </Link>
        ) : null}
        {!hasFamily && !loading ? (
          <Link to={FAMILY_CIRCLE_PATH}>
            <span aria-hidden="true">♙</span>
            <div>
              <strong>Create a Family Circle</strong>
              <span>Optional household workspace. You become the family admin.</span>
            </div>
          </Link>
        ) : null}
      </section>
    </>
  )
}
