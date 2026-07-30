import { PrototypeLoginForm } from '../shared/ui/PrototypeLoginForm'

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
          <h1 id="system-entry-title">Restricted administrative access.</h1>
          <p>
            This portal is limited to authorised CanMakan system administrators.
            Access attempts must be protected and audited by backend security.
          </p>
        </section>

        <section
          className="login-card login-card--system"
          aria-labelledby="system-login-title"
        >
          <p className="eyebrow">Restricted Prototype Login</p>
          <h2 id="system-login-title">System Administrator sign in</h2>
          <p>
            Enter the Sprint 1 prototype to review anonymised trends and user
            access controls.
          </p>
          <PrototypeLoginForm
            portal="SYSTEM"
            expectedRole="ROLE_SYSTEM_ADMIN"
            destination="/system"
            email="admin@demo.canmakan"
            buttonLabel="Enter System Administration"
            buttonClassName="button--dark"
            fieldId="system-demo-email"
          />
        </section>
      </div>
    </main>
  )
}
