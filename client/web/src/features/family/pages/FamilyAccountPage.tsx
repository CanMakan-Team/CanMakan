import { useSession } from '../../auth/useSession'

/**
 * Account settings — current live JWT session.
 *
 * @author Amelia
 */
export function FamilyAccountPage() {
  const { session } = useSession()

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Account</p>
          <h1>Account Settings</h1>
          <p>
            Session identity comes from live JWT login. API calls send
            Authorization Bearer.
          </p>
        </div>
      </header>

      <section className="panel account-panel">
        <span className="avatar avatar--large" aria-hidden="true">
          {session?.displayName.slice(0, 1)}
        </span>
        <dl className="detail-grid">
          <div>
            <dt>Display name</dt>
            <dd>{session?.displayName}</dd>
          </div>
          <div>
            <dt>User id</dt>
            <dd>{session?.userId}</dd>
          </div>
          <div>
            <dt>Role</dt>
            <dd>{session?.roles.join(', ')}</dd>
          </div>
          <div>
            <dt>Portal</dt>
            <dd>{session?.portal}</dd>
          </div>
          <div>
            <dt>Authentication</dt>
            <dd>
              {session?.accessToken
                ? 'Bearer token session'
                : 'Not signed in'}
            </dd>
          </div>
        </dl>
      </section>
    </>
  )
}
