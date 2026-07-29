import { useSession } from '../../auth/useSession'

export function FamilyAccountPage() {
  const { session } = useSession()

  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">Supporting prototype infrastructure</p>
          <h1>Account Settings</h1>
          <p>Review the current demo session. Production account changes require backend support.</p>
        </div>
      </header>
      <section className="panel account-panel">
        <span className="avatar avatar--large" aria-hidden="true">
          {session?.displayName.slice(0, 1)}
        </span>
        <dl className="detail-grid">
          <div><dt>Display name</dt><dd>{session?.displayName}</dd></div>
          <div><dt>Role</dt><dd>{session?.roles.join(', ')}</dd></div>
          <div><dt>Portal</dt><dd>{session?.portal}</dd></div>
          <div><dt>Authentication</dt><dd>Prototype Login (mock session)</dd></div>
        </dl>
      </section>
      <div className="notice notice--neutral">
        <strong>Production note</strong>
        <p>
          Passwords, JWT issuance and account recovery are out of scope for this
          frontend prototype.
        </p>
      </div>
    </>
  )
}
