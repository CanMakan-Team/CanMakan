import { CredentialLoginForm } from '../shared/ui/CredentialLoginForm'
import { LoginBrand } from '../shared/ui/CanMakanMascot'

/** System admin login — live email/password against POST /api/auth/login. */
export function SystemAdminLoginPage() {
  return (
    <main className="login-page login-page--system">
      <div className="login-composition login-composition--system">
        <section
          className="system-login-introduction"
          aria-labelledby="system-entry-title"
        >
          <LoginBrand variant="system" />
          <span className="restricted-label">Authorised Staff Only</span>
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
          <h2 id="system-login-title">Sign in</h2>
          <p>Use your administrator account to continue.</p>
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
