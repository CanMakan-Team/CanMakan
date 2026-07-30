import { PrototypeLoginForm } from '../shared/ui/PrototypeLoginForm'

export function FamilyLoginPage() {
  return (
    <main className="login-page login-page--family">
      <div className="login-composition login-composition--family">
        <section className="family-login-introduction" aria-labelledby="family-entry-title">
          <div className="login-brand">
            <span className="brand-mark" aria-hidden="true">
              CM
            </span>
            <strong>CanMakan</strong>
          </div>
          <span className="portal-icon portal-icon--family" aria-hidden="true">
            ♡
          </span>
          <p className="eyebrow">Family Portal</p>
          <h1 id="family-entry-title">Dietary support for your household.</h1>
          <p>
            Manage family profiles, dietary requirements and supplied scan
            assessment history in one private family workspace.
          </p>
        </section>

        <section className="login-card" aria-labelledby="family-login-title">
          <p className="eyebrow">Family Portal</p>
          <h2 id="family-login-title">Family Admin sign in</h2>
          <p>
            Enter the Sprint 1 prototype as the authorised administrator for
            your household.
          </p>
          <PrototypeLoginForm
            portal="FAMILY"
            expectedRole="ROLE_FAMILY_ADMIN"
            destination="/family"
            email="family.admin@demo.canmakan"
            buttonLabel="Enter Family Portal"
            buttonClassName="button--primary"
            fieldId="family-demo-email"
          />
        </section>
      </div>
    </main>
  )
}
