import { CredentialLoginForm } from '../shared/ui/CredentialLoginForm'

/** System admin login — live email/password against POST /api/auth/login. */
export function SystemAdminLoginPage() {
  return (
    <main className="login-page login-page--system">
      <div className="login-composition login-composition--system">
        <section
          className="system-login-introduction"
          aria-labelledby="system-entry-title"
        >
          <div className="login-brand login-brand--system">
            <span className="brand-mark" aria-hidden="true">
              CM
            </span>
            <strong>CanMakan</strong>
          </div>
          <span className="restricted-label">Authorised Staff Only</span>
          <span className="portal-icon portal-icon--system" aria-hidden="true">
            ◫
          </span>
          <p className="eyebrow">System Administration Portal</p>
          <h1 id="system-entry-title">System administrator sign in.</h1>
          <p>
            This portal is limited to authorised CanMakan system administrators.
            Sign in with your administrator account to continue.
          </p>
        </section>

        <section
          className="login-card login-card--system"
          aria-labelledby="system-login-title"
        >
          <p className="eyebrow">System Administration</p>
          <h2 id="system-login-title">System Administrator sign in</h2>
          <p>
            Sign in with an administrator account to review anonymised trends and
            user access controls.
          </p>
          <CredentialLoginForm
            portal="SYSTEM"
            expectedRole="ROLE_SYSTEM_ADMIN"
            destination="/system"
            buttonLabel="Enter System Administration"
            buttonClassName="button--dark"
          />
        </section>
      </div>
    </main>
  )
}
