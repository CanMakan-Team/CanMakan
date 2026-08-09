import { useSession } from '../../auth/useSession'

/**
 * Account settings — current live session (X-User-Id until UC19 JWT).
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
            Session identity comes from live login. API calls send X-User-Id until
            JWT replaces it.
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
                : 'Live login (X-User-Id until JWT)'}
            </dd>
          </div>
        </dl>
      </section>
    </>
  )
}
